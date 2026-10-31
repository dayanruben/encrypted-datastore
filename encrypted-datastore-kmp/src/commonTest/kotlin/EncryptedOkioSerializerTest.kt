package com.dayanruben.datastore.encrypted.kmp

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString.Companion.decodeHex
import okio.Sink
import okio.IOException
import okio.Source
import okio.Timeout
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EncryptedOkioSerializerTest {
    @Test
    fun roundTripsEmptyUtf8AndBinaryRecords() = runTest {
        val serializer = BytesSerializer.encrypted(aesGcmCipher(ByteArray(32)), "settings")
        for (plaintext in listOf(byteArrayOf(), "Olá, 🌍".encodeToByteArray(), ByteArray(256) { it.toByte() })) {
            val output = Buffer()
            serializer.writeTo(plaintext, output)
            val ciphertext = output.snapshot().toByteArray()
            assertContentEquals(byteArrayOf(0x45, 0x44, 0x53, 0x4b, 1, 1), ciphertext.copyOfRange(0, 6))
            assertEquals(plaintext.size + 34, ciphertext.size)
            assertContentEquals(plaintext, serializer.readFrom(output))
        }
    }

    @Test
    fun authenticatesEveryRecordByteBeforeCallingPrefixDelegate() = runTest {
        val cipher = aesGcmCipher(ByteArray(32))
        val output = Buffer()
        BytesSerializer.encrypted(cipher, "settings").writeTo(byteArrayOf(9, 8, 7), output)
        val original = output.readByteArray()
        var reads = 0
        val prefix = object : OkioSerializer<ByteArray> {
            override val defaultValue = byteArrayOf()
            override suspend fun readFrom(source: BufferedSource): ByteArray {
                reads++
                return source.readByteArray(1)
            }
            override suspend fun writeTo(t: ByteArray, sink: BufferedSink) { sink.write(t) }
        }.encrypted(cipher, "settings")
        for (index in original.indices) {
            val tampered = original.copyOf()
            tampered[index] = (tampered[index].toInt() xor 1).toByte()
            assertFailsWith<EncryptionException>("tampering at $index") { prefix.readFrom(Buffer().write(tampered)) }
        }
        assertEquals(0, reads)
        assertContentEquals(byteArrayOf(9), prefix.readFrom(Buffer().write(original)))
    }

    @Test
    fun rejectsEmptyTruncatedUnknownAndPlaintextRecords() = runTest {
        val serializer = BytesSerializer.encrypted(aesGcmCipher(ByteArray(32)), "settings")
        val output = Buffer()
        serializer.writeTo(ByteArray(10), output)
        val record = output.readByteArray()
        for (length in record.indices) {
            assertFailsWith<EncryptionException> { serializer.readFrom(Buffer().write(record, 0, length)) }
        }
        for (index in listOf(4, 5)) {
            val unknown = record.copyOf().also { it[index] = 2 }
            assertFailsWith<UnsupportedFormatException> { serializer.readFrom(Buffer().write(unknown)) }
        }
        assertFailsWith<UnsupportedFormatException> { serializer.readFrom(Buffer().writeUtf8("plaintext")) }
    }

    @Test
    fun bindsHeaderAndUtf8IdentityWithUnambiguousLength() = runTest {
        val real = aesGcmCipher(ByteArray(32))
        val checking = object : DataStoreCipher by real {
            override suspend fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
                assertContentEquals("4544534b01010000000473c3a36f".decodeHex().toByteArray(), associatedData)
                return real.encrypt(plaintext, associatedData)
            }
        }
        val output = Buffer()
        BytesSerializer.encrypted(checking, "são").writeTo(byteArrayOf(1), output)
        assertFailsWith<AuthenticationException> {
            BytesSerializer.encrypted(real, "other").readFrom(output)
        }
    }

    @Test
    fun enforcesExactReadAndWriteLimitsBeforeTouchingOutput() = runTest {
        val cipher = aesGcmCipher(ByteArray(32))
        val serializer = BytesSerializer.encrypted(cipher, "settings", maxPlaintextBytes = 64)
        val output = Buffer()
        serializer.writeTo(ByteArray(64), output)
        assertContentEquals(ByteArray(64), serializer.readFrom(output))
        assertFailsWith<PayloadTooLargeException> { serializer.writeTo(ByteArray(65), output) }
        assertEquals(0L, output.size)
        BytesSerializer.encrypted(cipher, "settings", maxPlaintextBytes = 65).writeTo(ByteArray(65), output)
        assertFailsWith<PayloadTooLargeException> { serializer.readFrom(output) }
    }

    @Test
    fun defaultLimitAllowsOneMiBAndRejectsOneByteMore() = runTest {
        val cipher = aesGcmCipher(ByteArray(32))
        val serializer = BytesSerializer.encrypted(cipher, "settings")
        val payload = ByteArray(1_048_576) { it.toByte() }
        val output = Buffer()
        serializer.writeTo(payload, output)
        assertEquals(1_048_610L, output.size)
        assertContentEquals(payload, serializer.readFrom(output))
        assertFailsWith<PayloadTooLargeException> { serializer.writeTo(ByteArray(1_048_577), output) }
        assertEquals(0L, output.size)
        BytesSerializer.encrypted(cipher, "settings", 1_048_577).writeTo(ByteArray(1_048_577), output)
        assertFailsWith<PayloadTooLargeException> { serializer.readFrom(output) }
    }

    @Test
    fun neverEndingSourceStopsAtTheBound() = runTest {
        var upstreamBytes = 0L
        val endless = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                sink.write(ByteArray(byteCount.toInt()))
                upstreamBytes += byteCount
                return byteCount
            }
            override fun close() = Unit
            override fun timeout() = Timeout.NONE
        }.buffer()
        val serializer = BytesSerializer.encrypted(aesGcmCipher(ByteArray(32)), "settings", 64)
        assertFailsWith<PayloadTooLargeException> { serializer.readFrom(endless) }
        assertTrue(upstreamBytes - endless.buffer.size <= 99) // 64 + 34 + one sentinel, consumed
        assertTrue(upstreamBytes <= 99 + 8192) // Okio may prefetch one segment, never the full source
    }

    @Test
    fun oversizedSerializerCannotGrowItsBufferWithoutBound() = runTest {
        var chunks = 0
        val unbounded = object : OkioSerializer<Unit> {
            override val defaultValue = Unit
            override suspend fun readFrom(source: BufferedSource) = Unit
            override suspend fun writeTo(t: Unit, sink: BufferedSink) {
                while (true) {
                    sink.write(ByteArray(1024))
                    chunks++
                }
            }
        }.encrypted(aesGcmCipher(ByteArray(32)), "settings", 64)
        val destination = Buffer()
        assertFailsWith<PayloadTooLargeException> { unbounded.writeTo(Unit, destination) }
        assertTrue(chunks <= 8)
        assertEquals(0L, destination.size)
    }

    @Test
    fun preservesIoAndCancellationExceptions() = runTest {
        val cipher = aesGcmCipher(ByteArray(32))
        val serializer = BytesSerializer.encrypted(cipher, "settings")
        val io = IOException("test IO failure")
        val source = object : Source by Buffer() {
            override fun read(sink: Buffer, byteCount: Long): Long = throw io
        }.buffer()
        assertSame(io, assertFailsWith<IOException> { serializer.readFrom(source) })
        val sink = object : Sink by Buffer() {
            override fun write(source: Buffer, byteCount: Long) { throw io }
        }.buffer()
        assertSame(io, assertFailsWith<IOException> { serializer.writeTo(ByteArray(8192), sink) })
        val cancelled = CancellationException("test cancellation")
        val cancelling = object : DataStoreCipher by cipher {
            override suspend fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = throw cancelled
            override suspend fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = throw cancelled
        }
        val cancelledSerializer = BytesSerializer.encrypted(cancelling, "settings")
        assertSame(cancelled, assertFailsWith<CancellationException> {
            cancelledSerializer.writeTo(byteArrayOf(1), Buffer())
        })
        val record = Buffer().also { serializer.writeTo(byteArrayOf(1), it) }
        assertSame(cancelled, assertFailsWith<CancellationException> { cancelledSerializer.readFrom(record) })
    }

    @Test
    fun doesNotCloseCallerStreams() = runTest {
        var sinkClosed = false
        var sourceClosed = false
        val buffer = Buffer()
        val sink = object : Sink by buffer {
            override fun close() { sinkClosed = true }
        }.buffer()
        val serializer = BytesSerializer.encrypted(aesGcmCipher(ByteArray(32)), "settings")
        serializer.writeTo(byteArrayOf(3), sink)
        sink.flush()
        val source = object : Source by buffer {
            override fun close() { sourceClosed = true }
        }.buffer()
        assertContentEquals(byteArrayOf(3), serializer.readFrom(source))
        assertFalse(sinkClosed)
        assertFalse(sourceClosed)
    }

    @Test
    fun rejectsInvalidIdentityAndLimits() = runTest {
        val cipher = aesGcmCipher(ByteArray(32))
        for (identity in listOf("", " \n", "a".repeat(1025), "🌍".repeat(257))) {
            assertFailsWith<IllegalArgumentException> { BytesSerializer.encrypted(cipher, identity) }
        }
        for (limit in listOf(-1, 0, 16_777_217, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { BytesSerializer.encrypted(cipher, "settings", limit) }
        }
        BytesSerializer.encrypted(cipher, "🌍".repeat(256), 16_777_216)
    }
}

package com.dayanruben.datastore.encrypted.kmp

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecordSerializerTest {
    @Test
    fun rejectsOneLargeByteArrayBeforeBufferingIt() = runTest {
        var remainingAfterRejection = -1
        val serializer = object : RecordSerializer<Unit> {
            override val defaultValue = Unit
            override suspend fun readFrom(source: BufferedSource) = Unit
            override suspend fun writeTo(t: Unit, writer: RecordWriter) {
                try { writer.write(ByteArray(1_048_576)) } catch (e: PayloadTooLargeException) {
                    remainingAfterRejection = writer.remainingBytes
                    throw e
                }
            }
        }.encrypted(aesGcmCipher(ByteArray(32)), "settings", 64)
        val output = Buffer()
        assertFailsWith<PayloadTooLargeException> { serializer.writeTo(Unit, output) }
        assertEquals(64, remainingAfterRejection)
        assertEquals(0L, output.size)
    }

    @Test
    fun rejectsOneLargeUtf8WriteBeforeBufferingIt() = runTest {
        var remainingAfterRejection = -1
        val serializer = object : RecordSerializer<Unit> {
            override val defaultValue = Unit
            override suspend fun readFrom(source: BufferedSource) = Unit
            override suspend fun writeTo(t: Unit, writer: RecordWriter) {
                try { writer.writeUtf8("🌍".repeat(8192)) } catch (e: PayloadTooLargeException) {
                    remainingAfterRejection = writer.remainingBytes
                    throw e
                }
            }
        }.encrypted(aesGcmCipher(ByteArray(32)), "settings", 64)
        assertFailsWith<PayloadTooLargeException> { serializer.writeTo(Unit, Buffer()) }
        assertEquals(64, remainingAfterRejection)
    }

    @Test
    fun supportsSlicesAndExactUtf8Budgets() = runTest {
        val serializer = object : RecordSerializer<String> {
            override val defaultValue = ""
            override suspend fun readFrom(source: BufferedSource) = source.readUtf8()
            override suspend fun writeTo(t: String, writer: RecordWriter) {
                writer.write(byteArrayOf(0, 65, 0), offset = 1, byteCount = 1)
                writer.writeUtf8(t)
            }
        }.encrypted(aesGcmCipher(ByteArray(32)), "settings", 5)
        val output = Buffer()
        serializer.writeTo("🌍", output)
        assertEquals("A🌍", serializer.readFrom(output))
        assertFailsWith<PayloadTooLargeException> { serializer.writeTo("🌍x", Buffer()) }
    }

    @Test
    fun swallowedLimitFailureCannotPublishASuffix() = runTest {
        val serializer = object : OkioSerializer<String> {
            override val defaultValue = ""
            override suspend fun readFrom(source: BufferedSource) = source.readUtf8()
            override suspend fun writeTo(t: String, sink: BufferedSink) {
                try { sink.write(ByteArray(1_048_576)) } catch (_: PayloadTooLargeException) { sink.buffer.clear() }
                sink.writeUtf8("suffix")
            }
        }.encrypted(aesGcmCipher(ByteArray(32)), "settings", 64)
        val output = Buffer()
        assertFailsWith<PayloadTooLargeException> { serializer.writeTo("unused", output) }
        assertEquals(0L, output.size)
    }

    @Test
    fun okioBridgeSupportsDirectBufferWritesWithARecordLimit() = runTest {
        val serializer = object : OkioSerializer<String> {
            override val defaultValue = ""
            override suspend fun readFrom(source: BufferedSource) = source.readUtf8()
            override suspend fun writeTo(t: String, sink: BufferedSink) { sink.buffer.writeUtf8(t) }
        }.encrypted(aesGcmCipher(ByteArray(32)), "settings", 4)
        val output = Buffer()
        serializer.writeTo("safe", output)
        assertEquals("safe", serializer.readFrom(output))
        assertFailsWith<PayloadTooLargeException> { serializer.writeTo("large", output) }
        assertEquals(0L, output.size)
    }
}

package com.dayanruben.datastore.encrypted.kmp

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.IOException
import okio.Sink
import okio.Timeout
import okio.buffer

/**
 * Adds a versioned, authenticated AES-256-GCM record around this serializer.
 *
 * [storeId] must be a stable, nonblank identity of at most 1024 UTF-8 bytes. Changing it makes
 * previous records unreadable. It binds ciphertext to its logical store, independently of its path.
 * [maxPlaintextBytes] bounds serialized settings, defaults to 1 MiB, and must be in 1..16 MiB.
 * Existing empty files and unsupported/unauthenticated records fail without returning defaults.
 *
 * This format is distinct from the legacy Tink formats. No implicit migration or plaintext
 * fallback occurs. One complete bounded record is authenticated before any bytes reach the delegate.
 * The source/sink supplied by DataStore remains caller-owned and is never closed by this adapter.
 */
public fun <T> OkioSerializer<T>.encrypted(
    cipher: DataStoreCipher,
    storeId: String,
    maxPlaintextBytes: Int = DEFAULT_MAX_PLAINTEXT_BYTES,
): OkioSerializer<T> {
    require(storeId.isNotBlank()) { "storeId must not be blank" }
    val identity = storeId.encodeToByteArray(throwOnInvalidSequence = true)
    require(identity.size <= 1024) { "storeId must be at most 1024 UTF-8 bytes" }
    require(maxPlaintextBytes in 1..16_777_216) { "maxPlaintextBytes must be in 1..16777216" }
    val aad = Buffer().write(RECORD_HEADER).writeInt(identity.size).write(identity).readByteArray()
    return EncryptedOkioSerializer(this, cipher, aad, maxPlaintextBytes)
}

internal const val DEFAULT_MAX_PLAINTEXT_BYTES = 1_048_576

private val RECORD_HEADER = byteArrayOf(0x45, 0x44, 0x53, 0x4b, 1, 1)
private const val RECORD_OVERHEAD = 34
private const val CIPHER_OVERHEAD = 28

private class EncryptedOkioSerializer<T>(
    private val delegate: OkioSerializer<T>,
    private val cipher: DataStoreCipher,
    private val associatedData: ByteArray,
    private val maxPlaintextBytes: Int,
) : OkioSerializer<T> {
    override val defaultValue: T get() = delegate.defaultValue

    override suspend fun readFrom(source: BufferedSource): T {
        currentCoroutineContext().ensureActive()
        val record = readBounded(source)
        if (record.size < RECORD_OVERHEAD || !record.copyOfRange(0, 6).contentEquals(RECORD_HEADER)) {
            throw UnsupportedFormatException()
        }
        val plaintext = cipher.decrypt(record.copyOfRange(6, record.size), associatedData)
        val decoded = Buffer()
        try {
            if (plaintext.size > maxPlaintextBytes) throw PayloadTooLargeException(maxPlaintextBytes)
            currentCoroutineContext().ensureActive()
            decoded.write(plaintext)
            return delegate.readFrom(decoded)
        } finally {
            plaintext.fill(0)
            decoded.clear()
        }
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        currentCoroutineContext().ensureActive()
        val serialized = Buffer()
        val bounded = LimitedSink(serialized, maxPlaintextBytes).buffer()
        var plaintext: ByteArray? = null
        try {
            delegate.writeTo(t, bounded)
            bounded.flush()
            plaintext = serialized.readByteArray()
            val encrypted = cipher.encrypt(plaintext, associatedData)
            if (encrypted.size != plaintext.size + CIPHER_OVERHEAD) {
                throw EncryptionException("DataStore cipher returned an invalid record length")
            }
            currentCoroutineContext().ensureActive()
            sink.write(RECORD_HEADER)
            sink.write(encrypted)
        } finally {
            plaintext?.fill(0)
            bounded.buffer.clear()
            serialized.clear()
        }
    }

    private fun readBounded(source: BufferedSource): ByteArray {
        val buffer = Buffer()
        val maxCiphertextBytes = maxPlaintextBytes.toLong() + RECORD_OVERHEAD
        try {
            while (true) {
                // One sentinel byte distinguishes an exact-limit record from an oversized record.
                val remaining = maxCiphertextBytes + 1 - buffer.size
                val count = source.read(buffer, minOf(8192L, remaining))
                if (count == -1L) return buffer.readByteArray()
                if (count == 0L) throw IOException("Encrypted DataStore source made no progress")
                if (buffer.size > maxCiphertextBytes) throw PayloadTooLargeException(maxPlaintextBytes)
            }
        } finally {
            buffer.clear()
        }
    }
}

private class LimitedSink(private val destination: Buffer, private val maxBytes: Int) : Sink {
    override fun write(source: Buffer, byteCount: Long) {
        if (byteCount > maxBytes.toLong() - destination.size) throw PayloadTooLargeException(maxBytes)
        destination.write(source, byteCount)
    }
    override fun flush(): Unit = Unit
    override fun close(): Unit = Unit
    override fun timeout(): Timeout = Timeout.NONE
}

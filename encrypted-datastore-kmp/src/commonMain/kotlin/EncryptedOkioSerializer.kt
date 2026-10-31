package com.dayanruben.datastore.encrypted.kmp

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.IOException
import okio.buffer

/**
 * Adds a versioned, authenticated AES-256-GCM record around an existing Okio serializer.
 *
 * [storeId] must be a stable, nonblank identity of at most 1024 UTF-8 bytes. Changing it makes
 * previous records unreadable. It binds ciphertext to its logical store, independently of its path.
 * [maxPlaintextBytes] bounds accepted serialized records, defaults to 1 MiB, and must be in 1..16 MiB.
 * Existing empty files and unsupported/unauthenticated records fail without returning defaults.
 *
 * This format is distinct from the legacy Tink formats. No implicit migration or plaintext
 * fallback occurs. One complete bounded record is authenticated before any bytes reach the delegate.
 * The source/sink supplied by DataStore remains caller-owned and is never closed by this adapter.
 *
 * This compatibility bridge must supply Okio's sealed BufferedSink implementation. Its bulk writes
 * and direct buffer access may allocate beyond the record limit before rejection. The delegate's
 * own allocations are also outside the limit. Use [RecordSerializer] for bounded adapter writes.
 */
public fun <T> OkioSerializer<T>.encrypted(
    cipher: DataStoreCipher,
    storeId: String,
    maxPlaintextBytes: Int = DEFAULT_MAX_PLAINTEXT_BYTES,
): OkioSerializer<T> {
    val delegate = this
    return encryptedSerializer(
        default = { delegate.defaultValue },
        read = { delegate.readFrom(it) },
        write = { value, writer ->
            val buffered = writer.buffer()
            try { delegate.writeTo(value, buffered); buffered.flush() } finally { buffered.buffer.clear() }
        },
        cipher, storeId, maxPlaintextBytes,
    )
}

/**
 * Encrypts immutable settings with the same format as the Okio bridge, checking each plaintext write
 * before buffering it. [RecordWriter] exposes no backing buffer. Serializer-private allocations are
 * outside this adapter's budget. The source/sink lifecycle and authentication contract match [encrypted].
 */
public fun <T> RecordSerializer<T>.encrypted(
    cipher: DataStoreCipher,
    storeId: String,
    maxPlaintextBytes: Int = DEFAULT_MAX_PLAINTEXT_BYTES,
): OkioSerializer<T> {
    val delegate = this
    return encryptedSerializer(
        default = { delegate.defaultValue },
        read = { delegate.readFrom(it) },
        write = { value, writer -> delegate.writeTo(value, writer) },
        cipher, storeId, maxPlaintextBytes,
    )
}

private fun <T> encryptedSerializer(
    default: () -> T,
    read: suspend (BufferedSource) -> T,
    write: suspend (T, BoundedRecordWriter) -> Unit,
    cipher: DataStoreCipher,
    storeId: String,
    maxPlaintextBytes: Int,
): OkioSerializer<T> {
    require(storeId.length <= 1024) { "storeId must be at most 1024 UTF-8 bytes" }
    require(storeId.isNotBlank()) { "storeId must not be blank" }
    val identity = storeId.encodeToByteArray(throwOnInvalidSequence = true)
    require(identity.size <= 1024) { "storeId must be at most 1024 UTF-8 bytes" }
    require(maxPlaintextBytes in 1..16_777_216) { "maxPlaintextBytes must be in 1..16777216" }
    val aad = Buffer().write(RECORD_HEADER).writeInt(identity.size).write(identity).readByteArray()
    return EncryptedOkioSerializer(default, read, write, cipher, aad, maxPlaintextBytes)
}

internal const val DEFAULT_MAX_PLAINTEXT_BYTES = 1_048_576

private val RECORD_HEADER = byteArrayOf(0x45, 0x44, 0x53, 0x4b, 1, 1)
private const val RECORD_OVERHEAD = 34
private const val CIPHER_OVERHEAD = 28

private class EncryptedOkioSerializer<T>(
    private val default: () -> T,
    private val read: suspend (BufferedSource) -> T,
    private val write: suspend (T, BoundedRecordWriter) -> Unit,
    private val cipher: DataStoreCipher,
    private val associatedData: ByteArray,
    private val maxPlaintextBytes: Int,
) : OkioSerializer<T> {
    override val defaultValue: T get() = default()

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
            return read(decoded)
        } finally {
            plaintext.fill(0)
            decoded.clear()
        }
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        currentCoroutineContext().ensureActive()
        val bounded = BoundedRecordWriter(maxPlaintextBytes)
        var plaintext: ByteArray? = null
        try {
            write(t, bounded)
            plaintext = bounded.readBytes()
            val encrypted = cipher.encrypt(plaintext, associatedData)
            if (encrypted.size != plaintext.size + CIPHER_OVERHEAD) {
                throw EncryptionException("DataStore cipher returned an invalid record length")
            }
            currentCoroutineContext().ensureActive()
            sink.write(RECORD_HEADER)
            sink.write(encrypted)
        } finally {
            plaintext?.fill(0)
            bounded.clear()
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

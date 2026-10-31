package com.dayanruben.datastore.encrypted.kmp

import okio.Buffer
import okio.Sink
import okio.Timeout
import okio.utf8Size

/**
 * Sequential plaintext output valid only during [RecordSerializer.writeTo].
 *
 * A write is checked before copying or UTF-8 encoding into the adapter's buffer. Rejected writes do
 * not consume [remainingBytes], and the record stays failed even if a serializer catches the error.
 * Inputs remain caller-owned. No mutable backing buffer, close, or platform stream is exposed.
 */
public interface RecordWriter {
    public val remainingBytes: Int
    public fun write(source: ByteArray, offset: Int = 0, byteCount: Int = source.size - offset)
    public fun writeUtf8(value: String)
}

internal class BoundedRecordWriter(private val maxBytes: Int) : RecordWriter, Sink {
    private val data = Buffer()
    private var failure: PayloadTooLargeException? = null

    override val remainingBytes: Int get() = maxBytes - data.size.toInt()

    override fun write(source: ByteArray, offset: Int, byteCount: Int) {
        require(offset >= 0 && byteCount >= 0 && offset <= source.size - byteCount) { "Invalid byte range" }
        checkCapacity(byteCount.toLong())
        data.write(source, offset, byteCount)
    }

    override fun writeUtf8(value: String) {
        // UTF-8 has at least one byte per UTF-16 code unit, including replacement of lone surrogates.
        // This bounds the sizing scan too, without allocating an encoded copy.
        checkCapacity(value.length.toLong())
        checkCapacity(value.utf8Size())
        data.writeUtf8(value)
    }

    override fun write(source: Buffer, byteCount: Long) {
        require(byteCount >= 0 && byteCount <= source.size) { "Invalid byte count" }
        checkCapacity(byteCount)
        data.write(source, byteCount)
    }

    private fun checkCapacity(byteCount: Long) {
        throwIfFailed()
        if (byteCount > remainingBytes) {
            val error = PayloadTooLargeException(maxBytes)
            failure = error
            throw error
        }
    }

    fun throwIfFailed() { failure?.let { throw it } }
    fun readBytes(): ByteArray { throwIfFailed(); return data.readByteArray() }
    fun clear() { data.clear() }
    override fun flush() { throwIfFailed() }
    override fun close() = Unit
    override fun timeout(): Timeout = Timeout.NONE
}

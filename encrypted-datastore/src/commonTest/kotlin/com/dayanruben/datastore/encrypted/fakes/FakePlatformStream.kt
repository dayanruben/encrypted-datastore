package com.dayanruben.datastore.encrypted.fakes

import com.dayanruben.datastore.encrypted.ByteArrayPlatformOutputStream
import com.dayanruben.datastore.encrypted.PlatformInputStream
import com.dayanruben.datastore.encrypted.PlatformOutputStream

// --- FakePlatformInputStream ---
class FakePlatformInputStream(private val data: ByteArray) : PlatformInputStream() {
    private var position = 0
    private var closed = false

    fun markSupported(): Boolean = true // Simple fake support
    private var markPosition = 0
    fun mark(readlimit: Int) {
        markPosition = position
    }
    fun reset() {
        position = markPosition
    }


    fun read(): Int {
        if (closed) throw Exception("Stream closed")
        return if (position < data.size) data[position++].toInt() and 0xFF else -1
    }

    fun read(b: ByteArray, off: Int, len: Int): Int {
        if (closed) throw Exception("Stream closed")
        if (position >= data.size) return -1
        val bytesToRead = minOf(len, data.size - position)
        data.copyInto(b, off, position, position + bytesToRead)
        position += bytesToRead
        return bytesToRead
    }

    fun available(): Int {
        if (closed) return 0
        return data.size - position
    }

    override fun close() {
        closed = true
    }

    fun isClosed() = closed
}

// --- FakePlatformOutputStream ---
// A simple fake that writes to a ByteArray. Note that ByteArrayPlatformOutputStream is also an expect class.
// This FakePlatformOutputStream is more for general testing if needed.
open class FakePlatformOutputStream : PlatformOutputStream() {
    val output = mutableListOf<Byte>()
    private var closed = false

    fun write(b: Int) {
        if (closed) throw Exception("Stream closed")
        output.add(b.toByte())
    }

    fun write(b: ByteArray, off: Int, len: Int) {
        if (closed) throw Exception("Stream closed")
        for (i in off until off + len) {
            output.add(b[i])
        }
    }

    fun toByteArray(): ByteArray = output.toByteArray()

    override fun close() {
        closed = true
    }

    fun isClosed() = closed

    fun flush() { /* No-op for this fake */ }
}

// --- FakeByteArrayPlatformOutputStream ---
// This is the actual for the expect class ByteArrayPlatformOutputStream
// For commonTest, it needs to be a concrete class.
actual class CommonTestByteArrayPlatformOutputStream : ByteArrayPlatformOutputStream() {
    private val buffer = mutableListOf<Byte>()
    private var closed = false

    actual fun toByteArray(): ByteArray {
        return buffer.toByteArray()
    }

    // Implement PlatformOutputStream methods
    override fun write(b: Int) {
        if (closed) throw Exception("Stream closed")
        buffer.add(b.toByte())
    }

    // Add other overloads of write if your common code uses them, e.g., write(byteArray: ByteArray)
    fun write(byteArray: ByteArray) { // Convenience for testing
        if (closed) throw Exception("Stream closed")
        buffer.addAll(byteArray.toList())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (closed) throw Exception("Stream closed")
        for (i in off until off + len) {
            buffer.add(b[i])
        }
    }

    override fun flush() {
        // No-op for byte array stream
    }

    override fun close() {
        closed = false // Typically doesn't prevent toByteArray(), but good practice
    }

    fun isClosed(): Boolean = closed
}

// Actual for platformWriteBytes (extension on PlatformOutputStream)
actual fun PlatformOutputStream.platformWriteBytes(bytes: ByteArray) {
    if (this is CommonTestByteArrayPlatformOutputStream) { // Or any other FakePlatformOutputStream
        this.write(bytes) // Use the convenience method if available
    } else if (this is FakePlatformOutputStream) {
        this.write(bytes, 0, bytes.size)
    }
    else {
        // Fallback for generic PlatformOutputStream, write byte by byte or chunked
        // This would depend on the methods available on the expect PlatformOutputStream
        // For testing, ensure the fake used implements a way to accept byte arrays.
        // If PlatformOutputStream only has write(Int), then:
        // bytes.forEach { this.write(it.toInt()) }
        // Or if it has write(ByteArray, Int, Int)
        this.write(bytes, 0, bytes.size)
    }
}


// Actuals for expect fun PlatformInputStream.platformReadBytes() and ByteArray.toPlatformInputStream()
actual fun PlatformInputStream.platformReadBytes(): ByteArray {
    if (this is FakePlatformInputStream) {
        // Efficiently read if it's our fake
        val result = mutableListOf<Byte>()
        var byte = this.read()
        while (byte != -1) {
            result.add(byte.toByte())
            byte = this.read()
        }
        return result.toByteArray()
    }
    // Generic implementation for any PlatformInputStream
    val buffer = mutableListOf<Byte>()
    val chunk = ByteArray(1024)
    var bytesRead: Int
    while (true) {
        bytesRead = this.read(chunk, 0, chunk.size)
        if (bytesRead == -1) break
        buffer.addAll(chunk.take(bytesRead))
    }
    return buffer.toByteArray()
}

actual fun ByteArray.toPlatformInputStream(): PlatformInputStream {
    return FakePlatformInputStream(this)
}

// Actuals for close methods on PlatformInputStream and PlatformOutputStream
// These are now part of the expect class definitions, so the `actual class`
// (or typealias to a class that has close()) must implement/provide it.
// FakePlatformInputStream and FakePlatformOutputStream above have close().
// CommonTestByteArrayPlatformOutputStream also has close().
// So, no separate `actual fun PlatformInputStream.close()` needed here.

// For `expect class PlatformInputStream { fun close() }`
// `actual class FakePlatformInputStream(...) : PlatformInputStream() { override fun close() ... }`
// This is how it's handled.
// The same applies to PlatformOutputStream.
// The `actual typealias` on JVM/iOS point to types that already have `close()`.
// The `actual class CommonTestByteArrayPlatformOutputStream` also provides `close()`.

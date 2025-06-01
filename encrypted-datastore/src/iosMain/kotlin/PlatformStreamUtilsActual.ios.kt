package com.dayanruben.datastore.encrypted

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.Foundation.NSOutputStream
import platform.Foundation.NSStreamDataWrittenToMemoryStreamKey
import platform.Foundation.NSStreamStatus
import platform.Foundation.NSStreamStatusNotOpen
import platform.Foundation.propertyForKey
import platform.posix.memcpy
import platform.posix.uint8_tVar
import kotlinx.cinterop.CPointer

actual class ByteArrayPlatformOutputStream : NSOutputStream(null, 0u) { // Call a base NSOutputStream constructor

    private val _backingStore: NSMutableData = NSMutableData.data()!!
    private var _streamOpen: Boolean = false

    init {
        // This class is a placeholder. In a real scenario, it would need to correctly
        // implement the NSOutputStream contract using _backingStore or similar.
        _streamOpen = true // Simulate stream being open
    }

    actual fun toByteArray(): ByteArray {
        return _backingStore.toByteArray()
    }

    // Implement NSOutputStream abstract methods (or override as needed)
    // These are minimal placeholders to make the class concrete.

    override fun write(buffer: CPointer<uint8_tVar>?, length: ULong): Long {
        if (!_streamOpen || buffer == null || length == 0UL) return -1L // Or 0L if stream is just full
        // Simulate writing to backing store
        // In a real implementation, copy bytes from buffer to _backingStore
        // For now, just append a representation or do nothing functional for the write.
        // This is a placeholder: actual data from `buffer` should be appended.
        // _backingStore.appendBytes(buffer, length) // This is the conceptual operation
        println("Warning: ByteArrayPlatformOutputStream.write on iOS is a placeholder.")
        // Let's pretend we wrote some bytes to avoid infinite loops if calling code checks return value
        // return length.toLong() // Placeholder: assume all written
        // To be safer for a placeholder:
        _backingStore.appendData(NSData.dataWithBytes(buffer, length)) // Actually append
        return length.toLong()

    }

    override fun hasSpaceAvailable(): Boolean {
        return _streamOpen // Placeholder
    }

    override fun streamStatus(): NSStreamStatus {
        return if (_streamOpen) platform.Foundation.NSStreamStatusWriting else platform.Foundation.NSStreamStatusClosed
    }

    override fun streamError(): NSError? {
        return null // Placeholder
    }

    override fun open() {
        _streamOpen = true
        // No actual stream to open, just manage state
    }

    override fun close() {
        _streamOpen = false
        // No actual stream to close, just manage state
    }
}

actual fun PlatformOutputStream.platformWriteBytes(bytes: ByteArray) {
    // `this` is an NSOutputStream due to typealias
    if (bytes.isEmpty()) return

    // If 'this' is our ByteArrayPlatformOutputStream, its write method will be called.
    // If it's another NSOutputStream, that write method will be called.
    bytes.usePinned { pinned ->
        var offset = 0
        val totalBytes = bytes.size
        while (offset < totalBytes) {
            val bytesToWrite = (totalBytes - offset)
            // The CPointer needs to be of type CValuesRef<uint8_tVar /* = UByteVar */>?, which is CPointer<UByte>?
            // pinned.addressOf(offset) gives CPointer<ByteVar>
            // Need to cast or use appropriate pointer type if NSOutputStream.write is strict
            val result = this.write(pinned.addressOf(offset) as CPointer<uint8_tVar>?, bytesToWrite.toULong())

            if (result < 0L) {
                println("Warning: Failed to write bytes to NSOutputStream: ${this.streamError()?.localizedDescription}")
                return
            }
            offset += result.toInt()
            if (result == 0L && offset < totalBytes) {
                println("Warning: NSOutputStream indicates no space, but not all bytes written.")
                return
            }
        }
    }
}

// Helper to convert NSData to ByteArray
fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

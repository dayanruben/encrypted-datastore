package com.dayanruben.datastore.encrypted

import platform.Foundation.NSData
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import platform.Foundation.dataWithBytes
import platform.Foundation.inputStreamWithData
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.objcPtr
import platform.posix.memcpy

actual typealias PlatformInputStream = NSInputStream
actual typealias PlatformOutputStream = NSOutputStream

actual fun PlatformInputStream.platformReadBytes(): ByteArray {
    // Placeholder: A proper implementation would read from NSInputStream into a ByteArray.
    // This is non-trivial due to how streams are handled in Foundation.
    // For now, returning an empty array or throwing NotImplementedError is safer.
    // Consider using kotlinx-io or a dedicated stream reading utility for iOS.
    // if (!this.hasBytesAvailable()) return ByteArray(0)
    // val data = NSMutableData.data()!!
    // val buffer = ByteArray(4096)
    // while (this.hasBytesAvailable()) {
    //     val len = this.read(buffer.toUByteArray(), buffer.size.toULong()).toInt()
    //     if (len <= 0) break
    //     data.appendBytes(buffer.refTo(0), len.toULong())
    // }
    // return data.toByteArray()
    println("Warning: PlatformInputStream.platformReadBytes() on iOS is a placeholder.")
    return ByteArray(0) // Placeholder
}

actual fun ByteArray.toPlatformInputStream(): PlatformInputStream {
    // This conversion is relatively safe.
    if (this.isEmpty()) {
        return NSInputStream.inputStreamWithData(NSData.data())
    }
    return this.usePinned { pinned ->
        NSInputStream.inputStreamWithData(
            NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
        )
    }
}

// Helper to convert NSData to ByteArray (can be moved to a common utility if needed)
fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

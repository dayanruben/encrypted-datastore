package com.dayanruben.datastore.encrypted

import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream

actual typealias PlatformInputStream = InputStream
actual typealias PlatformOutputStream = OutputStream

actual fun PlatformInputStream.platformReadBytes(): ByteArray {
    // For InputStream, can use extension readBytes() from kotlin.io
    return this.readBytes()
}

actual fun ByteArray.toPlatformInputStream(): PlatformInputStream {
    return ByteArrayInputStream(this)
}

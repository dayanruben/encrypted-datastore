package com.dayanruben.datastore.encrypted

import java.io.ByteArrayOutputStream
import java.io.OutputStream

actual typealias ByteArrayPlatformOutputStream = ByteArrayOutputStream

actual fun PlatformOutputStream.platformWriteBytes(bytes: ByteArray) {
    (this as OutputStream).write(bytes)
}

// Actual for close() in PlatformInputStream / PlatformOutputStream
// Since PlatformInputStream and PlatformOutputStream are typealiased to
// java.io.InputStream and java.io.OutputStream respectively, and these
// implement java.io.Closeable, the close() method is already available.
// No explicit actual needed for the close() methods themselves if defined in expect class.
// However, if we had `expect abstract class PlatformInputStream { abstract fun close() }`
// then an `actual abstract class AndroidPlatformInputStream : java.io.InputStream() { override fun close() = super.close() }`
// would be needed.
// The current setup `expect class PlatformInputStream { fun close() }` with
// `actual typealias PlatformInputStream = java.io.InputStream` works because `java.io.InputStream` has `close()`.

// Let's verify the expect class structure for PlatformInputStream/OutputStream
// `expect class PlatformInputStream { fun close() }`
// This means the actual class or typealias *must* provide a public close() method.
// java.io.InputStream does, so the typealias is fine.

package com.dayanruben.datastore.encrypted

import androidx.datastore.core.Serializer // Actual AndroidX Serializer
import java.io.InputStream
import java.io.OutputStream

/**
 * Actual implementation of PlatformSerializer for Android.
 * It directly implements the androidx.datastore.core.Serializer interface.
 * Common code will use `PlatformSerializer`, and on Android, this actual
 * implementation will be provided, which IS an AndroidX Serializer.
 */
actual interface PlatformSerializer<T> : Serializer<T> {
    actual override val defaultValue: T

    // These methods are already defined in androidx.datastore.core.Serializer
    // actual override suspend fun readFrom(input: PlatformInputStream): T
    // actual override suspend fun writeTo(t: T, output: PlatformOutputStream)

    // Implement the expected methods by delegating to the Android Serializer's methods.
    // Since PlatformInputStream is typealiased to InputStream, and
    // PlatformOutputStream to OutputStream on Android, these methods align.
    actual override suspend fun readFrom(input: PlatformInputStream): T = readFrom(input as InputStream)
    actual override suspend fun writeTo(t: T, output: PlatformOutputStream): Unit = writeTo(t, output as OutputStream)
}

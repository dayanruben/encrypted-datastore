package com.dayanruben.datastore.encrypted

import kotlinx.serialization.SerializationException

/**
 * Actual declaration for the PlatformSerializer interface on iOS.
 * Concrete implementations will conform to this.
 */
actual interface PlatformSerializer<T> {
    actual val defaultValue: T

    @Throws(CommonCorruptionException::class)
    actual suspend fun readFrom(input: PlatformInputStream): T

    @Throws(SerializationException::class)
    actual suspend fun writeTo(t: T, output: PlatformOutputStream)
}

// Placeholder for a concrete implementation or factory if needed later.
// For now, just providing the actual interface is sufficient to satisfy the expect.
// A typical iOS app might use a library like Multiplatform-Settings or SQLDelight
// as a "datastore" and then provide a PlatformSerializer adapter if needed.
// Or, one could implement a file-based serializer using Foundation APIs.

class PlaceholderIosSerializer<T>(actual override val defaultValue: T) : PlatformSerializer<T> {
    actual override suspend fun readFrom(input: PlatformInputStream): T {
        input.close() // Close the stream as we are not reading
        println("Warning: PlaceholderIosSerializer.readFrom() called. Returns defaultValue.")
        // In a real scenario, attempt to read and deserialize from input.
        // If input is empty or corrupted, and defaultValue is appropriate, return it.
        // Otherwise, throw CommonCorruptionException.
        return defaultValue // Or throw CommonCorruptionException("Read not implemented")
    }

    actual override suspend fun writeTo(t: T, output: PlatformOutputStream) {
        output.close() // Close the stream as we are not writing
        println("Warning: PlaceholderIosSerializer.writeTo() called. Does nothing.")
        // In a real scenario, serialize t and write to output.
        // Throw SerializationException if issues occur.
    }
}

package com.dayanruben.datastore.encrypted

import kotlinx.serialization.SerializationException // Or a custom CorruptionException equivalent

/**
 * Platform-specific serializer interface corresponding to androidx.datastore.core.Serializer.
 */
expect interface PlatformSerializer<T> {
    /**
     * The default value of this type, which is used to create the initial data store file
     * if it doesnot exist.
     */
    val defaultValue: T

    /**
     * Reads data from the given [input] and decodes it into an object of type [T].
     *
     * @param input the platform-specific input stream to read from.
     * @return the decoded object.
     * @throws SerializationException if data cannot be de-serialized.
     */
    @Throws(SerializationException::class)
    suspend fun readFrom(input: PlatformInputStream): T

    /**
     * Encodes the given [t] and writes it to the [output].
     *
     * @param t the object to be encoded.
     * @param output the platform-specific output stream to write to.
     * @throws SerializationException if data cannot be serialized.
     */
    @Throws(SerializationException::class)
    suspend fun writeTo(t: T, output: PlatformOutputStream)
}

// Common CorruptionException, can be specialized if needed
open class CommonCorruptionException(message: String, cause: Throwable? = null) : SerializationException(message, cause)

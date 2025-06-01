package com.dayanruben.datastore.encrypted

// This file can contain common crypto-related helpers that use the expect interfaces.

/**
 * Helper function to decrypt data from a PlatformInputStream using an ExpectedAead.
 * This reads all bytes from the stream, decrypts them, and returns a new PlatformInputStream.
 * Note: This is suitable for non-streaming AEAD. For streaming, use ExpectedStreamingAead.
 */
internal fun ExpectedAead.newDecryptedPlatformStream(inputStream: PlatformInputStream): PlatformInputStream {
    // The original check for inputStream.available() > 0 might need a platform-agnostic way
    // or be handled differently. For now, let's assume platformReadBytes() handles empty streams gracefully
    // or returns an empty ByteArray.
    val encryptedData = inputStream.platformReadBytes() // Uses expect fun

    // If encryptedData is empty, some AEAD implementations might throw an error.
    // The original code checked available() > 0. If platformReadBytes() returns empty for empty stream:
    return if (encryptedData.isNotEmpty()) {
        val decryptedData = this.decrypt(encryptedData, null) // Uses ExpectedAead.decrypt
        decryptedData.toPlatformInputStream() // Uses expect fun
    } else {
        // Return a new empty stream or the original one if it can be reused.
        // For simplicity, returning a new empty stream from an empty byte array.
        ByteArray(0).toPlatformInputStream()
    }
}

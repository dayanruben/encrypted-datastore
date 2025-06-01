package com.dayanruben.datastore.encrypted

/**
 * Expected interface for AEAD (Authenticated Encryption with Associated Data) operations.
 * Corresponds to com.google.crypto.tink.Aead on Android/JVM.
 */
expect interface ExpectedAead {
    /**
     * Encrypts [plaintext] with [associatedData] as associated authenticated data.
     *
     * @param plaintext the plaintext to encrypt.
     * @param associatedData associated data to be authenticated but not encrypted.
     * @return the concatenation of the initialization vector and the ciphertext.
     * @throws GeneralSecurityException if encryption fails.
     */
    @Throws(CommonGeneralSecurityException::class)
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray

    /**
     * Decrypts [ciphertext] with [associatedData] as associated authenticated data.
     *
     * @param ciphertext the ciphertext to decrypt.
     * @param associatedData associated data to be authenticated but not encrypted.
     * @return the decrypted plaintext.
     * @throws GeneralSecurityException if decryption fails.
     */
    @Throws(CommonGeneralSecurityException::class)
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray
}

/**
 * Expected interface for Streaming AEAD operations.
 * Corresponds to com.google.crypto.tink.StreamingAead on Android/JVM.
 */
expect interface ExpectedStreamingAead {
    /**
     * Returns a new encrypting PlatformOutputStream that encrypts the written data using [ciphertextDestination]
     * as ciphertext destination, and [associatedData] as associated data.
     *
     * The PlatformOutputStream must be closed after all data has been written. Closing the stream typically
     * writes the last chunk of ciphertext and a tag to the [ciphertextDestination].
     */
    @Throws(CommonGeneralSecurityException::class)
    fun newEncryptingStream(ciphertextDestination: PlatformOutputStream, associatedData: ByteArray): PlatformOutputStream

    /**
     * Returns a new decrypting PlatformInputStream that decrypts the data from [ciphertextSource] using
     * [associatedData] as associated data.
     */
    @Throws(CommonGeneralSecurityException::class)
    fun newDecryptingStream(ciphertextSource: PlatformInputStream, associatedData: ByteArray): PlatformInputStream
}

// Common GeneralSecurityException, can be specialized if needed
open class CommonGeneralSecurityException(message: String, cause: Throwable? = null) : Exception(message, cause)

package com.dayanruben.datastore.encrypted

// Actual interfaces for iOS crypto operations.
// Concrete implementations will use Apple's CryptoKit or CommonCrypto.

actual interface ExpectedAead {
    actual fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray
    actual fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray
}

actual interface ExpectedStreamingAead {
    actual fun newEncryptingStream(ciphertextDestination: PlatformOutputStream, associatedData: ByteArray): PlatformOutputStream
    actual fun newDecryptingStream(ciphertextSource: PlatformInputStream, associatedData: ByteArray): PlatformInputStream
}

// --- Placeholder Implementations ---

class NotSupportedAead : ExpectedAead {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
        throw CommonGeneralSecurityException("AEAD encryption is not supported on this platform yet.")
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
        throw CommonGeneralSecurityException("AEAD decryption is not supported on this platform yet.")
    }
}

class NotSupportedStreamingAead : ExpectedStreamingAead {
    override fun newEncryptingStream(ciphertextDestination: PlatformOutputStream, associatedData: ByteArray): PlatformOutputStream {
        throw CommonGeneralSecurityException("Streaming AEAD encryption is not supported on this platform yet.")
    }

    override fun newDecryptingStream(ciphertextSource: PlatformInputStream, associatedData: ByteArray): PlatformInputStream {
        throw CommonGeneralSecurityException("Streaming AEAD decryption is not supported on this platform yet.")
    }
}

// Factory functions or dependency injection would provide these placeholders for iOS builds.
// For example:
// fun getActualAead(): ExpectedAead = NotSupportedAead()
// fun getActualStreamingAead(): ExpectedStreamingAead = NotSupportedStreamingAead()

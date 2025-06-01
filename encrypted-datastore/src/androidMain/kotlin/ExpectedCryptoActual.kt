package com.dayanruben.datastore.encrypted

import com.google.crypto.tink.Aead as TinkAead
import com.google.crypto.tink.StreamingAead as TinkStreamingAead
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

actual interface ExpectedAead {
    actual fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray
    actual fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray
}

actual interface ExpectedStreamingAead {
    actual fun newEncryptingStream(ciphertextDestination: PlatformOutputStream, associatedData: ByteArray): PlatformOutputStream
    actual fun newDecryptingStream(ciphertextSource: PlatformInputStream, associatedData: ByteArray): PlatformInputStream
}

// Wrapper class for TinkAead to implement ExpectedAead
class AndroidTinkAead(private val tinkAead: TinkAead) : ExpectedAead {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
        return try {
            tinkAead.encrypt(plaintext, associatedData)
        } catch (e: GeneralSecurityException) {
            throw CommonGeneralSecurityException(e.message ?: "Encryption failed", e)
        }
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
        return try {
            tinkAead.decrypt(ciphertext, associatedData)
        } catch (e: GeneralSecurityException) {
            throw CommonGeneralSecurityException(e.message ?: "Decryption failed", e)
        }
    }
}

// Wrapper class for TinkStreamingAead to implement ExpectedStreamingAead
class AndroidTinkStreamingAead(private val tinkStreamingAead: TinkStreamingAead) : ExpectedStreamingAead {
    override fun newEncryptingStream(ciphertextDestination: PlatformOutputStream, associatedData: ByteArray): PlatformOutputStream {
        return try {
            tinkStreamingAead.newEncryptingStream(ciphertextDestination as OutputStream, associatedData)
        } catch (e: GeneralSecurityException) {
            throw CommonGeneralSecurityException(e.message ?: "Failed to create encrypting stream", e)
        }
    }

    override fun newDecryptingStream(ciphertextSource: PlatformInputStream, associatedData: ByteArray): PlatformInputStream {
        return try {
            tinkStreamingAead.newDecryptingStream(ciphertextSource as InputStream, associatedData)
        } catch (e: GeneralSecurityException) {
            throw CommonGeneralSecurityException(e.message ?: "Failed to create decrypting stream", e)
        }
    }
}

// Helper to convert actual Tink Aead to our expected Aead
fun TinkAead.toExpectedAead(): ExpectedAead = AndroidTinkAead(this)
fun TinkStreamingAead.toExpectedStreamingAead(): ExpectedStreamingAead = AndroidTinkStreamingAead(this)

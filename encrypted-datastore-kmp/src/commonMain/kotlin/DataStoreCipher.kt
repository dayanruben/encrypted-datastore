package com.dayanruben.datastore.encrypted.kmp

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Concurrent-safe AES-256-GCM boundary for an encrypted DataStore record.
 *
 * Implementations must generate a fresh random 12-byte nonce for each encryption and return
 * `nonce || ciphertext || 16-byte tag`. Decryption must authenticate the complete record and
 * [associatedData] before returning any plaintext. Custom implementations are trusted security code.
 * These operations run in the calling coroutine; use a background scope for DataStore.
 */
public interface DataStoreCipher {
    public suspend fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    public suspend fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

/**
 * Imports a 32-byte AES key supplied by the application's secure key management.
 *
 * Reuse the returned cipher for the store's lifetime. This function neither generates nor persists
 * keys and does not modify [key]. It takes an owned copy before importing the key into the platform
 * provider. Temporary key copies are cleared best-effort; runtime/provider copies can remain.
 */
public suspend fun aesGcmCipher(key: ByteArray): DataStoreCipher {
    require(key.size == 32) { "AES-256-GCM requires a 32-byte key" }
    currentCoroutineContext().ensureActive()
    val copy = key.copyOf()
    try {
        val imported = CryptographyProvider.Default.get(AES.GCM)
            .keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, copy)
        return ProviderDataStoreCipher(imported)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw EncryptionException("DataStore cipher initialization failed", e)
    } finally {
        copy.fill(0)
    }
}

private class ProviderDataStoreCipher(private val key: AES.GCM.Key) : DataStoreCipher {
    override suspend fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        currentCoroutineContext().ensureActive()
        return try {
            key.cipher().encrypt(plaintext, associatedData).also { currentCoroutineContext().ensureActive() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw EncryptionException("DataStore encryption failed", e)
        }
    }

    override suspend fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        currentCoroutineContext().ensureActive()
        if (ciphertext.size < 28) throw AuthenticationException()
        return try {
            key.cipher().decrypt(ciphertext, associatedData).also { currentCoroutineContext().ensureActive() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthenticationException(e)
        }
    }
}

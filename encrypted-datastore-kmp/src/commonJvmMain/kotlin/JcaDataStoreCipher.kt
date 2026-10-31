package com.dayanruben.datastore.encrypted.kmp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.InvalidKeyException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A new JCA operation object per call, including for nonexportable Android Keystore keys. */
internal class JcaDataStoreCipher(private val key: SecretKey) : DataStoreCipher {
    override suspend fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        try {
            val operation = Cipher.getInstance("AES/GCM/NoPadding")
            operation.init(Cipher.ENCRYPT_MODE, key)
            operation.updateAAD(associatedData)
            val nonce = operation.iv
            if (nonce.size != 12) throw EncryptionException("DataStore cipher returned an invalid nonce length")
            val encrypted = operation.doFinal(plaintext)
            currentCoroutineContext().ensureActive()
            nonce + encrypted
        } catch (e: CancellationException) {
            throw e
        } catch (e: EncryptionException) {
            throw e
        } catch (e: InvalidKeyException) {
            throw KeyStorageException("DataStore key is unavailable", cause = e)
        } catch (e: Exception) {
            throw EncryptionException("DataStore encryption failed", e)
        }
    }

    override suspend fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        if (ciphertext.size < 28) throw AuthenticationException()
        try {
            val operation = Cipher.getInstance("AES/GCM/NoPadding")
            operation.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext, 0, 12))
            operation.updateAAD(associatedData)
            val plaintext = operation.doFinal(ciphertext, 12, ciphertext.size - 12)
            try {
                currentCoroutineContext().ensureActive()
                plaintext
            } catch (e: CancellationException) {
                plaintext.fill(0)
                throw e
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidKeyException) {
            throw KeyStorageException("DataStore key is unavailable", cause = e)
        } catch (e: Exception) {
            throw AuthenticationException(e)
        }
    }
}

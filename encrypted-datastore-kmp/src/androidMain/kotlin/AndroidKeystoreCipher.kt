package com.dayanruben.datastore.encrypted.kmp

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private val keyCreationMutex = Mutex()

/**
 * Explicitly creates a new nonexportable AES-256-GCM key. An existing [alias] is never replaced.
 *
 * Use only during first-time provisioning; reopen with [openAndroidKeystoreCipher] on subsequent
 * launches. Creation is serialized within this process. Coordinate provisioning outside this API
 * if multiple processes share the alias. Hardware/StrongBox backing and biometric gates are not
 * implied. Cancellation can occur after the platform persists a key; inspect/open it on retry.
 */
public suspend fun createAndroidKeystoreCipher(alias: String): DataStoreCipher {
    require(alias.isNotBlank()) { "Keystore alias must not be blank" }
    return withContext(Dispatchers.IO) {
        keyCreationMutex.withLock {
            try {
                val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (store.containsAlias(alias)) throw KeyStorageException("DataStore key already exists")
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                generator.init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
                JcaDataStoreCipher(generator.generateKey())
            } catch (e: CancellationException) {
                throw e
            } catch (e: KeyStorageException) {
                throw e
            } catch (e: Exception) {
                throw KeyStorageException("DataStore key creation failed", cause = e)
            }
        }
    }
}

/** Opens an existing key. A missing, invalidated, or inaccessible key is never recreated. */
public suspend fun openAndroidKeystoreCipher(alias: String): DataStoreCipher {
    require(alias.isNotBlank()) { "Keystore alias must not be blank" }
    return withContext(Dispatchers.IO) {
        keyCreationMutex.withLock {
            try {
                val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val key = store.getKey(alias, null) as? SecretKey
                    ?: throw KeyStorageException("DataStore key does not exist or is not an AES key")
                if (key.algorithm != KeyProperties.KEY_ALGORITHM_AES) {
                    throw KeyStorageException("DataStore key is not an AES key")
                }
                val info = SecretKeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    .getKeySpec(key, KeyInfo::class.java) as KeyInfo
                val requiredPurposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                if (info.keySize != 256 || info.purposes and requiredPurposes != requiredPurposes ||
                    KeyProperties.BLOCK_MODE_GCM !in info.blockModes ||
                    KeyProperties.ENCRYPTION_PADDING_NONE !in info.encryptionPaddings
                ) {
                    throw KeyStorageException("DataStore key does not meet AES-256-GCM requirements")
                }
                JcaDataStoreCipher(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: KeyStorageException) {
                throw e
            } catch (e: Exception) {
                throw KeyStorageException("DataStore key could not be opened", cause = e)
            }
        }
    }
}

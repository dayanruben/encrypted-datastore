package com.dayanruben.datastore.encrypted.kmp

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AndroidKeystoreCipherTest {
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Test
    fun createsNonExportableKeyAndReopens() = runTest {
        val alias = "encrypted-datastore-test-${UUID.randomUUID()}"
        try {
            val created = createAndroidKeystoreCipher(alias)
            val plaintext = "persisted".encodeToByteArray()
            val ciphertext = created.encrypt(plaintext, byteArrayOf(1))
            assertNull(keyStore().getKey(alias, null).encoded)
            assertContentEquals(plaintext, openAndroidKeystoreCipher(alias).decrypt(ciphertext, byteArrayOf(1)))
            assertFailsWith<AuthenticationException> { created.decrypt(ciphertext, byteArrayOf(2)) }
        } finally { keyStore().deleteEntry(alias) }
    }

    @Test
    fun duplicateCreationCannotReplaceTheExistingKey() = runTest {
        val alias = "encrypted-datastore-test-${UUID.randomUUID()}"
        try {
            val first = createAndroidKeystoreCipher(alias)
            val ciphertext = first.encrypt(byteArrayOf(7), byteArrayOf())
            assertFailsWith<KeyStorageException> { createAndroidKeystoreCipher(alias) }
            assertContentEquals(byteArrayOf(7), openAndroidKeystoreCipher(alias).decrypt(ciphertext, byteArrayOf()))
        } finally { keyStore().deleteEntry(alias) }
    }

    @Test
    fun concurrentCreatesHaveOnlyOneWinner() = runTest {
        val alias = "encrypted-datastore-test-${UUID.randomUUID()}"
        try {
            val results = (0 until 10).map {
                async(Dispatchers.Default) { runCatching { createAndroidKeystoreCipher(alias) } }
            }.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(9, results.count { it.exceptionOrNull() is KeyStorageException })
            val ciphertext = results.single { it.isSuccess }.getOrThrow().encrypt(byteArrayOf(9), byteArrayOf())
            assertContentEquals(byteArrayOf(9), openAndroidKeystoreCipher(alias).decrypt(ciphertext, byteArrayOf()))
        } finally { keyStore().deleteEntry(alias) }
    }

    @Test
    fun missingKeyIsNotRecreatedAndBlankAliasIsRejected() = runTest {
        val alias = "encrypted-datastore-test-${UUID.randomUUID()}"
        assertFailsWith<KeyStorageException> { openAndroidKeystoreCipher(alias) }
        assertFalse(keyStore().containsAlias(alias))
        assertFailsWith<IllegalArgumentException> { createAndroidKeystoreCipher(" ") }
        assertFailsWith<IllegalArgumentException> { openAndroidKeystoreCipher("") }
    }

    @Test
    fun refusesAnExistingKeyWithTheWrongSecurityParameters() = runTest {
        val alias = "encrypted-datastore-test-${UUID.randomUUID()}"
        try {
            val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(128)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generator.generateKey()
            assertFailsWith<KeyStorageException> { openAndroidKeystoreCipher(alias) }
        } finally { keyStore().deleteEntry(alias) }
    }
}

package com.dayanruben.datastore.encrypted.kmp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.decodeHex
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class JcaDataStoreCipherTest {
    @Test
    fun decryptsTheIndependentKnownAnswer() = runTest {
        val cipher = JcaDataStoreCipher(SecretKeySpec(ByteArray(32), "AES"))
        assertContentEquals(byteArrayOf(), cipher.decrypt(
            ("00".repeat(12) + "530f8afbc74536b9a963b4f1c4cb738b").decodeHex().toByteArray(), byteArrayOf(),
        ))
    }

    @Test
    fun interoperatesWithThePortableProviderInBothDirections() = runTest {
        val jca = JcaDataStoreCipher(SecretKeySpec(ByteArray(32), "AES"))
        val portable = aesGcmCipher(ByteArray(32))
        val plaintext = "cross-provider".encodeToByteArray()
        val aad = byteArrayOf(1, 2)
        assertContentEquals(plaintext, portable.decrypt(jca.encrypt(plaintext, aad), aad))
        assertContentEquals(plaintext, jca.decrypt(portable.encrypt(plaintext, aad), aad))
        assertFailsWith<AuthenticationException> { jca.decrypt(portable.encrypt(plaintext, aad), byteArrayOf(3)) }
    }

    @Test
    fun concurrentOperationsHaveIndependentCipherState() = runTest {
        val jca = JcaDataStoreCipher(SecretKeySpec(ByteArray(32), "AES"))
        (0 until 100).map { value ->
            async(Dispatchers.Default) {
                val plaintext = value.toString().encodeToByteArray()
                assertContentEquals(plaintext, jca.decrypt(jca.encrypt(plaintext, byteArrayOf()), byteArrayOf()))
            }
        }.awaitAll()
    }
}

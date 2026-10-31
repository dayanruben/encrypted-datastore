package com.dayanruben.datastore.encrypted.kmp

import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.decodeHex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DataStoreCipherTest {
    @Test
    fun knownAnswerDecrypts() = runTest {
        // NIST AES-256-GCM: zero key, 96-bit zero IV, no plaintext or AAD.
        val cipher = aesGcmCipher(ByteArray(32))
        val record = ("00".repeat(12) + "530f8afbc74536b9a963b4f1c4cb738b").decodeHex().toByteArray()
        assertContentEquals(byteArrayOf(), cipher.decrypt(record, byteArrayOf()))
    }

    @Test
    fun noncesDiffer() = runTest {
        val cipher = aesGcmCipher(ByteArray(32) { 17 })
        val message = "settings".encodeToByteArray()
        val first = cipher.encrypt(message, byteArrayOf())
        val second = cipher.encrypt(message, byteArrayOf())
        assertFalse(first.copyOfRange(0, 12).contentEquals(second.copyOfRange(0, 12)))
        assertContentEquals(message, cipher.decrypt(first, byteArrayOf()))
        assertContentEquals(message, cipher.decrypt(second, byteArrayOf()))
    }

    @Test
    fun rejectsWrongKeyAndAad() = runTest {
        val cipher = aesGcmCipher(ByteArray(32))
        val ciphertext = cipher.encrypt(byteArrayOf(1, 2, 3), byteArrayOf(4))
        assertFailsWith<AuthenticationException> { cipher.decrypt(ciphertext, byteArrayOf(5)) }
        assertFailsWith<AuthenticationException> {
            aesGcmCipher(ByteArray(32) { 1 }).decrypt(ciphertext, byteArrayOf(4))
        }
        assertFailsWith<AuthenticationException> { cipher.decrypt(ByteArray(27), byteArrayOf()) }
    }

    @Test
    fun rejectsNon256BitKey() = runTest {
        for (size in listOf(0, 16, 24, 33)) {
            assertFailsWith<IllegalArgumentException> { aesGcmCipher(ByteArray(size)) }
        }
    }

    @Test
    fun importsKeyWithoutRetainingCallerArray() = runTest {
        val key = ByteArray(32) { 3 }
        val cipher = aesGcmCipher(key)
        val ciphertext = cipher.encrypt(byteArrayOf(7), byteArrayOf())
        key.fill(9)
        assertContentEquals(byteArrayOf(7), cipher.decrypt(ciphertext, byteArrayOf()))
        assertContentEquals(byteArrayOf(7), aesGcmCipher(ByteArray(32) { 3 }).decrypt(ciphertext, byteArrayOf()))
    }

    @Test
    fun supportsConcurrentOperations() = runTest {
        val cipher = aesGcmCipher(ByteArray(32))
        (0 until 100).map { value ->
            async(Dispatchers.Default) {
                val plaintext = value.toString().encodeToByteArray()
                assertContentEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext, byteArrayOf()), byteArrayOf()))
            }
        }.awaitAll()
    }
}

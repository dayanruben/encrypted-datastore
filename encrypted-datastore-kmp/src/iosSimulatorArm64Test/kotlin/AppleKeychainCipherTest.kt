@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.dayanruben.datastore.encrypted.kmp

import kotlinx.cinterop.*
import kotlinx.coroutines.test.runTest
import platform.CoreFoundation.*
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSUUID
import platform.Security.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppleKeychainCipherTest {
    @Test
    fun createsProtectedKeyAndReopens() = runTest {
        withTestItem { service, account ->
            val created = createAppleKeychainCipher(service, account)
            val ciphertext = created.encrypt(byteArrayOf(1, 2, 3), byteArrayOf(4))
            assertContentEquals(byteArrayOf(1, 2, 3), openAppleKeychainCipher(service, account).decrypt(ciphertext, byteArrayOf(4)))
            assertFailsWith<AuthenticationException> { created.decrypt(ciphertext, byteArrayOf(5)) }
            testQuery(service, account) { query ->
                CFDictionarySetValue(query, kSecReturnAttributes, kCFBooleanTrue)
                memScoped {
                    val result = alloc<CFTypeRefVar>()
                    assertEquals(errSecSuccess, SecItemCopyMatching(query, result.ptr))
                    val attributes: CFDictionaryRef = requireNotNull(result.value).reinterpret()
                    try {
                        val protection: CFStringRef = requireNotNull(CFDictionaryGetValue(attributes, kSecAttrAccessible)).reinterpret()
                        assertEquals(kCFCompareEqualTo, CFStringCompare(
                            protection,
                            kSecAttrAccessibleWhenUnlockedThisDeviceOnly, 0UL,
                        ))
                    } finally { CFRelease(attributes) }
                }
            }
        }
    }

    @Test
    fun duplicateCreationPreservesTheExistingKey() = runTest {
        withTestItem { service, account ->
            val created = createAppleKeychainCipher(service, account)
            val ciphertext = created.encrypt(byteArrayOf(7), byteArrayOf())
            assertEquals(errSecDuplicateItem, assertFailsWith<KeyStorageException> {
                createAppleKeychainCipher(service, account)
            }.status)
            assertContentEquals(byteArrayOf(7), openAppleKeychainCipher(service, account).decrypt(ciphertext, byteArrayOf()))
        }
    }

    @Test
    fun missingKeyIsNotRecreated() = runTest {
        withTestItem { service, account ->
            assertEquals(errSecItemNotFound, assertFailsWith<KeyStorageException> {
                openAppleKeychainCipher(service, account)
            }.status)
            testQuery(service, account) { assertEquals(errSecItemNotFound, SecItemCopyMatching(it, null)) }
        }
    }

    @Test
    fun wrongSizedStoredKeyIsRejected() = runTest {
        withTestItem { service, account ->
            testQuery(service, account) { query ->
                val value = ByteArray(16).usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), 16) }!!
                try {
                    CFDictionarySetValue(query, kSecValueData, value)
                    CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                    assertEquals(errSecSuccess, SecItemAdd(query, null))
                } finally { CFRelease(value) }
            }
            assertFailsWith<KeyStorageException> { openAppleKeychainCipher(service, account) }
        }
    }

    @Test
    fun rejectsBlankServiceAndAccount() = runTest {
        assertFailsWith<IllegalArgumentException> { createAppleKeychainCipher("", "settings") }
        assertFailsWith<IllegalArgumentException> { createAppleKeychainCipher("service", " ") }
        assertFailsWith<IllegalArgumentException> { openAppleKeychainCipher(" ", "settings") }
        assertFailsWith<IllegalArgumentException> { openAppleKeychainCipher("service", "") }
    }
}

private suspend fun withTestItem(block: suspend (String, String) -> Unit) {
    val service = "com.dayanruben.encrypted-datastore-tests.${NSUUID().UUIDString}"
    val account = "settings 🔒"
    try { block(service, account) } finally { testQuery(service, account) { SecItemDelete(it) } }
}

private fun <T> testQuery(service: String, account: String, block: (CFMutableDictionaryRef) -> T): T {
    val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
    val nativeService = CFBridgingRetain(service)!!
    val nativeAccount = CFBridgingRetain(account)!!
    try {
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, nativeService)
        CFDictionarySetValue(query, kSecAttrAccount, nativeAccount)
        CFDictionarySetValue(query, kSecAttrSynchronizable, kCFBooleanFalse)
        CFDictionarySetValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
        return block(query)
    } finally {
        CFRelease(nativeService)
        CFRelease(nativeAccount)
        CFRelease(query)
    }
}

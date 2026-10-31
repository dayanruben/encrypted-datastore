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

private const val PRIMARY_GROUP = "com.dayanruben.encrypted-datastore.tests"
private const val SHARED_GROUP = "com.dayanruben.encrypted-datastore.tests.shared"

class AppleKeychainCipherTest {
    @Test
    fun createsProtectedKeyAndReopens() = runTest {
        withTestItem { service, account ->
            val created = createAppleKeychainCipher(service, account, PRIMARY_GROUP)
            val ciphertext = created.encrypt(byteArrayOf(1, 2, 3), byteArrayOf(4))
            assertContentEquals(byteArrayOf(1, 2, 3), openAppleKeychainCipher(service, account, PRIMARY_GROUP).decrypt(ciphertext, byteArrayOf(4)))
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
            val created = createAppleKeychainCipher(service, account, PRIMARY_GROUP)
            val ciphertext = created.encrypt(byteArrayOf(7), byteArrayOf())
            assertEquals(errSecDuplicateItem, assertFailsWith<KeyStorageException> {
                createAppleKeychainCipher(service, account, PRIMARY_GROUP)
            }.status)
            assertContentEquals(byteArrayOf(7), openAppleKeychainCipher(service, account, PRIMARY_GROUP).decrypt(ciphertext, byteArrayOf()))
        }
    }

    @Test
    fun missingKeyIsNotRecreated() = runTest {
        withTestItem { service, account ->
            assertEquals(errSecItemNotFound, assertFailsWith<KeyStorageException> {
                openAppleKeychainCipher(service, account, PRIMARY_GROUP)
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
            assertFailsWith<KeyStorageException> { openAppleKeychainCipher(service, account, PRIMARY_GROUP) }
        }
    }

    @Test
    fun openingAGroupNeverSearchesAnotherEntitledGroup() = runTest {
        withTestItem { service, account ->
            testQuery(service, account, SHARED_GROUP) { query ->
                val data = ByteArray(32) { 1 }.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), 32) }!!
                try {
                    CFDictionarySetValue(query, kSecValueData, data)
                    CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                    assertEquals(errSecSuccess, SecItemAdd(query, null))
                } finally { CFRelease(data) }
            }
            assertEquals(errSecItemNotFound, assertFailsWith<KeyStorageException> {
                openAppleKeychainCipher(service, account, PRIMARY_GROUP)
            }.status)
        }
    }

    @Test
    fun equalServiceAccountsInDifferentGroupsHaveIndependentStableKeys() = runTest {
        withTestItem { service, account ->
            val primary = createAppleKeychainCipher(service, account, PRIMARY_GROUP)
            val shared = createAppleKeychainCipher(service, account, SHARED_GROUP)
            val first = primary.encrypt(byteArrayOf(1), byteArrayOf())
            val second = shared.encrypt(byteArrayOf(2), byteArrayOf())
            assertContentEquals(byteArrayOf(1), openAppleKeychainCipher(service, account, PRIMARY_GROUP).decrypt(first, byteArrayOf()))
            assertContentEquals(byteArrayOf(2), openAppleKeychainCipher(service, account, SHARED_GROUP).decrypt(second, byteArrayOf()))
            assertFailsWith<AuthenticationException> { primary.decrypt(second, byteArrayOf()) }
            assertFailsWith<KeyStorageException> { createAppleKeychainCipher(service, account, SHARED_GROUP) }
        }
    }

    @Test
    fun rejectsBlankServiceAndAccount() = runTest {
        assertFailsWith<IllegalArgumentException> { createAppleKeychainCipher("", "settings", PRIMARY_GROUP) }
        assertFailsWith<IllegalArgumentException> { createAppleKeychainCipher("service", " ", PRIMARY_GROUP) }
        assertFailsWith<IllegalArgumentException> { openAppleKeychainCipher(" ", "settings", PRIMARY_GROUP) }
        assertFailsWith<IllegalArgumentException> { openAppleKeychainCipher("service", "", PRIMARY_GROUP) }
        assertFailsWith<IllegalArgumentException> { createAppleKeychainCipher("service", "account", " ") }
        assertFailsWith<IllegalArgumentException> { openAppleKeychainCipher("service", "account", "") }
    }
}

private suspend fun withTestItem(block: suspend (String, String) -> Unit) {
    val service = "com.dayanruben.encrypted-datastore-tests.${NSUUID().UUIDString}"
    val account = "settings 🔒"
    try { block(service, account) } finally {
        for (group in listOf(PRIMARY_GROUP, SHARED_GROUP)) testQuery(service, account, group) { SecItemDelete(it) }
    }
}

private fun <T> testQuery(service: String, account: String, group: String = PRIMARY_GROUP, block: (CFMutableDictionaryRef) -> T): T {
    val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
    val nativeService = CFBridgingRetain(service)!!
    val nativeAccount = CFBridgingRetain(account)!!
    val nativeGroup = CFBridgingRetain(group)!!
    try {
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, nativeService)
        CFDictionarySetValue(query, kSecAttrAccount, nativeAccount)
        CFDictionarySetValue(query, kSecAttrAccessGroup, nativeGroup)
        CFDictionarySetValue(query, kSecAttrSynchronizable, kCFBooleanFalse)
        CFDictionarySetValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
        return block(query)
    } finally {
        CFRelease(nativeService)
        CFRelease(nativeAccount)
        CFRelease(nativeGroup)
        CFRelease(query)
    }
}

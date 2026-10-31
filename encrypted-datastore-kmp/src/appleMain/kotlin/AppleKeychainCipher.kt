@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.dayanruben.datastore.encrypted.kmp

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreFoundation.*
import platform.Foundation.CFBridgingRetain
import platform.Security.*

/**
 * Explicitly provisions a random AES-256 key in the Apple data-protection Keychain.
 *
 * Items are nonsynchronizing and accessible only while unlocked on this device. An existing
 * [service]/[account]/[accessGroup] identity is never overwritten. [accessGroup] must be the exact
 * entitled group, including its signing prefix. Both creation and lookup use that same identity;
 * neither searches other entitled groups. Signed applications need valid Keychain entitlements;
 * missing entitlements and locked storage fail with [KeyStorageException.status]. This is not a
 * Secure Enclave AES key: the key is imported into process memory while the cipher is alive.
 * Cancellation can occur after the item is persisted; use [openAppleKeychainCipher] on retry.
 */
public suspend fun createAppleKeychainCipher(service: String, account: String, accessGroup: String): DataStoreCipher {
    validateKeyIdentity(service, account)
    require(accessGroup.isNotBlank()) { "Keychain access group must not be blank" }
    return withContext(Dispatchers.IO) {
        val key = ByteArray(32)
        try {
            val randomStatus = key.usePinned {
                SecRandomCopyBytes(kSecRandomDefault, key.size.convert(), it.addressOf(0))
            }
            if (randomStatus != errSecSuccess) throw KeyStorageException("Secure key generation failed", randomStatus)
            val cipher = aesGcmCipher(key)
            withKeychainQuery(service, account, accessGroup) { query ->
                val data = key.usePinned {
                    CFDataCreate(null, it.addressOf(0).reinterpret(), key.size.convert())
                } ?: throw KeyStorageException("DataStore key allocation failed")
                try {
                    CFDictionarySetValue(query, kSecValueData, data)
                    CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                    val status = SecItemAdd(query, null)
                    if (status != errSecSuccess) throw KeyStorageException("DataStore key could not be created", status)
                } finally {
                    CFRelease(data)
                }
            }
            cipher
        } finally {
            key.fill(0)
        }
    }
}

/** Opens an existing Keychain key. A missing, locked, or inaccessible item is never recreated. */
public suspend fun openAppleKeychainCipher(service: String, account: String, accessGroup: String): DataStoreCipher {
    validateKeyIdentity(service, account)
    require(accessGroup.isNotBlank()) { "Keychain access group must not be blank" }
    return withContext(Dispatchers.IO) {
        val key = readKeychainKey(service, account, accessGroup)
        try { aesGcmCipher(key) } finally { key.fill(0) }
    }
}

private fun validateKeyIdentity(service: String, account: String) {
    require(service.isNotBlank()) { "Keychain service must not be blank" }
    require(account.isNotBlank()) { "Keychain account must not be blank" }
}

private fun readKeychainKey(service: String, account: String, accessGroup: String): ByteArray =
    withKeychainQuery(service, account, accessGroup) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        CFDictionarySetValue(query, kSecUseAuthenticationUI, kSecUseAuthenticationUIFail)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            result.value = null
            val status = SecItemCopyMatching(query, result.ptr)
            try {
                if (status != errSecSuccess) throw KeyStorageException("DataStore key could not be opened", status)
                val value = result.value ?: throw KeyStorageException("DataStore key has no data")
                if (CFGetTypeID(value) != CFDataGetTypeID()) throw KeyStorageException("DataStore key has an invalid type")
                val data: CFDataRef = value.reinterpret()
                if (CFDataGetLength(data) != 32L) throw KeyStorageException("DataStore key must be 32 bytes")
                CFDataGetBytePtr(data)!!.reinterpret<ByteVar>().readBytes(32)
            } finally {
                result.value?.let { CFRelease(it) }
            }
        }
    }

private fun <T> withKeychainQuery(service: String, account: String, accessGroup: String, block: (CFMutableDictionaryRef) -> T): T {
    val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        ?: throw KeyStorageException("DataStore key query allocation failed")
    val nativeService = CFBridgingRetain(service)!!
    val nativeAccount = CFBridgingRetain(account)!!
    val nativeGroup = CFBridgingRetain(accessGroup)!!
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

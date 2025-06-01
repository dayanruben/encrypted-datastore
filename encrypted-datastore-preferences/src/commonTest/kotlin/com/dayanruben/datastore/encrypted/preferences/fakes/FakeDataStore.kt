package com.dayanruben.datastore.encrypted.preferences.fakes

import androidx.datastore.core.DataStore // Common DataStore interface
import com.dayanruben.datastore.encrypted.ExpectedAead
import com.dayanruben.datastore.encrypted.preferences.PlatformPreferences
import com.dayanruben.datastore.encrypted.preferences.createEncryptedPreferencesDataStore
import com.dayanruben.datastore.encrypted.preferences.createPlatformPreferencesDataStore
import com.dayanruben.datastore.encrypted.preferences.checkPreferencesFileExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job // For CoroutineScope

// --- FakeDataStore for commonTest ---
class FakeDataStore<T>(
    private val initialData: T,
    private val scope: CoroutineScope
) : DataStore<T> {

    private val _data = MutableStateFlow(initialData)
    override val data: Flow<T> = _data.asStateFlow()

    var updateDataCalledCount = 0
        private set
    var lastTransform: (suspend (t: T) -> T)? = null
        private set

    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        updateDataCalledCount++
        lastTransform = transform
        _data.update { currentData ->
            transform(currentData)
        }
        return _data.value
    }

    // Helper to manually set data for test setup
    fun setData(newData: T) {
        _data.value = newData
    }
}

// --- Actuals for DataStore factory functions for commonTest ---
actual fun createEncryptedPreferencesDataStore(
    producePath: () -> String,
    aead: ExpectedAead, // This will use the FakeExpectedAead re-created below or from other module
    corruptionHandler: ((Exception) -> Unit)?,
    scope: CoroutineScope
): DataStore<PlatformPreferences> {
    // Path can be ignored for fake in-memory datastore for now, or used for logging
    println("Fake createEncryptedPreferencesDataStore called for path: ${producePath()}")
    // The serializer is needed to get the defaultValue
    val serializer = CommonTestPlatformPreferencesSerializer()
    return FakeDataStore(serializer.defaultValue, scope)
}

actual fun createPlatformPreferencesDataStore(
    producePath: () -> String,
    corruptionHandler: ((Exception) -> Unit)?,
    scope: CoroutineScope
): DataStore<PlatformPreferences> {
    println("Fake createPlatformPreferencesDataStore called for path: ${producePath()}")
    val serializer = CommonTestPlatformPreferencesSerializer()
    return FakeDataStore(serializer.defaultValue, scope)
}

// --- Actual for File Path Utilities for commonTest ---
actual fun String.checkPreferencesFileExtension(): String {
    // No-op for commonTest or a very basic check if desired
    println("Fake String.checkPreferencesFileExtension called for: $this")
    if (!this.endsWith(".preferences_pb") && !this.endsWith(".fake_prefs")) {
        // throw IllegalStateException("File extension for file: $this does not match required extension .preferences_pb or .fake_prefs")
        // For testing, let's be lenient or just log.
    }
    return this
}


// --- Re-create Minimal FakeExpectedAead for this module's commonTest ---
// To avoid complex build configurations for accessing test fakes from another module.
class MinimalFakeExpectedAead : ExpectedAead {
    var encryptCalledCount = 0
    var decryptCalledCount = 0

    // Simple XOR "encryption"
    private val key: Byte = 0xAA
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
        encryptCalledCount++
        return plaintext.map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
        decryptCalledCount++
        return ciphertext.map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
    }
}

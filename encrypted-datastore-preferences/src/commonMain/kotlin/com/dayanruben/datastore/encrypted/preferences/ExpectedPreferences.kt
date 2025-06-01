package com.dayanruben.datastore.encrypted.preferences

import com.dayanruben.datastore.encrypted.ExpectedAead // From the other module
import com.dayanruben.datastore.encrypted.PlatformSerializer // From the other module

// --- Core Preferences Abstractions ---

/**
 * Expected typealias for Preferences.
 * Corresponds to androidx.datastore.preferences.core.Preferences on Android.
 */
expect class PlatformPreferences {
    // Keep required methods if not using typealias directly to an interface/class with them
    // For typealias, these are not needed here as they come from the actual type.
    // fun <T> get(key: PlatformPreferenceKey<T>): T?
    // fun contains(key: PlatformPreferenceKey<*>): Boolean
    // fun asMap(): Map<PlatformPreferenceKey<*>, Any>
}


/**
 * Expected typealias for a Preferences Key.
 * Corresponds to androidx.datastore.preferences.core.Preferences.Key on Android.
 */
expect class PlatformPreferenceKey<T>(val name: String)
// This form allows `PlatformPreferenceKey<Int>("myKey")` in common code.
// The actual on Android will be `actual typealias PlatformPreferenceKey<T> = androidx.datastore.preferences.core.Preferences.Key<T>`.
// This is valid because Preferences.Key<T>(name: String) constructor exists.


// --- DataStore Abstraction ---
// Re-using DataStore<T> concept. The `T` will be `PlatformPreferences`.
// The `encrypted-datastore` module defines `PlatformSerializer<T>`. We need one for `PlatformPreferences`.

/**
 * Expected factory for creating a DataStore that persists PlatformPreferences.
 * The actual creation might involve platform-specific mechanisms.
 *
 * @param producePath A function that returns the platform-specific path (as a String) for the data.
 * @param corruptionHandler Optional handler for data corruption. Type Any? for now, to be refined.
 * @param scope Coroutine scope for the DataStore.
 */
expect fun createPlatformPreferencesDataStore(
    producePath: () -> String,
    corruptionHandler: ((Exception) -> Unit)? = null, // Simplified corruption handler
    scope: kotlinx.coroutines.CoroutineScope
): androidx.datastore.core.DataStore<PlatformPreferences>

// The above might be too generic. Let's focus on the encrypted factory.

/**
 * Expected function to create an encrypted DataStore for PlatformPreferences.
 *
 * @param producePath Function providing the file path as a String.
 * @param aead The AEAD implementation for encryption/decryption.
 * @param corruptionHandler Optional handler for data corruption.
 * @param scope Coroutine scope for the DataStore.
 */
expect fun createEncryptedPreferencesDataStore(
    producePath: () -> String,
    aead: ExpectedAead,
    corruptionHandler: ((Exception) -> Unit)? = null, // Simplified
    // migrations: List<DataMigration<PlatformPreferences>> = listOf(), // Migrations are complex; defer.
    scope: kotlinx.coroutines.CoroutineScope
): androidx.datastore.core.DataStore<PlatformPreferences>


// --- Preferences Serializer ---
// This needs to be a PlatformSerializer<PlatformPreferences>
// The actual on Android will adapt androidx.datastore.preferences.core.PreferencesSerializer

expect class PlatformPreferencesSerializer : PlatformSerializer<PlatformPreferences> {
    // If PlatformPreferences becomes a typealias, this serializer needs to work with that actual type.
    // The constructor `actual constructor()` is defined in the actual.
}


// --- File Path Utilities ---
/**
 * Checks if the given file path string has the expected preferences extension.
 * Throws an IllegalStateException if not.
 */
expect fun String.checkPreferencesFileExtension(): String

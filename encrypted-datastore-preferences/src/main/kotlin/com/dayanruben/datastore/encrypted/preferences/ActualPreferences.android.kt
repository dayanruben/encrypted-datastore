package com.dayanruben.datastore.encrypted.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer // Real Android one
import com.dayanruben.datastore.encrypted.ExpectedAead
import com.dayanruben.datastore.encrypted.PlatformSerializer
import com.dayanruben.datastore.encrypted.internal.asJvmSerializer // From encrypted-datastore (Android actual)
import com.dayanruben.datastore.encrypted.encrypted // From encrypted-datastore (common)
import kotlinx.coroutines.CoroutineScope
import java.io.File

// --- Actual PlatformPreferences ---
actual typealias PlatformPreferences = Preferences

// No wrapper class (AndroidPreferencesWrapper) needed if using typealias.

// --- Actual PlatformPreferenceKey ---
actual typealias PlatformPreferenceKey<T> = Preferences.Key<T>
// This works because `expect class PlatformPreferenceKey<T>(val name: String)`
// matches the constructor of `androidx.datastore.preferences.core.Preferences.Key<T>(name: String)`.


// --- Actual PlatformPreferencesSerializer ---
// This needs to be a PlatformSerializer<PlatformPreferences>, which is now PlatformSerializer<Preferences>.
// Android's PreferencesSerializer is an OkioSerializer<Preferences>.
// We need to convert OkioSerializer<Preferences> to PlatformSerializer<Preferences> (which is Serializer<Preferences> on Android).
actual class PlatformPreferencesSerializer actual constructor() :
    PlatformSerializer<PlatformPreferences> by androidx.datastore.preferences.core.PreferencesSerializer.asJvmSerializer()
// No .adapt(...) needed anymore because PlatformPreferences is Preferences.
// The asJvmSerializer() extension comes from the `encrypted-datastore` module (androidMain actual)
// and converts an OkioSerializer<T> to a PlatformSerializer<T> (which is Serializer<T> on Android).


// Helper extension to adapt a PlatformSerializer<A> to PlatformSerializer<B>
// This is no longer needed here if PlatformPreferences is a typealias.
// fun <A : Any, B : Any> PlatformSerializer<A>.adapt(
//     adaptTo: (A) -> B,
//     adaptFrom: (B) -> A
// ): PlatformSerializer<B> { ... }


// --- Actual createEncryptedPreferencesDataStore ---
actual fun createEncryptedPreferencesDataStore(
    producePath: () -> String,
    aead: ExpectedAead,
    corruptionHandler: ((Exception) -> Unit)?,
    scope: CoroutineScope
): DataStore<PlatformPreferences> {
    val produceFile = { File(producePath()) }

    // We need a PlatformSerializer<PlatformPreferences> for the .encrypted() call
    val platformPrefsSerializer = PlatformPreferencesSerializer()

    // The .encrypted() extension is on PlatformSerializer, and it expects an ExpectedAead.
    // This should work directly.
    val encryptedBaseSerializer = platformPrefsSerializer.encrypted(aead)

    val delegateDataStore: DataStore<PlatformPreferences> = androidx.datastore.core.DataStoreFactory.create(
        serializer = encryptedBaseSerializer, // This is now PlatformSerializer<PlatformPreferences>
        corruptionHandler = corruptionHandler?.let { handler ->
            object : androidx.datastore.core.handlers.ReplaceFileCorruptionHandler<PlatformPreferences> {
                override suspend fun replaceFile(ex: Exception): PlatformPreferences {
                    handler(ex)
                    // Must return a default value or rethrow
                    return platformPrefsSerializer.defaultValue
                }
            }
        },
        scope = scope,
        produceFile = { produceFile().checkPreferencesFileExtensionActual() }
    )
    // This does not return a PreferenceDataStore, but a raw DataStore<PlatformPreferences>
    // The original code wrapped this in `androidx.datastore.preferences.core.PreferenceDataStore(delegate)`
    // If common code needs specific Preference-like operations not on DataStore<T> directly,
    // then PlatformDataStore might need to be an expect interface itself.
    // For now, returning DataStore<PlatformPreferences>.
    return delegateDataStore
}

// --- Actual createPlatformPreferencesDataStore (non-encrypted) ---
actual fun createPlatformPreferencesDataStore(
    producePath: () -> String,
    corruptionHandler: ((Exception) -> Unit)?,
    scope: CoroutineScope
): DataStore<PlatformPreferences> {
     val produceFile = { File(producePath()) }
     val platformPrefsSerializer = PlatformPreferencesSerializer()

    val delegateDataStore: DataStore<PlatformPreferences> = androidx.datastore.core.DataStoreFactory.create(
        serializer = platformPrefsSerializer,
         corruptionHandler = corruptionHandler?.let { handler ->
            object : androidx.datastore.core.handlers.ReplaceFileCorruptionHandler<PlatformPreferences> {
                override suspend fun replaceFile(ex: Exception): PlatformPreferences {
                    handler(ex)
                    return platformPrefsSerializer.defaultValue
                }
            }
        },
        scope = scope,
        produceFile = { produceFile().checkPreferencesFileExtensionActual() }
    )
    return delegateDataStore
}


// --- Actual File Path Utilities ---
// Corresponds to private const val FILE_EXTENSION = "preferences_pb"
private const val ANDROID_PREFERENCES_FILE_EXTENSION = "preferences_pb"

// Actual for String.checkPreferencesFileExtension()
actual fun String.checkPreferencesFileExtension(): String = apply {
    val file = File(this)
    check(file.extension == ANDROID_PREFERENCES_FILE_EXTENSION) {
        "File extension for file: $this does not match required extension for Preferences file: $ANDROID_PREFERENCES_FILE_EXTENSION"
    }
}

// Internal helper used by the actual create functions
private fun File.checkPreferencesFileExtensionActual(): File = apply {
    check(extension == ANDROID_PREFERENCES_FILE_EXTENSION) {
        "File extension for file: $this does not match required extension for Preferences file: $ANDROID_PREFERENCES_FILE_EXTENSION"
    }
}

package com.dayanruben.datastore.encrypted.kmp

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path

/**
 * Creates a single-process encrypted DataStore using upstream transactional Okio storage.
 *
 * Maintain one instance per absolute [path]. [scope] owns its lifetime and must use a background
 * dispatcher. Cancel and join that scope before reopening the file. [serializer] must produce an
 * immutable type. Missing files return its default; existing invalid ciphertext is never replaced.
 * Supply a stable [storeId] and the same persisted key when reopening. No plaintext/legacy fallback
 * or destructive corruption handler is installed. This format differs from the legacy Tink API.
 */
public fun <T> createEncryptedDataStore(
    serializer: OkioSerializer<T>,
    cipher: DataStoreCipher,
    storeId: String,
    path: Path,
    scope: CoroutineScope,
    fileSystem: FileSystem = systemFileSystem(),
    migrations: List<DataMigration<T>> = emptyList(),
    maxPlaintextBytes: Int = 1_048_576,
): DataStore<T> {
    require(path.isAbsolute) { "Encrypted DataStore requires an absolute path" }
    val normalizedPath = path.normalized()
    return DataStoreFactory.create(
        storage = OkioStorage(
            fileSystem = fileSystem,
            serializer = serializer.encrypted(cipher, storeId, maxPlaintextBytes),
            producePath = { normalizedPath },
        ),
        migrations = migrations,
        scope = scope,
    )
}

/**
 * Creates encrypted Preferences with the same key, identity, limit and lifetime contract as
 * [createEncryptedDataStore]. [path] must end in `.preferences_pb`. The public AndroidX Preferences
 * factory freezes values after each update to prevent mutations outside a transaction.
 */
public fun createEncryptedPreferencesDataStore(
    cipher: DataStoreCipher,
    storeId: String,
    path: Path,
    scope: CoroutineScope,
    fileSystem: FileSystem = systemFileSystem(),
    migrations: List<DataMigration<Preferences>> = emptyList(),
    maxPlaintextBytes: Int = 1_048_576,
): DataStore<Preferences> {
    require(path.isAbsolute) { "Encrypted DataStore requires an absolute path" }
    require(path.name.substringAfterLast('.', "") == "preferences_pb") {
        "Encrypted Preferences requires the .preferences_pb extension"
    }
    val normalizedPath = path.normalized()
    return PreferenceDataStoreFactory.create(
        storage = OkioStorage(
            fileSystem = fileSystem,
            serializer = PreferencesSerializer.encrypted(cipher, storeId, maxPlaintextBytes),
            producePath = { normalizedPath },
        ),
        migrations = migrations,
        scope = scope,
    )
}

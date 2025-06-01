package com.dayanruben.security.crypto.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import com.dayanruben.datastore.encrypted.PlatformSerializer // From encrypted-datastore module
import kotlinx.coroutines.CoroutineScope

// Using androidx.datastore.core.CorruptionException if available in common, otherwise a common one.
// Assuming CorruptionException is available via datastore-core dependency in commonMain.
import androidx.datastore.core.CorruptionException


/**
 * Expected function to create a DataStore instance.
 * This abstracts the platform-specific DataStoreFactory.create call.
 *
 * @param serializer The KMP PlatformSerializer for type T.
 * @param corruptionHandler Optional handler for data corruption. This one matches DataStoreFactory's.
 * @param migrations List of data migrations.
 * @param scope Coroutine scope for the DataStore.
 * @param produceFile Function that returns the PlatformFile to be used by the DataStore.
 */
internal expect fun <T> createPlatformDataStoreInternal(
    serializer: PlatformSerializer<T>,
    corruptionHandler: androidx.datastore.core.handlers.ReplaceFileCorruptionHandler<T>? = null,
    migrations: List<DataMigration<T>> = listOf(),
    scope: CoroutineScope,
    produceFile: () -> PlatformFile // Uses our PlatformFile
): DataStore<T>

@file:Suppress("FunctionName")

package com.dayanruben.security.crypto.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.dayanruben.datastore.encrypted.PlatformSerializer
import com.dayanruben.datastore.encrypted.encrypted // Extension from encrypted-datastore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Creates a DataStore that is encrypted using an underlying PlatformEncryptedFile.
 *
 * @param produceEncryptedFile A function that produces the [PlatformEncryptedFile]. This allows deferring
 *                             file creation/initialization until DataStore is actually needed.
 * @param serializer The [PlatformSerializer] for the data type T.
 * @param corruptionHandler Optional [ReplaceFileCorruptionHandler] for handling data corruption.
 * @param migrations List of [DataMigration]s to apply.
 * @param scope The [CoroutineScope] for the DataStore.
 * @param associatedData Optional associated data for AEAD encryption. It's recommended to use
 *                       contextually relevant data, like the file name or path, if not using
 *                       the default from PlatformEncryptedFile's AEAD.
 * @return A [DataStore<T>] instance.
 */
public fun <T> createEncrypted(
    // Old signature: produceEncryptedFile: () -> PlatformEncryptedFile,
    // New signature will require parameters to create master key and then AEAD
    produceFile: () -> PlatformFile, // Changed from PlatformEncryptedFile to PlatformFile
    masterKeyAlias: String,
    masterKeySpec: PlatformKeyGenParameterSpec, // Defined in PlatformKeyProvider.kt
    platformContext: PlatformContext, // Defined in PlatformDataContext.kt
    serializer: PlatformSerializer<T>,
    corruptionHandler: ReplaceFileCorruptionHandler<T>? = null,
    migrations: List<DataMigration<T>> = listOf(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    associatedData: ByteArray? = null // Allow overriding associated data
): DataStore<T> {
    // 1. Get or create the master key
    val masterKey = getOrCreatePlatformMasterKey(masterKeyAlias, masterKeySpec, platformContext)

    // 2. Create StreamingAead from the master key
    val streamingAead = createStreamingAeadFromPlatformKey(masterKey, platformContext)

    // Get the PlatformFile for associated data (e.g., file name)
    val platformFile = produceFile() // Call produceFile once

    // Use the file name from PlatformFile as default associated data if not provided
    val finalAssociatedData = associatedData ?: platformFile.name.encodeToByteArray()

    // Encrypt the base serializer
    val encryptedSerializer = serializer.encrypted(streamingAead, finalAssociatedData)

    // Use the internal expect factory to create the DataStore
    return createPlatformDataStoreInternal(
        serializer = encryptedSerializer,
        corruptionHandler = corruptionHandler,
        migrations = migrations,
        scope = scope,
        produceFile = { platformFile } // Pass the already obtained PlatformFile
    )
}

// Note: The original EncryptedDataStoreFactory had multiple overloads.
// This KMP version consolidates them. If specific overloads are needed (e.g., one that
// takes file paths directly and internally builds the PlatformEncryptedFile), they can be added.
// For example, an overload that takes path, alias, spec, context:

public fun <T> createEncryptedWithPath( // Renamed for clarity, if keeping the other overload
    filePath: okio.Path, // Use Okio Path directly
    masterKeyAlias: String,
    masterKeySpec: PlatformKeyGenParameterSpec,
    platformContext: PlatformContext, // Required for key and EncryptedFile creation
    serializer: PlatformSerializer<T>,
    corruptionHandler: ReplaceFileCorruptionHandler<T>? = null,
    migrations: List<DataMigration<T>> = listOf(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    associatedData: ByteArray? = null
): DataStore<T> {
    // This overload simplifies by creating PlatformFile internally.
    // The `produceFile` lambda for the main createEncrypted now directly gives PlatformFile.
    return createEncrypted(
        produceFile = { createPlatformFile(filePath) },
        masterKeyAlias = masterKeyAlias,
        masterKeySpec = masterKeySpec,
        platformContext = platformContext,
        serializer = serializer,
        corruptionHandler = corruptionHandler,
        migrations = migrations,
        scope = scope,
        associatedData = associatedData
    )
}
// The commented out overload that used PlatformEncryptedFileBuilder is now effectively replaced
// by the main createEncrypted function's new signature and this createEncryptedWithPath helper.

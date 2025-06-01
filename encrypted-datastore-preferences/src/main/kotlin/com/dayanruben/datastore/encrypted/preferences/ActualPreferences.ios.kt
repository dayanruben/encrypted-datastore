package com.dayanruben.datastore.encrypted.preferences

import com.dayanruben.datastore.encrypted.ExpectedAead
import com.dayanruben.datastore.encrypted.PlatformInputStream
import com.dayanruben.datastore.encrypted.PlatformOutputStream
import com.dayanruben.datastore.encrypted.PlatformSerializer
import kotlinx.coroutines.CoroutineScope
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDictionary
import platform.Foundation.NSString
import platform.Foundation.NSNumber
import platform.Foundation.NSData
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.URLByAppendingPathComponent
import platform.Foundation.pathExtension
import platform.Foundation.stringByAppendingPathExtension
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

// --- Actual PlatformPreferences for iOS ---
// This will be a wrapper around a mutable map, simulating basic preference storage.
// A real implementation might use NSUserDefaults or a plist file.
actual class PlatformPreferences actual constructor() { // Add actual constructor if expect class has one implicitly or explicitly
    private val values: MutableMap<PlatformPreferenceKey<*>, Any> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: PlatformPreferenceKey<T>): T? {
        return values[key] as? T
    }

    fun contains(key: PlatformPreferenceKey<*>): Boolean {
        return values.containsKey(key)
    }

    fun asMap(): Map<PlatformPreferenceKey<*>, Any> {
        return values.toMap()
    }

    // iOS specific methods to modify for the fake implementation
    fun <T> put(key: PlatformPreferenceKey<T>, value: T) {
        values[key] = value as Any
    }

    fun <T> remove(key: PlatformPreferenceKey<T>) {
        values.remove(key)
    }

    fun clear() {
        values.clear()
    }
}

// --- Actual PlatformPreferenceKey for iOS ---
actual class PlatformPreferenceKey<T> actual constructor(actual val name: String) {
    // Just stores the name, type T is for generic use.
    // Equality and hashcode are important if used in Maps.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlatformPreferenceKey<*>) return false
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

// --- Actual PlatformPreferencesSerializer for iOS ---
actual class PlatformPreferencesSerializer actual constructor() : PlatformSerializer<PlatformPreferences> {
    // This is a placeholder. A real iOS serializer would interact with how
    // PlatformPreferences are stored (e.g., NSUserDefaults, plist file).
    // For this fake, we'll (de)serialize our in-memory map representation.
    // This is highly simplified and not robust.

    actual override val defaultValue: PlatformPreferences by lazy { PlatformPreferences() } // Empty preferences

    actual override suspend fun readFrom(input: PlatformInputStream): PlatformPreferences {
        // Placeholder: try to read a simple map representation (e.g., JSON, or Ktor equivalent)
        // For now, just return default value or throw.
        // This would involve complex parsing if we were to serialize the map properly.
        println("Warning: iOS PlatformPreferencesSerializer.readFrom is a placeholder and returns default.")
        input.close() // Important to close the input stream
        return defaultValue
    }

    actual override suspend fun writeTo(t: PlatformPreferences, output: PlatformOutputStream) {
        // Placeholder: try to write a simple map representation.
        println("Warning: iOS PlatformPreferencesSerializer.writeTo is a placeholder.")
        // In a real scenario, you'd serialize t.asMap()
        // For example, convert to a JSON string and write bytes.
        // val jsonString = someJsonEncode(t.asMap()) // Map keys would need to be strings
        // output.platformWriteBytes(jsonString.encodeToByteArray())
        output.close() // Important to close the output stream
    }
}

// --- Actual createEncryptedPreferencesDataStore for iOS ---
actual fun createEncryptedPreferencesDataStore(
    producePath: () -> String,
    aead: ExpectedAead,
    corruptionHandler: ((Exception) -> Unit)?,
    scope: CoroutineScope
): androidx.datastore.core.DataStore<PlatformPreferences> {
    // This is a placeholder. A real iOS implementation would need:
    // 1. A real PlatformPreferencesSerializer for iOS.
    // 2. An iOS DataStore implementation (file-based, using NSFileManager).
    //    The `encrypted-datastore` module's PlatformSerializer and stream utils for iOS are also placeholders.
    println("Warning: createEncryptedPreferencesDataStore on iOS is a placeholder and uses a dummy DataStore.")

    val filePath = producePath() // Get the path string

    // Use the placeholder serializer
    val iosPlatformPrefsSerializer = PlatformPreferencesSerializer()
    val encryptedSerializer = iosPlatformPrefsSerializer.encrypted(aead) // common extension

    // Create a dummy DataStore for now that operates in-memory or throws.
    // This requires a KMP DataStoreFactory or a KMP implementation of DataStore.
    // The common `androidx.datastore.core.DataStoreFactory.create` might not be directly usable
    // if its `produceFile` expects a specific type not available here or if its underlying storage is JVM only.
    // For now, returning a simple in-memory fake DataStore.

    return object : androidx.datastore.core.DataStore<PlatformPreferences> {
        private var currentData = iosPlatformPrefsSerializer.defaultValue
        override val data: kotlinx.coroutines.flow.Flow<PlatformPreferences>
            get() = kotlinx.coroutines.flow.flowOf(currentData)

        override suspend fun updateData(transform: suspend (t: PlatformPreferences) -> PlatformPreferences): PlatformPreferences {
            currentData = transform(currentData)
            return currentData
        }
    }
    // A file-based implementation would look more like:
    // return androidx.datastore.core.DataStoreFactory.create(
    //     serializer = encryptedSerializer,
    //     scope = scope,
    //     corruptionHandler = corruptionHandler?.let { ch ->
    //         object : androidx.datastore.core.handlers.ReplaceFileCorruptionHandler<PlatformPreferences> {
    //             override suspend fun replaceFile(ex: Exception): PlatformPreferences {
    //                 ch(ex)
    //                 return iosPlatformPrefsSerializer.defaultValue
    //             }
    //         }
    //     },
    //     produceFile = { filePath.toNsFile().also { it.parentFile?.mkdirs() } } // Needs path to file util
    // )
    // This depends on `toNsFile()` and KMP-compatible file operations for `produceFile`.
}

// --- Actual createPlatformPreferencesDataStore (non-encrypted) for iOS ---
actual fun createPlatformPreferencesDataStore(
    producePath: () -> String,
    corruptionHandler: ((Exception) -> Unit)?,
    scope: CoroutineScope
): androidx.datastore.core.DataStore<PlatformPreferences> {
    println("Warning: createPlatformPreferencesDataStore on iOS is a placeholder and uses a dummy DataStore.")
    val iosPlatformPrefsSerializer = PlatformPreferencesSerializer()
    return object : androidx.datastore.core.DataStore<PlatformPreferences> {
        private var currentData = iosPlatformPrefsSerializer.defaultValue
        override val data: kotlinx.coroutines.flow.Flow<PlatformPreferences>
            get() = kotlinx.coroutines.flow.flowOf(currentData)
        override suspend fun updateData(transform: suspend (t: PlatformPreferences) -> PlatformPreferences): PlatformPreferences {
            currentData = transform(currentData)
            return currentData
        }
    }
}

// --- Actual File Path Utilities for iOS ---
private const val IOS_PREFERENCES_FILE_EXTENSION = "preferences_pb" // Or "plist" if using plists

actual fun String.checkPreferencesFileExtension(): String = apply {
    // In Foundation, file extensions are typically handled by NSURL methods or path manipulation.
    // For a simple string check:
    val currentExtension = this.substringAfterLast('.', "")
    check(currentExtension == IOS_PREFERENCES_FILE_EXTENSION) {
        "File extension for file: $this does not match required extension for Preferences file: $IOS_PREFERENCES_FILE_EXTENSION"
    }
}

// Helper to get a writable file path on iOS (e.g., in Application Support)
internal fun getFilePathInAppSupport(fileName: String): String {
    val appSupportDir = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true
    ).firstOrNull() as? String ?: error("Cannot find Application Support directory")

    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(appSupportDir)) {
        fm.createDirectoryAtPath(appSupportDir, true, null, null)
    }
    return appSupportDir + "/" + fileName
}

package com.dayanruben.security.crypto.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.dayanruben.datastore.encrypted.ExpectedAead
import com.dayanruben.datastore.encrypted.ExpectedStreamingAead
import com.dayanruben.datastore.encrypted.PlatformInputStream
import com.dayanruben.datastore.encrypted.PlatformOutputStream
import com.dayanruben.datastore.encrypted.PlatformSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import okio.Path
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSError
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile atomically
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import platform.Foundation.inputStreamWithData
import platform.Foundation.outputStreamToMemory // For placeholder output streams

// --- PlatformContext ---
actual class PlatformContext actual constructor() {
    // No specific content for iOS context for now, could hold app-specific paths if needed.
}

// --- PlatformMasterKey ---
actual class PlatformMasterKey actual constructor() {
    // Placeholder for iOS. Real implementation would use Keychain.
    // For now, it's just an opaque class.
    internal val iosKeyAlias: String? = null // Example internal property
}

actual enum class PlatformKeyGenParameterSpec {
    AES256_GCM;
    // No iOS specific conversion needed in the enum itself for this placeholder.
}

actual fun getOrCreatePlatformMasterKey(
    alias: String,
    spec: PlatformKeyGenParameterSpec,
    context: PlatformContext?
): PlatformMasterKey {
    println("Warning: iOS getOrCreatePlatformMasterKey is a placeholder.")
    // Real implementation: Use Keychain to store/retrieve a suitable key.
    // val query = mapOf(
    //     kSecClass to kSecClassGenericPassword,
    //     kSecAttrService to "MyDataStoreMasterKeyService",
    //     kSecAttrAccount to alias,
    //     kSecMatchLimit to kSecMatchLimitOne,
    //     kSecReturnData to kCFBooleanTrue
    // )
    // var item: CFTypeRef? = null
    // val status = SecItemCopyMatching(query as CFDictionaryRef, item)
    // if (status == errSecSuccess) { /* use key */ } else { /* generate and store */ }
    return PlatformMasterKey() // Return dummy key
}

// --- PlatformFile ---
actual interface PlatformFile {
    actual val path: Path
    actual val name: String
    actual fun exists(): Boolean
    actual fun delete(): Boolean
    actual fun parent(): PlatformFile?
    actual fun mkdirs(): Boolean
    actual fun isDirectory(): Boolean
}

class IosPlatformFile(override val path: Path) : PlatformFile {
    private val fileManager = NSFileManager.defaultManager
    private val nsPath = path.toString() // Okio Path to String for NSFileManager

    override val name: String get() = path.name
    override fun exists(): Boolean = fileManager.fileExistsAtPath(nsPath)
    override fun delete(): Boolean = try {
        fileManager.removeItemAtPath(nsPath, null)
        true
    } catch (e: Exception) { false }

    override fun parent(): PlatformFile? = path.parent?.let { IosPlatformFile(it) }
    override fun mkdirs(): Boolean = try {
        fileManager.createDirectoryAtPath(nsPath, true, null, null) // withIntermediateDirectories = true
        true
    } catch (e: Exception) { false }
    override fun isDirectory(): Boolean = memScoped {
        val isDirectoryVar = alloc<BooleanVar>()
        if (fileManager.fileExistsAtPath(nsPath, isDirectory = isDirectoryVar.ptr)) {
            return isDirectoryVar.value
        }
        return false
    }
}

actual fun createPlatformFile(path: Path): PlatformFile {
    return IosPlatformFile(path)
}

// --- PlatformEncryptedFile ---
actual class PlatformEncryptedFile actual constructor() {
    actual lateinit var file: PlatformFile // Should be set by builder

    // These are placeholders. Real encrypted file I/O is complex on iOS.
    // Would likely use CommonCrypto or CryptoKit directly on file data.
    actual fun openInputStream(): PlatformInputStream {
        println("Warning: iOS PlatformEncryptedFile.openInputStream is a placeholder.")
        if (!file.exists()) throw Exception("File not found: ${file.path}")
        // Placeholder: returns unencrypted file data if it exists, or empty stream.
        val nsData = NSData.dataWithContentsOfURL(NSURL(fileURLWithPath = file.path.toString())) ?: NSData()
        return nsData.toPlatformInputStream() // Needs actual for ByteArray.toPlatformInputStream -> NSInputStream
    }

    actual fun openOutputStream(): PlatformOutputStream {
        println("Warning: iOS PlatformEncryptedFile.openOutputStream is a placeholder.")
        // Placeholder: returns a memory stream. Data written here won't be encrypted to file.
        return NSOutputStream.outputStreamToMemory() as PlatformOutputStream // Needs actual for NSOutputStream -> PlatformOutputStream
    }
}

// Helper extension for NSData to PlatformInputStream (assuming PlatformInputStream is NSInputStream on iOS)
fun NSData.toPlatformInputStream(): PlatformInputStream = NSInputStream.inputStreamWithData(this) as PlatformInputStream


actual class PlatformEncryptedFileBuilder actual constructor() {
    private var platformFile: PlatformFile? = null
    // iOS might not use masterKey/context directly in EncryptedFile if crypto is handled separately
    // private var masterKey: PlatformMasterKey? = null
    // private var context: PlatformContext? = null

    actual fun file(platformFile: PlatformFile): PlatformEncryptedFileBuilder {
        this.platformFile = platformFile
        return this
    }

    actual fun masterKey(platformMasterKey: PlatformMasterKey): PlatformEncryptedFileBuilder {
        // this.masterKey = platformMasterKey // Store if needed for later AEAD creation
        println("Warning: iOS PlatformEncryptedFileBuilder.masterKey is noted but not used in placeholder.")
        return this
    }

    actual fun context(platformContext: PlatformContext): PlatformEncryptedFileBuilder {
        // this.context = platformContext
        println("Warning: iOS PlatformEncryptedFileBuilder.context is noted but not used in placeholder.")
        return this
    }

    actual fun build(): PlatformEncryptedFile {
        val currentFile = requireNotNull(platformFile) { "File is required for PlatformEncryptedFile" }
        val encryptedFile = PlatformEncryptedFile()
        encryptedFile.file = currentFile
        // In a real iOS impl, this builder might configure keys/streams for encryption.
        return encryptedFile
    }
}

// --- AEAD Creation from PlatformMasterKey (iOS Placeholders) ---
actual fun createAeadFromPlatformKey(
    masterKey: PlatformMasterKey,
    context: PlatformContext?
): ExpectedAead {
    println("Warning: iOS createAeadFromPlatformKey is a placeholder.")
    // Real impl: Use masterKey (from Keychain) to initialize CryptoKit/CommonCrypto AEAD.
    throw NotImplementedError("iOS AEAD creation from master key not implemented.")
}

actual fun createStreamingAeadFromPlatformKey(
    masterKey: PlatformMasterKey,
    context: PlatformContext?
): ExpectedStreamingAead {
    println("Warning: iOS createStreamingAeadFromPlatformKey is a placeholder.")
    // Real impl: Use masterKey to initialize streaming AEAD.
    throw NotImplementedError("iOS Streaming AEAD creation from master key not implemented.")
}

// --- PlatformDataStoreFactory (iOS Placeholder) ---
internal actual fun <T> createPlatformDataStoreInternal(
    serializer: PlatformSerializer<T>,
    corruptionHandler: ReplaceFileCorruptionHandler<T>?,
    migrations: List<DataMigration<T>>,
    scope: CoroutineScope,
    produceFile: () -> PlatformFile
): DataStore<T> {
    println("Warning: iOS createPlatformDataStoreInternal is a placeholder returning a dummy DataStore.")
    // This would be where the file-based DataStore for iOS (from encrypted-datastore actuals) would be used.
    // For now, a simple in-memory fake.
    return object : DataStore<T> {
        private var currentData: T = serializer.defaultValue // kotlinx.coroutines.runBlocking { serializer.defaultValue }
        override val data: kotlinx.coroutines.flow.Flow<T> = flowOf(currentData)
        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            currentData = transform(currentData)
            return currentData
        }
    }
}

package com.dayanruben.security.crypto.datastore

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.security.crypto.EncryptedFile as AndroidEncryptedFile
import androidx.security.crypto.MasterKey as AndroidMasterKey
import com.dayanruben.datastore.encrypted.ExpectedAead
import com.dayanruben.datastore.encrypted.ExpectedStreamingAead
import com.dayanruben.datastore.encrypted.PlatformInputStream
import com.dayanruben.datastore.encrypted.PlatformOutputStream
import com.dayanruben.datastore.encrypted.PlatformSerializer
import com.dayanruben.datastore.encrypted.AndroidTinkAead // Corrected import
import com.dayanruben.datastore.encrypted.AndroidTinkStreamingAead // Corrected import
import kotlinx.coroutines.CoroutineScope
import okio.Path
import okio.source
import okio.sink
import java.io.File

// --- PlatformContext ---
actual typealias PlatformContext = Context

// --- PlatformMasterKey ---
actual class PlatformMasterKey(internal val androidMasterKey: AndroidMasterKey)

actual enum class PlatformKeyGenParameterSpec {
    AES256_GCM; // Matches expect enum

    fun toAndroidKeyGenParameterSpec(): AndroidMasterKey.KeyScheme {
        return when (this) {
            AES256_GCM -> AndroidMasterKey.KeyScheme.AES256_GCM
        }
    }
}

actual fun getOrCreatePlatformMasterKey(
    alias: String,
    spec: PlatformKeyGenParameterSpec,
    context: PlatformContext?
): PlatformMasterKey {
    requireNotNull(context) { "Android context is required to get or create master key." }
    val androidMasterKey = AndroidMasterKey.Builder(context, alias)
        .setKeyScheme(spec.toAndroidKeyGenParameterSpec())
        .build()
    return PlatformMasterKey(androidMasterKey)
}

// --- PlatformFile ---
actual interface PlatformFile { // actual interface for expect interface
    actual val path: Path
    actual val name: String
    actual fun exists(): Boolean
    actual fun delete(): Boolean
    actual fun parent(): PlatformFile?
    actual fun mkdirs(): Boolean
    actual fun isDirectory(): Boolean

    // Android specific helper
    fun toJavaFile(): File
}

class AndroidPlatformFile(private val javaFile: File) : PlatformFile {
    override val path: Path by lazy { Path.get(javaFile.absolutePath) }
    override val name: String get() = javaFile.name
    override fun exists(): Boolean = javaFile.exists()
    override fun delete(): Boolean = javaFile.delete()
    override fun parent(): PlatformFile? = javaFile.parentFile?.let { AndroidPlatformFile(it) }
    override fun mkdirs(): Boolean = javaFile.mkdirs()
    override fun isDirectory(): Boolean = javaFile.isDirectory
    override fun toJavaFile(): File = javaFile
}

actual fun createPlatformFile(path: Path): PlatformFile {
    return AndroidPlatformFile(path.toFile()) // Okio Path.toFile() extension
}


// --- PlatformEncryptedFile ---
actual class PlatformEncryptedFile actual constructor() { // Default constructor if expect has none
    // Internal state needs to be set by the builder
    internal lateinit var androidEncryptedFile: AndroidEncryptedFile
    actual lateinit var file: PlatformFile // Needs to be initialized by builder

    actual fun openInputStream(): PlatformInputStream {
        return androidEncryptedFile.openFileInput() // This is java.io.InputStream
        // PlatformInputStream is typealiased to java.io.InputStream on Android
    }

    actual fun openOutputStream(): PlatformOutputStream {
        return androidEncryptedFile.openFileOutput() // This is java.io.OutputStream
        // PlatformOutputStream is typealiased to java.io.OutputStream on Android
    }

    // createOrRetrieveExpectedAead and createOrRetrieveExpectedStreamingAead removed as per expect class change
}

actual class PlatformEncryptedFileBuilder actual constructor() {
    private var context: PlatformContext? = null
    private var fileToBuild: File? = null // Using java.io.File internally for builder
    private var masterKey: AndroidMasterKey? = null
    // private var scheme: AndroidEncryptedFile.FileEncryptionScheme? = null // If scheme was added

    actual fun file(platformFile: PlatformFile): PlatformEncryptedFileBuilder {
        this.fileToBuild = (platformFile as AndroidPlatformFile).toJavaFile()
        return this
    }

    actual fun masterKey(platformMasterKey: PlatformMasterKey): PlatformEncryptedFileBuilder {
        this.masterKey = platformMasterKey.androidMasterKey
        return this
    }

    actual fun context(platformContext: PlatformContext): PlatformEncryptedFileBuilder {
        this.context = platformContext
        return this
    }

    actual fun build(): PlatformEncryptedFile {
        val currentContext = requireNotNull(context) { "Context is required to build EncryptedFile" }
        val currentFile = requireNotNull(fileToBuild) { "File is required to build EncryptedFile" }
        val currentMasterKey = requireNotNull(masterKey) { "MasterKey is required to build EncryptedFile" }

        val androidEncFile = AndroidEncryptedFile.Builder(
            currentContext,
            currentFile,
            currentMasterKey,
            AndroidEncryptedFile.DEFAULT_FILE_ENCRYPTION_SCHEME // Default scheme
        ).build()

        val result = PlatformEncryptedFile()
        result.androidEncryptedFile = androidEncFile
        result.file = AndroidPlatformFile(currentFile) // Store the PlatformFile reference
        return result
    }
}

// --- PlatformDataStoreFactory ---
internal actual fun <T> createPlatformDataStoreInternal(
    serializer: PlatformSerializer<T>, // This is Serializer<T> (Java Streams) on Android
    corruptionHandler: ReplaceFileCorruptionHandler<T>?,
    migrations: List<DataMigration<T>>,
    scope: CoroutineScope,
    produceFile: () -> PlatformFile
): DataStore<T> {
    return DataStoreFactory.create(
        serializer = serializer, // PlatformSerializer is typealiased to Android's Serializer
        corruptionHandler = corruptionHandler,
        migrations = migrations,
        scope = scope,
        produceFile = { (produceFile() as AndroidPlatformFile).toJavaFile() }
    )
}

// --- AEAD Creation from PlatformMasterKey ---
actual fun createAeadFromPlatformKey(
    masterKey: PlatformMasterKey,
    context: PlatformContext?
): ExpectedAead {
    // This is where the complex part of getting a Tink Aead from AndroidMasterKey would go.
    // AndroidMasterKey is 'androidx.security.crypto.MasterKey'.
    // We need to convert this to 'com.google.crypto.tink.Aead' and then wrap in 'AndroidTinkAead'.
    // This might involve using AndroidKeystoreKmsClient if the masterKey.androidMasterKey.mKeyAlias is a Keystore key.
    // Or, if AndroidMasterKey provides access to a KeysetHandle.
    // For now, this is a placeholder as it's non-trivial.
    println("Warning: createAeadFromPlatformKey on Android is using a placeholder/dummy AEAD.")
    // Placeholder: Create a new Tink Aead for testing purposes, not using the masterKey yet.
    // This would require adding Tink dependencies if not already present for this specific task.
    // import com.google.crypto.tink.aead.AeadKeyTemplates
    // import com.google.crypto.tink.KeysetHandle
    // val keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    // val tinkAead = keysetHandle.getPrimitive(com.google.crypto.tink.Aead::class.java)
    // return AndroidTinkAead(tinkAead) // AndroidTinkAead is from encrypted-datastore
    throw NotImplementedError("Actual implementation for createAeadFromPlatformKey from AndroidMasterKey required.")
}

actual fun createStreamingAeadFromPlatformKey(
    masterKey: PlatformMasterKey,
    context: PlatformContext?
): ExpectedStreamingAead {
    // Similar to createAeadFromPlatformKey, this is complex.
    // Android's EncryptedFile uses internal StreamingAeadFactory.
    println("Warning: createStreamingAeadFromPlatformKey on Android is using a placeholder/dummy StreamingAEAD.")
    // Placeholder:
    // import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates
    // import com.google.crypto.tink.KeysetHandle
    // val keysetHandle = KeysetHandle.generateNew(StreamingAeadKeyTemplates.AES256_GCM_HKDF_4KB)
    // val tinkStreamingAead = keysetHandle.getPrimitive(com.google.crypto.tink.StreamingAead::class.java)
    // return AndroidTinkStreamingAead(tinkStreamingAead) // AndroidTinkStreamingAead is from encrypted-datastore
    throw NotImplementedError("Actual implementation for createStreamingAeadFromPlatformKey from AndroidMasterKey required.")
}
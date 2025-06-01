package com.dayanruben.datastore.encrypted

// Removed AndroidX and Tink imports, using our platform abstractions
// import androidx.datastore.core.CorruptionException // Replaced by CommonCorruptionException
// import androidx.datastore.core.Serializer // Replaced by PlatformSerializer
// import com.google.crypto.tink.Aead // Replaced by ExpectedAead
// import com.google.crypto.tink.StreamingAead // Replaced by ExpectedStreamingAead

// Removed: import com.dayanruben.datastore.encrypted.migration.isProbablyEncryptedWithAeadException
import com.dayanruben.datastore.encrypted.migration.withDecryptionFallback // This will also need adjustment for ExpectedAead/StreamingAead
// import java.io.* // Replaced by PlatformInputStream/PlatformOutputStream related operations
// import java.security.GeneralSecurityException // Replaced by CommonGeneralSecurityException

/** Interface for [PlatformSerializer] supporting encryption. */
public sealed interface EncryptingSerializer<T> : PlatformSerializer<T>

internal abstract class WrappingEncryptingSerializer<T> : EncryptingSerializer<T> {

    protected abstract val delegate: PlatformSerializer<T>

    final override val defaultValue: T
        get() = delegate.defaultValue

    final override suspend fun readFrom(input: PlatformInputStream): T {
        return try {
            readEncryptedFrom(input)
        } catch (e: CommonGeneralSecurityException) { // Use common exception
            throw CommonCorruptionException("DataStore decryption failed", e)
        }
        // Catch other relevant exceptions like IOException, though PlatformInputStream might abstract this
    }

    /** Reads encrypted data from the given [input]. */
    protected abstract suspend fun readEncryptedFrom(input: PlatformInputStream): T
}

internal class AeadEncryptingSerializer<T>(
    private val aead: ExpectedAead, // Use ExpectedAead
    override val delegate: PlatformSerializer<T>, // Use PlatformSerializer
) : WrappingEncryptingSerializer<T>() {

    override suspend fun readEncryptedFrom(input: PlatformInputStream): T {
        // Use the helper from Aead.kt (which now uses PlatformInputStream)
        val decryptedStream = aead.newDecryptedPlatformStream(input)
        return delegate.readFrom(decryptedStream)
    }

    override suspend fun writeTo(t: T, output: PlatformOutputStream) {
        // To write, we first serialize the object to a ByteArray, then encrypt the ByteArray.
        // This requires a temporary ByteArrayOutputStream or equivalent.
        // kotlinx-io's Buffer can be used here if we adopt it more broadly.
        // For now, let's assume a way to get bytes from the delegate.
        // This is tricky without a common ByteArrayOutputStream.

        // Option 1: Add a method to PlatformSerializer to get bytes directly (not ideal)
        // Option 2: Create an expect/actual ByteArrayOutputStream
        // For now, placeholder, this needs a robust common solution for stream to byte array.
        val tempBuffer = ByteArrayPlatformOutputStream() // Needs to be an actual class
        delegate.writeTo(t, tempBuffer)
        val bytes = tempBuffer.toByteArray()

        val encryptedBytes = aead.encrypt(bytes, null)

        // output.write(encryptedBytes) // PlatformOutputStream doesn't have simple write(ByteArray)
        // This also needs a helper or expect/actual for writing ByteArray to PlatformOutputStream
        output.platformWriteBytes(encryptedBytes) // Needs to be an actual fun
    }
}

/**
 * Adds encryption to [this] serializer using the given [ExpectedAead].
 */
@Deprecated(
    "Consider using StreamingAead version for better performance with large data. " +
            "A 'withDecryptionFallback' mechanism is available for Android/JVM.",
    ReplaceWith(
        "this.encrypted(streamingAead /*, optional_fallback_aead_on_jvm */)"
        // The import "com.dayanruben.datastore.encrypted.migration.withDecryptionFallback" is no longer valid here.
        // Actual fallback usage would be platform-specific if needed.
    ),
)
public fun <T> PlatformSerializer<T>.encrypted(aead: ExpectedAead): EncryptingSerializer<T> =
    AeadEncryptingSerializer(aead, delegate = this)

internal class StreamingAeadEncryptingSerializer<T>(
    private val streamingAead: ExpectedStreamingAead, // Use ExpectedStreamingAead
    private val associatedData: ByteArray,
    override val delegate: PlatformSerializer<T>, // Use PlatformSerializer
) : WrappingEncryptingSerializer<T>() {

    override suspend fun readEncryptedFrom(input: PlatformInputStream): T {
        return try {
            // `use` extension is common for Closeable, ensure PlatformInputStream supports it or handle manually
            val decryptingStream = streamingAead.newDecryptingStream(input, associatedData)
            try {
                delegate.readFrom(decryptingStream)
            } finally {
                decryptingStream.close() // Manual close if `use` is not available/applicable
            }
        } catch (e: Exception) { // Catch generic Exception, can be refined
             // The IOException.toFriendlyException() logic needs to be revisited.
             // isProbablyEncryptedWithAeadException might rely on JVM specific exceptions.
            throw CommonCorruptionException("Failed to read with StreamingAead", e)
        }
    }

    // toFriendlyException logic removed for now, needs KMP adaptation if retained.

    override suspend fun writeTo(t: T, output: PlatformOutputStream) {
        val encryptingStream = streamingAead.newEncryptingStream(output, associatedData)
        try {
            delegate.writeTo(t, encryptingStream)
        } finally {
            encryptingStream.close() // Ensure stream is closed
        }
    }
}

/**
 * Adds encryption to [this] serializer using the given [ExpectedStreamingAead] and [associatedData].
 */
public fun <T> PlatformSerializer<T>.encrypted(
    streamingAead: ExpectedStreamingAead,
    associatedData: ByteArray = byteArrayOf(),
): EncryptingSerializer<T> = StreamingAeadEncryptingSerializer(streamingAead, associatedData, delegate = this)


// --- Helper ByteArrayPlatformOutputStream and platformWriteBytes (needs expect/actual) ---
// These are temporary solutions for the AeadEncryptingSerializer.writeTo method.
// A more robust solution would involve kotlinx-io or proper expect/actual for stream utilities.

expect class ByteArrayPlatformOutputStream() : PlatformOutputStream {
    fun toByteArray(): ByteArray
}

expect fun PlatformOutputStream.platformWriteBytes(bytes: ByteArray)

// Add actual implementations for these in androidMain and iosMain
// androidMain: ByteArrayPlatformOutputStream can wrap java.io.ByteArrayOutputStream
//              platformWriteBytes can use OutputStream.write(bytes)
// iosMain:     ByteArrayPlatformOutputStream can use NSMutableData or similar
//              platformWriteBytes can use NSOutputStream.write(...)

// close() method for PlatformInputStream/PlatformOutputStream needs to be defined in their expect class
// if not already present via typealias to a closeable type.
// For typealiased streams (like java.io.InputStream), close() is available
// via the typealias if the actual type has it (e.g. java.io.Closeable).
// For expect classes, `close()` has been added to their definition in PlatformStreams.kt.

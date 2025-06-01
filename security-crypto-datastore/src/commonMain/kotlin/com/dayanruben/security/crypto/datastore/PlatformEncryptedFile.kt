package com.dayanruben.security.crypto.datastore

import com.dayanruben.datastore.encrypted.ExpectedAead // From encrypted-datastore module
import com.dayanruben.datastore.encrypted.ExpectedStreamingAead // From encrypted-datastore module
import com.dayanruben.datastore.encrypted.PlatformInputStream // From encrypted-datastore module
import com.dayanruben.datastore.encrypted.PlatformOutputStream // From encrypted-datastore module

/**
 * Represents a platform-specific encrypted file.
 * Provides methods to access its content via streams and to get AEAD instances.
 */
expect class PlatformEncryptedFile {
    val file: PlatformFile // The underlying (unencrypted) PlatformFile reference, if applicable

    fun openInputStream(): PlatformInputStream
    fun openOutputStream(): PlatformOutputStream

    // How to provide AEAD instances is tricky.
    // AndroidX EncryptedFile creates and manages its own Tink Aead/StreamingAead instances.
    // For KMP, we might need to:
    // 1. Have methods here that return ExpectedAead/ExpectedStreamingAead. (REMOVED - AEADs will be created directly from master keys)
    //    This implies PlatformEncryptedFile itself might need the PlatformMasterKey or context.
    // 2. Or, the factory/builder for PlatformEncryptedFile takes the key, and AEADs are created
    //    by the user by passing this key to actual crypto implementations.
    // For now, let's assume this class can provide them, similar to AndroidX.
    // fun createOrRetrieveExpectedAead(): ExpectedAead // REMOVED
    // fun createOrRetrieveExpectedStreamingAead(): ExpectedStreamingAead // REMOVED
}

/**
 * Builder for creating PlatformEncryptedFile instances.
 */
expect class PlatformEncryptedFileBuilder {
    // Common builder parameters
    fun file(platformFile: PlatformFile): PlatformEncryptedFileBuilder
    fun masterKey(platformMasterKey: PlatformMasterKey): PlatformEncryptedFileBuilder
    fun context(platformContext: PlatformContext): PlatformEncryptedFileBuilder // For Android context

    // Scheme might be an enum or string constants if needed
    // fun fileEncryptionScheme(scheme: String): PlatformEncryptedFileBuilder

    fun build(): PlatformEncryptedFile
}

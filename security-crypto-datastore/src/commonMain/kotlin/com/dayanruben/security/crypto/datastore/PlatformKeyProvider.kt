package com.dayanruben.security.crypto.datastore

/**
 * Opaque representation of a platform-specific master key used for encryption.
 */
expect class PlatformMasterKey

/**
 * Defines standard key generation parameter specifications.
 * These will map to platform-specific key generation parameters.
 */
expect enum class PlatformKeyGenParameterSpec {
    AES256_GCM // Example: For AES256-GCM
    // Add other common specs if needed
}

/**
 * Gets or creates a PlatformMasterKey.
 *
 * @param alias A unique alias for the key.
 * @param spec The key generation parameters.
 * @param context Optional platform context, may be needed on some platforms (e.g., Android).
 * @return The PlatformMasterKey.
 * @throws GeneralSecurityException if key creation or retrieval fails.
 */
expect fun getOrCreatePlatformMasterKey(
    alias: String,
    spec: PlatformKeyGenParameterSpec,
    context: PlatformContext? = null // Added optional context
): PlatformMasterKey

/**
 * Creates an ExpectedAead instance from a PlatformMasterKey.
 *
 * @param masterKey The PlatformMasterKey to use for deriving the AEAD.
 * @param context Optional platform context.
 * @return An ExpectedAead instance.
 * @throws GeneralSecurityException if AEAD creation fails.
 */
expect fun createAeadFromPlatformKey(
    masterKey: PlatformMasterKey,
    context: PlatformContext? = null
): com.dayanruben.datastore.encrypted.ExpectedAead

/**
 * Creates an ExpectedStreamingAead instance from a PlatformMasterKey.
 *
 * @param masterKey The PlatformMasterKey to use for deriving the Streaming AEAD.
 * @param context Optional platform context.
 * @return An ExpectedStreamingAead instance.
 * @throws GeneralSecurityException if Streaming AEAD creation fails.
 */
expect fun createStreamingAeadFromPlatformKey(
    masterKey: PlatformMasterKey,
    context: PlatformContext? = null
): com.dayanruben.datastore.encrypted.ExpectedStreamingAead

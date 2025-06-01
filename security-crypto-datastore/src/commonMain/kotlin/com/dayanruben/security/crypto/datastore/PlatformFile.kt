package com.dayanruben.security.crypto.datastore

import okio.Path // Using Okio Path for a more robust file path representation

/**
 * Represents a platform-specific file.
 * This abstraction allows common code to reason about files.
 */
expect interface PlatformFile {
    val path: Path // Using Okio Path
    val name: String
    fun exists(): Boolean
    fun delete(): Boolean
    fun parent(): PlatformFile? // Optional: get parent directory
    fun mkdirs(): Boolean // Optional: create directories
    fun isDirectory(): Boolean // Optional
}

/**
 * Creates a PlatformFile instance from a given path string.
 *
 * @param path The platform-specific path string.
 * @return A PlatformFile instance.
 */
expect fun createPlatformFile(path: Path): PlatformFile

// Extension to convert String to PlatformFile easily
fun String.toPlatformFile(): PlatformFile = createPlatformFile(okio.Path.Companion.get(this))

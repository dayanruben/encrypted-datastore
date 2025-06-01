package com.dayanruben.datastore.encrypted.preferences.fakes

import com.dayanruben.datastore.encrypted.PlatformInputStream
import com.dayanruben.datastore.encrypted.PlatformOutputStream
import com.dayanruben.datastore.encrypted.PlatformSerializer
import com.dayanruben.datastore.encrypted.platformReadBytes // commonTest actual from encrypted-datastore's fakes
import com.dayanruben.datastore.encrypted.preferences.PlatformPreferenceKey
import com.dayanruben.datastore.encrypted.preferences.PlatformPreferences
import com.dayanruben.datastore.encrypted.preferences.PlatformPreferencesSerializer

// --- FakePlatformPreferences for commonTest ---
actual class CommonTestPlatformPreferences : PlatformPreferences() {
    val preferencesMap: MutableMap<String, Any> = mutableMapOf()

    // These methods need to be implemented based on the `expect class PlatformPreferences` definition
    // If `expect class PlatformPreferences` has no methods, these are not strictly needed here
    // but are useful for the fake's operation.
    // The `expect class PlatformPreferences` in `ExpectedPreferences.kt` was empty,
    // relying on `actual typealias` for Android/iOS to provide methods.
    // For commonTest, we need to define how it behaves.

    fun <T> get(key: CommonTestPlatformPreferenceKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return preferencesMap[key.name] as? T
    }

    fun contains(key: CommonTestPlatformPreferenceKey<*>): Boolean {
        return preferencesMap.containsKey(key.name)
    }

    fun asMap(): Map<CommonTestPlatformPreferenceKey<*>, Any> {
        // This is tricky because original keys have type info.
        // For a fake, returning Map<String, Any> might be simpler if used only for inspection.
        // Or, reconstruct CommonTestPlatformPreferenceKey, but without original type for T, it's problematic.
        // Let's assume for testing, direct map access is fine.
        return preferencesMap.mapKeys { CommonTestPlatformPreferenceKey<Any>(it.key) } // This is not ideal for type safety
    }

    fun <T> put(key: CommonTestPlatformPreferenceKey<T>, value: T) {
        preferencesMap[key.name] = value as Any
    }

    fun <T> remove(key: CommonTestPlatformPreferenceKey<T>) {
        preferencesMap.remove(key.name)
    }

    fun clear() {
        preferencesMap.clear()
    }
}

// --- FakePlatformPreferenceKey for commonTest ---
actual class CommonTestPlatformPreferenceKey<T> actual constructor(actual override val name: String) : PlatformPreferenceKey<T>(name) {
    // Needs to match `expect class PlatformPreferenceKey<T>(val name: String)`
    // The `actual override val name: String` correctly implements the expect.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false // Ensure same class for commonTest fake
        other as CommonTestPlatformPreferenceKey<*>
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()
}

// --- FakePlatformPreferencesSerializer for commonTest ---
actual class CommonTestPlatformPreferencesSerializer : PlatformPreferencesSerializer() {
    // This serializer will work with CommonTestPlatformPreferences.
    // It will do a very simple fake serialization: store key-value pairs as a string.
    // E.g., "key1=value1\nkey2=value2"
    // This is extremely basic and only for testing the encryption layer, not serialization robustness.

    actual override val defaultValue: PlatformPreferences by lazy { CommonTestPlatformPreferences() }

    actual override suspend fun readFrom(input: PlatformInputStream): PlatformPreferences {
        val prefs = CommonTestPlatformPreferences()
        try {
            val content = input.platformReadBytes().decodeToString()
            if (content.isNotBlank()) {
                content.split('\n').forEach { line ->
                    val parts = line.split('=', limit = 2)
                    if (parts.size == 2) {
                        // This fake serializer doesn't know the type T of the key.
                        // It will store everything as String.
                        // This is a limitation of this simple fake for preferences.
                        // For testing encryption, it's mostly about whether data passes through.
                        prefs.preferencesMap[parts[0]] = parts[1]
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore read errors for this simple fake, return default (empty)
            println("Fake serializer read error: ${e.message}")
        } finally {
            input.close()
        }
        return prefs
    }

    actual override suspend fun writeTo(t: PlatformPreferences, output: PlatformOutputStream) {
        val commonTestPrefs = t as? CommonTestPlatformPreferences
            ?: throw IllegalArgumentException("FakePlatformPreferencesSerializer can only handle CommonTestPlatformPreferences")

        val sb = StringBuilder()
        commonTestPrefs.preferencesMap.forEach { (key, value) ->
            sb.append(key).append('=').append(value.toString()).append('\n')
        }
        try {
            output.platformWriteBytes(sb.toString().encodeToByteArray())
        } finally {
            output.close()
        }
    }
}

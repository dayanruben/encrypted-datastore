package com.dayanruben.datastore.encrypted.kmp

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EncryptedPreferencesDataStoreTest {
    @Test
    fun persistsAllSupportedPreferenceTypes() = runTest {
        withStoreFiles {
            val path = "/settings.preferences_pb".toPath()
            val cipher = aesGcmCipher(ByteArray(32))
            val scope = newScope()
            val store = createEncryptedPreferencesDataStore(cipher, "settings", path, scope, fileSystem)
            assertTrue(store.data.first().asMap().isEmpty())
            store.edit {
                it[booleanPreferencesKey("bool")] = true
                it[intPreferencesKey("int")] = 42
                it[longPreferencesKey("long")] = 9_000_000_000L
                it[floatPreferencesKey("float")] = 1.25f
                it[doublePreferencesKey("double")] = 2.5
                it[stringPreferencesKey("string")] = "Olá 🌍"
                it[stringSetPreferencesKey("set")] = setOf("a", "b")
                it[byteArrayPreferencesKey("bytes")] = byteArrayOf(0, 1, -1)
            }
            scope.coroutineContext.job.cancelAndJoin()
            val values = createEncryptedPreferencesDataStore(cipher, "settings", path, newScope(), fileSystem).data.first()
            assertEquals(true, values[booleanPreferencesKey("bool")])
            assertEquals(42, values[intPreferencesKey("int")])
            assertEquals(9_000_000_000L, values[longPreferencesKey("long")])
            assertEquals(1.25f, values[floatPreferencesKey("float")])
            assertEquals(2.5, values[doublePreferencesKey("double")])
            assertEquals("Olá 🌍", values[stringPreferencesKey("string")])
            assertEquals(setOf("a", "b"), values[stringSetPreferencesKey("set")])
            assertContentEquals(byteArrayOf(0, 1, -1), values[byteArrayPreferencesKey("bytes")])
        }
    }

    @Test
    fun freezesEscapedMutablePreferences() = runTest {
        withStoreFiles {
            val store = createEncryptedPreferencesDataStore(aesGcmCipher(ByteArray(32)), "settings", "/settings.preferences_pb".toPath(), newScope(), fileSystem)
            lateinit var escaped: MutablePreferences
            val key = stringPreferencesKey("value")
            store.edit { escaped = it; it[key] = "saved" }
            assertFailsWith<IllegalStateException> { escaped[key] = "changed outside transaction" }
            assertEquals("saved", store.data.first()[key])
        }
    }

    @Test
    fun requiresThePreferencesFileExtension() = runTest {
        withStoreFiles {
            assertFailsWith<IllegalArgumentException> {
                createEncryptedPreferencesDataStore(aesGcmCipher(ByteArray(32)), "settings", "/settings.pb".toPath(), newScope(), fileSystem)
            }
        }
    }
}

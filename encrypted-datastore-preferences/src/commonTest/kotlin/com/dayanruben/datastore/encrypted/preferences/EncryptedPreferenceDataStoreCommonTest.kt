package com.dayanruben.datastore.encrypted.preferences

import com.dayanruben.datastore.encrypted.preferences.fakes.CommonTestPlatformPreferenceKey
import com.dayanruben.datastore.encrypted.preferences.fakes.CommonTestPlatformPreferences
import com.dayanruben.datastore.encrypted.preferences.fakes.FakeDataStore
import com.dayanruben.datastore.encrypted.preferences.fakes.MinimalFakeExpectedAead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedPreferenceDataStoreCommonTest {

    private val testScope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val fakeAead = MinimalFakeExpectedAead()

    // Helper to create DataStore using the commonTest actual factory
    private fun testEncryptedDataStore(): androidx.datastore.core.DataStore<PlatformPreferences> {
        return createEncryptedPreferencesDataStore(
            producePath = { "fake/path/test.fake_prefs".checkPreferencesFileExtension() },
            aead = fakeAead,
            scope = testScope
        )
    }

    @Test
    fun `can store and retrieve a String preference`() = runBlocking {
        val dataStore = testEncryptedDataStore()
        val stringKey = CommonTestPlatformPreferenceKey<String>("myStringKey")
        val testValue = "Hello KMP!"

        dataStore.updateData { prefs ->
            // The actual prefs instance will be CommonTestPlatformPreferences
            (prefs as CommonTestPlatformPreferences).put(stringKey, testValue)
            prefs
        }

        val retrievedPrefs = dataStore.data.first()
        // Again, cast to access the fake's methods for verification
        val actualValue = (retrievedPrefs as CommonTestPlatformPreferences).get(stringKey)

        assertEquals(testValue, actualValue)
        assertTrue(fakeAead.encryptCalledCount > 0, "AEAD encrypt should have been called")
        assertTrue(fakeAead.decryptCalledCount > 0, "AEAD decrypt should have been called")
    }

    @Test
    fun `can store and retrieve an Int preference`() = runBlocking {
        val dataStore = testEncryptedDataStore()
        val intKey = CommonTestPlatformPreferenceKey<Int>("myIntKey")
        val testValue = 12345

        dataStore.updateData { prefs ->
            (prefs as CommonTestPlatformPreferences).put(intKey, testValue)
            prefs
        }

        val retrievedPrefs = dataStore.data.first()
        val actualValue = (retrievedPrefs as CommonTestPlatformPreferences).get(intKey)

        assertEquals(testValue, actualValue)
        assertTrue(fakeAead.encryptCalledCount > 0)
        assertTrue(fakeAead.decryptCalledCount > 0)
    }

    @Test
    fun `can store and retrieve a Boolean preference`() = runBlocking {
        val dataStore = testEncryptedDataStore()
        val boolKey = CommonTestPlatformPreferenceKey<Boolean>("myBoolKey")
        val testValue = true

        dataStore.updateData { prefs ->
            (prefs as CommonTestPlatformPreferences).put(boolKey, testValue)
            prefs
        }

        val retrievedPrefs = dataStore.data.first()
        val actualValue = (retrievedPrefs as CommonTestPlatformPreferences).get(boolKey)

        assertEquals(testValue, actualValue)
    }

    @Test
    fun `retrieving non_existent key returns null`() = runBlocking {
        val dataStore = testEncryptedDataStore()
        val nonExistentKey = CommonTestPlatformPreferenceKey<String>("nonExistentKey")

        val retrievedPrefs = dataStore.data.first() // Get initial (default) preferences
        val actualValue = (retrievedPrefs as CommonTestPlatformPreferences).get(nonExistentKey)

        assertNull(actualValue)
        // Decrypt would be called once to get the initial empty preferences
        assertTrue(fakeAead.decryptCalledCount > 0)
    }

    @Test
    fun `multiple updates work correctly`() = runBlocking {
        val dataStore = testEncryptedDataStore()
        val keyA = CommonTestPlatformPreferenceKey<String>("keyA")
        val keyB = CommonTestPlatformPreferenceKey<Int>("keyB")

        dataStore.updateData { prefs ->
            (prefs as CommonTestPlatformPreferences).put(keyA, "Value A1")
            prefs.put(keyB, 100) // Using extension on CommonTestPlatformPreferences
            prefs
        }
        dataStore.updateData { prefs ->
            (prefs as CommonTestPlatformPreferences).put(keyA, "Value A2")
            prefs
        }

        val retrievedPrefs = dataStore.data.first() as CommonTestPlatformPreferences
        assertEquals("Value A2", retrievedPrefs.get(keyA))
        assertEquals(100, retrievedPrefs.get(keyB))
    }

    // It would be good to have preference key creation functions like Android's stringPreferencesKey, intPreferencesKey etc.
    // For commonTest, we are using CommonTestPlatformPreferenceKey directly.
    // If those were `expect fun stringPreferencesKey(name: String): PlatformPreferenceKey<String>`,
    // then `actual` implementations would be needed. For now, direct instantiation is fine for tests.
}

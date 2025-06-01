@file:Suppress("FunctionName") // If any expect funs here still use that convention

package com.dayanruben.datastore.encrypted.preferences // Corrected package

// This file will now primarily host common declarations related to Encrypted Preferences DataStore
// if any are needed beyond what's in ExpectedPreferences.kt.

// For example, common constants or helper functions that operate on PlatformPreferences
// or the common DataStore<PlatformPreferences> interface.

// Most of the original content was Android/JVM-specific factory code and has been moved
// to the `actual` implementation in `ActualPreferences.android.kt` or made obsolete
// by the `expect` / `actual` design (e.g. File.checkPreferenceDataStoreFileExtension).

// The deprecated `val PreferencesSerializer` is removed; use `PlatformPreferencesSerializer`.
// The `fun PreferenceDataStore(delegate: DataStore<Preferences>): DataStore<Preferences>`
// was an Android-specific constructor exposure; common code uses `DataStore<PlatformPreferences>`.

// If there are no common declarations left that specifically belong to "EncryptedPreferenceDataStore"
// concept beyond what's in ExpectedPreferences.kt, this file could be removed or merged.
// For now, keeping it as a potential placeholder for future common utilities.

// Example: A common helper that might have existed (illustrative)
// fun <T> PlatformPreferences.getValueFlow(key: PlatformPreferenceKey<T>, dataStore: DataStore<PlatformPreferences>): Flow<T?> {
//     return dataStore.data.map { it[key] }
// }
// This type of helper would remain here. Currently, no such helpers are in the original file.

// Retaining the file suppression for now.
// Ensure this file is in the correct package path if it wasn't already.
// Based on previous steps, it should be:
// encrypted-datastore-preferences/src/commonMain/kotlin/com/dayanruben/datastore/encrypted/preferences/EncryptedPreferenceDataStore.kt
// The package declaration was corrected.

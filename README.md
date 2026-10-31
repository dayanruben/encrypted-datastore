# Encrypted DataStore
[![Kotlin](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fdayanruben%2Fencrypted-datastore%2Frefs%2Fheads%2Fmain%2Fgradle%2Flibs.versions.toml&query=versions.kotlin&prefix=v&logo=kotlin&label=Kotlin)](./gradle/libs.versions.toml)
[![Gradle](https://img.shields.io/badge/Gradle-9.8.2-blue?logo=gradle)](https://gradle.org)
[![Version](https://img.shields.io/maven-central/v/com.dayanruben/encrypted-datastore)][mavenCentral]
[![License](https://img.shields.io/github/license/dayanruben/encrypted-datastore)][license]

Authenticated settings storage for Kotlin Multiplatform, with compatibility APIs for existing Android/JVM encrypted files.

> [!WARNING]
> Special thanks to the original [author](https://github.com/osipxd) for laying the foundation of this [repository](https://github.com/osipxd/encrypted-datastore), your work continues to inspire.
> DataStore 1.3.0 now provides official AEAD encryption through `androidx.datastore:datastore-tink` and `AeadSerializer`.
> Existing users can continue using this library for streaming encryption and legacy encrypted files. Switching from `StreamingAead` to the official AEAD serializer requires an explicit data migration; the ciphertext formats are different.

---

## Kotlin Multiplatform (unreleased)

The new `encrypted-datastore-kmp` module supports Android, JVM, iOS ARM64, iOS ARM64 simulator, and macOS ARM64. It uses native AES-256-GCM providers, a versioned authenticated record, public AndroidX Okio APIs, and explicit platform key creation/opening. Existing modules and ciphertext formats remain compatible.

This module has **not been published**. While developing from this repository, use:

```kotlin
commonMain.dependencies {
    implementation(project(":encrypted-datastore-kmp"))
}
```

Create Preferences storage in shared Kotlin code, supplying a persisted cipher, a private absolute path ending in `.preferences_pb`, and a background scope owned by the application:

```kotlin
import com.dayanruben.datastore.encrypted.kmp.*
import kotlinx.coroutines.CoroutineScope
import okio.Path

fun openSettings(cipher: DataStoreCipher, path: Path, scope: CoroutineScope) =
    createEncryptedPreferencesDataStore(
        cipher = cipher,
        storeId = "application.settings.v1",
        path = path,
        scope = scope,
    )
```

On Android, provision once with `createAndroidKeystoreCipher(alias)` and reopen with `openAndroidKeystoreCipher(alias)`. On Apple platforms, use `createAppleKeychainCipher(service, account)` and `openAppleKeychainCipher(service, account)` with your application's Keychain entitlements. On JVM, `aesGcmCipher(key)` imports a 32-byte key from the application's secure key management. Never recreate keys automatically after an open failure.

The default serialized limit is 1 MiB; `maxPlaintextBytes` accepts `1..16_777_216`. The portable API authenticates a complete bounded record before deserialization. Large streaming files can continue using the existing Tink API below. The new format requires an explicit migration from legacy files; keep the old path and keys until the migrated file has been reopened successfully.

See [KMP usage and security](docs/kmp-security.md), [validation and benchmark evidence](docs/kmp-validation.md), and the [implementation plan](docs/superpowers/plans/2026-10-30-kotlin-multiplatform.md). Apple binary consumers should apply the [provider's Swift linker configuration](https://whyoleg.github.io/cryptography-kotlin/getting-started/troubleshooting/xcode-compatibility/) when Xcode uses a nonstandard path.

## Installation

The following coordinates are the released Android/JVM compatibility APIs.

Add the dependency:

```kotlin
repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.dayanruben:security-crypto-datastore:1.3.0-0.10")
    // Or, if you want to use Preferences DataStore:
    implementation("com.dayanruben:security-crypto-datastore-preferences:1.3.0-0.10")
}
```

> **Android requirements:** minSdk 24; compileSdk 37 or newer.

> **Dependencies:**
> - `security-crypto` [1.1.0](https://developer.android.com/jetpack/androidx/releases/security#1.1.0)
> - `datastore` [1.3.0](https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0)
> - `tink` [1.24.0](https://github.com/tink-crypto/tink-java/releases/tag/v1.24.0)

> [!NOTE]
> Ensure that the version of this library aligns with the DataStore library version used in your project.

## Usage

To create an encrypted DataStore, simply replace the `dataStore` method with `encryptedDataStore` when setting up your delegate:

```kotlin
// At the top level of your Kotlin file:
val Context.settingsDataStore: DataStore<Settings> by encryptedDataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer
)
```

<details>
<summary>Or, if you want full control over <code>EncryptedFile</code> creation</summary>

```kotlin
val settingsDataStore: DataStore<Settings> = DataStoreFactory.createEncrypted(SettingsSerializer) {
    EncryptedFile.Builder(
        context.dataStoreFile("settings.pb"),
        context,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()
}
```
</details>

Similarly, you can create Preferences DataStore:

```kotlin
// At the top level of your Kotlin file:
val Context.dataStore by encryptedPreferencesDataStore(name = "settings")
```

<details>
<summary>Or, if you want full control over <code>EncryptedFile</code> creation</summary>

```kotlin
val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createEncrypted {
    EncryptedFile.Builder(
        context.preferencesDataStoreFile("settings"),
        context,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()
}
```
</details>

Once your encrypted DataStore is configured, you can use it in the same manner as a regular DataStore.
For usage guides and examples, refer to the [DataStore documentation](https://developer.android.com/topic/libraries/architecture/datastore).

## Migration

### Migrating from DataStoreFactory to delegate usage

If you are starting with the following code:

```kotlin
val dataStore = DataStoreFactory.createEncrypted(serializer) {
    EncryptedFile.Builder(
        context.dataStoreFile("filename"),
        context,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()
}
```

To simplify the creation of DataStore using a delegate, follow these steps:

1. Move the field to the top level of your Kotlin file and convert it into an extension on `Context`.
2. Replace `DataStoreFactory.createEncrypted` with `encryptedDataStore`.

```kotlin
val Context.dataStore by encryptedDataStore(
    fileName = "filename", // Keep file the same
    serializer = serializer,
)
```

> [!NOTE]
> This only will be interchangeable if you have used `context.dataStoreFile(...)` to create the datastore file.
> If you have custom logic for master key creation, ensure to pass the created master key as the `masterKey` parameter to the delegate.

### Migrating from `encrypted-datastore` to `security-crypto-datastore`

Change the dependency in build script:

```diff
 dependencies {
-    implementation("com.dayanruben:encrypted-datastore:...")
+    implementation("com.dayanruben:security-crypto-datastore:...")
 }
```

New library uses `StreamingAead` instead of `Aead` under the hood, so to not lose the previously encrypted data you should specify `fallbackAead`:

```kotlin
// This AEAD was used to encrypt DataStore previously, we will use it as fallback
val aead = AndroidKeysetManager.Builder()
    .withSharedPref(context, "master_keyset", "master_key_preference")
    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
    .withMasterKeyUri("android-keystore://master_key")
    .build()
    .keysetHandle
    .getPrimitive(Aead::class.java)
```

The old code to create DataStore was looking like this:

```kotlin
val dataStore = DataStoreFactory.create(serializer.encrypted(aead)) {
    context.dataStoreFile("filename")
}
```

The new code will look like this:

```kotlin
// At the top level of your Kotlin file:
val Context.dataStore by encryptedDataStore(
    fileName = "filename", // Keep file the same
    serializer = serializer,
    encryptionOptions = {
        // Specify fallback Aead to make it possible to decrypt data encrypted with it
        fallbackAead = aead
    }
)
```

<details>
<summary>Or, if you want full control over <code>EncryptedFile</code> creation</summary>

```kotlin
val dataStore = DataStoreFactory.createEncrypted(
    serializer = serializer,
    encryptionOptions = { fallbackAead = aead }
) {
    EncryptedFile.Builder(
        context.dataStoreFile("filename"), // Keep file the same
        context,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()
}
```
</details>

### Thanks

- Artem Kulakov ([Fi5t]), for his [example][secured-datastore] of DataStore encryption.
- Gods of Kotlin, for posibility to [hack] `internal` visibility modifier 

## License

[MIT][license]


[mavenCentral]: https://search.maven.org/artifact/com.dayanruben/encrypted-datastore
[license]: LICENSE

[tink]: https://github.com/tink-crypto/tink-java
[secured-datastore]: https://github.com/Fi5t/secured-datastore
[fi5t]: https://github.com/Fi5t
[hack]: encrypted-datastore-preferences/src/main/java/com/dayanruben/datastore/encrypted/PreferenceDataStoreHack.java

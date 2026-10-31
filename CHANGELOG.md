## [Unreleased]

### Multiplatform

- Add `encrypted-datastore-kmp` for Android, JVM, iOS ARM64/simulator, and macOS ARM64, preserving existing coordinates and ciphertext formats.
- Add authenticated AES-256-GCM Okio serialization, stable store identity binding, bounded records, and typed failures that do not trigger corruption replacement.
- Add typed and Preferences factories using public AndroidX APIs and caller-owned coroutine scopes.
- Add explicit Android Keystore and Apple device-only Keychain creation/opening, plus JVM key import.
- Add JVM/native/Android integration suites, Android and Native API gates, an opt-in benchmark, native CI, and Apple-hosted complete KMP publication.

### Fixes

- Decrypt legacy AEAD streams correctly when `InputStream.available()` reports zero before EOF.
- Snapshot streaming associated data so caller mutation cannot change an existing serializer's authentication context.

The new artifact is unreleased; the existing published `1.3.0-0.10` coordinates have not changed.

## [1.3.0-0.10] – 2026.10.26

### Dependencies

- Migrate to AndroidX DataStore 1.3.0; retain the existing AEAD and StreamingAead formats and public APIs.
- Kotlin 2.4.21, Tink 1.24.0, Gradle 9.8.2, Android Gradle Plugin 9.4.1, and JUnit 6.1.3.
- Maven Publish plugin 0.37.0 and binary compatibility validator 0.18.2.
- Update sample dependencies to their latest stable releases.

### Build and publication

- Resolve Preferences serializer friend paths lazily and update Gradle source-set configuration.
- Update GitHub Actions, validate the sample APK in CI, and enable Maven Central publication for this fork.

### Migration

- Android now requires API 24 or newer, matching DataStore 1.3.0. Compile against SDK 37 for the updated AndroidX dependencies.
- DataStore 1.3.0 includes official `datastore-tink` AEAD encryption. It is not a drop-in replacement for this library's StreamingAead files; keep the existing serializer or migrate the data explicitly.

## Historical development notes

### Publish `encrypted-datastore` as KMP library

As `security-crypto-datastore` artifacts are going to be deprecated,
`encrypted-datastore` artifacts are published as a KMP library.
It has only two targets yet: JVM and Android.
This change was made to use the same dependency for JVM and Android projects
and to prepare the library for possible support for iOS target in the future.

### Dependencies

- Kotlin `1.9.23` → `2.0.20`
- [Tink](https://github.com/tink-crypto/tink-java/releases/tag/v1.15.0) `1.13.0` → `1.15.0`

### Housekeeping

- Compile against Android SDK 36
- Update dependencies in sample project
- Drop usage of gradle-infrastructure plugins
- Use `-Xfriendly-paths` compiler option to access internal entities from the androidx.datastore library, delete `PreferenceDataStoreHack`

## [1.1.1-beta03] – 2024.05.13

### Added

- Enforce version alignment between the library and DataStore using BOM (#45)

### Housekeeping

- Publication migrated to [com.vanniktech:gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)

## [1.1.1-beta02] – 2024.05.07

### Fixed

- Fixed Preference DataStore saving (#38)
- Test fixtures are excluded from publication (#32)

### Housekeeping

- Added sample application

## [1.1.1-beta01] – 2024.05.04

### DataStore 1.1.0+

> [!NOTE]
> Ensure that the version of this library aligns with the DataStore library version used in your project.
> This version should only be used with [`datastore:1.1.1`](https://developer.android.com/jetpack/androidx/releases/datastore#1.1.1), as it depends on new APIs introduced in that release.

Starting with version 1.1.0, DataStore has become a multiplatform library.
However, encrypted datastore is currently limited to JVM and Android targets due to dependencies on JVM-specific libraries like Tink and `security-crypto`.
I am actively exploring ways to add support for more targets to the library.
The research will take some time, so I've released a beta version compatible with DataStore 1.1.0 for developers not requiring multiplatform targets support.

### Changes

- **Potentially breaking change:** The field `com.dayanruben.datastore.encrypted.PreferencesSerializer` has been hidden from the public API.
  Instead, directly use the object `PreferencesSerializer`, which is now publicly available starting with the `datastore:1.1.0` release.
- Added the `@RestrictTo(LIBRARY_GROUP)` annotation to public members that are intended for internal use only, ensuring they are not used externally.

### Housekeeping

- Added a binary compatibility validator to prevent unintentional breaks in binary compatibility

## [1.0.0] – 2024.04.17

### Delegates to create encrypted DataStores

Delegates `encryptedDataStore` and `encryptedPreferencesDataStore` were added to simplify DataStore creation.
If you have the following code:

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

You can simplify it using delegate for DataStore creation.

```kotlin
// 1. Move the field to top level of you Kotlin file and turn it to an extension on Context
// 2. Replace `DataStoreFactory.createEncrypted` with `encryptedDataStore`
val Context.dataStore by encryptedDataStore(
    fileName = "filename", // Keep file the same
    serializer = serializer,
)
```

> [!NOTE]
> This only will be interchangeable if you used `context.dataStoreFile(...)` to create datastore file.
> In case you have custom logic for master key creation, pass the created master key as a parameter `masterKey` to the delegate.

### Dependencies

- Target JVM `1.8` → `11`
- Kotlin `1.7.21` → `1.9.23`
- [Tink](https://github.com/tink-crypto/tink-java/releases) `1.7.0` → `1.13.0`

### Housekeeping

- Update Gradle to 8.7
- Update AGP to 8.3.2
- Move `PreferenceDataStoreHack.java` into `encrypted-datastore-preferences` module

## [1.0.0-beta01] – 2023.02.26

### Fixed

- Fixed decryption fallback from StreamingAead to Aead (#15)
- Change min SDK 21 → 23 to align it with security-crypto 1.0.0

### Housekeeping

- Update Gradle to 7.6.1

## [1.0.0-alpha04] – 2022.12.19

### Fixed

- Fixed the case when data can not be read if output stream was not closed in serializer (#10)
- Added default value for parameter `encryptionOptions` in `PreferenceDataStoreFactory.createEncrypted` (#12)

## [1.0.0-alpha03] – 2022.11.18

#### More high-level library `security-crypto-datastore`

New library provides more simple and less error-prone API to create encrypted DataStores.
All Tink-related stuff hidden from you in `security-crypto` library, and all you should do is wrap `File` with `EncryptedFile`:

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

Or even simpler, if you use `security-crypto-ktx:1.1.0`:

```kotlin
val dataStore = DataStoreFactory.createEncrypted(serializer) {
    EncryptedFile(
        context = context,
        file = context.dataStoreFile("filename"),
        masterKey = MasterKey(context)
    )
}
```

See the [Migration guide](README.md#migration).

#### Streaming serializer

Introduced new extension-function `Serializer.encrypted(StreamingAead)` to encrypt DataStore in streaming manner.
Old extension-function with `Aead` is not planned to be removed yet, but for all new code it is recommended to use the new function or migrate to the `security-crypto-datastore`.

> **ATTENTION!**
> You can not use `StreamingAead` to decrypt data encrypted with `Aead`,
> so you can not just replace `Aead` with `StreamingAead` without migration.
> To not lose your previously encrypted data, you have three options:
> 1. **Migration** – add fallback for `StreamingAead` using function `StreamingAead.withDecryptionFallback(Aead)`
> 2. **Do nothing** – continue to use `Aead`
> 3. **Destructive migration** – specify `CorruptionHandler` to replace old content with something else

#### New module `encrypted-datastore-preferences`

:warning: **Breaking change:** 

All stuff related to Preference DataStore was moved to `com.dayanruben:encrypted-datastore-preferences`.
To continue use it, change the dependency module in your build script:

```diff
-implmentation("com.dayanruben:encrypted-datastore:...")
+implmentation("com.dayanruben:encrypted-datastore-preferences:...")
```

### Fixed

- Fixed crash when DataStore can not be decrypted (#1)

### Dependencies

- Kotlin `1.5.30` → `1.7.21`
- [Tink](https://github.com/google/tink/releases/tag/v1.7.0) `1.6.1` → `1.7.0`

### Housekeeping

- Gradle `7.2` → `7.5.1`
- gradle-infrastructure `0.12.1` → `0.17`
- Migrate dependencies to version catalogs
- 

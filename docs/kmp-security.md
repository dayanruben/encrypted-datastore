# Kotlin Multiplatform usage and security

`encrypted-datastore-kmp` is an unreleased, independent artifact. Legacy `encrypted-datastore`, Preferences, and security-crypto modules retain their existing APIs and formats. The new module targets Android (minSdk 24, compileSdk 37), JVM (toolchain/bytecode 21), iOS ARM64, iOS ARM64 simulator, and macOS ARM64. Browser storage, other native targets, and Swift package distribution are not included.

## Shared typed and Preferences storage

All new API declarations use `com.dayanruben.datastore.encrypted.kmp`. An application supplies an immutable `OkioSerializer<T>`, a persisted cipher, a stable store identity, an absolute private path, and a background coroutine scope. The default file system resolves to the platform system file system; tests and specialized applications can supply another `FileSystem`.

```kotlin
import androidx.datastore.core.okio.OkioSerializer
import com.dayanruben.datastore.encrypted.kmp.*
import kotlinx.coroutines.CoroutineScope
import okio.BufferedSink
import okio.BufferedSource
import okio.Path

object SettingsSerializer : OkioSerializer<String> {
    override val defaultValue = "system"
    override suspend fun readFrom(source: BufferedSource) = source.readUtf8()
    override suspend fun writeTo(t: String, sink: BufferedSink) { sink.writeUtf8(t) }
}

fun typedSettings(cipher: DataStoreCipher, path: Path, scope: CoroutineScope) =
    createEncryptedDataStore(
        serializer = SettingsSerializer,
        cipher = cipher,
        storeId = "application.typed-settings.v1",
        path = path,
        scope = scope,
    )

fun preferences(cipher: DataStoreCipher, path: Path, scope: CoroutineScope) =
    createEncryptedPreferencesDataStore(
        cipher = cipher,
        storeId = "application.preferences.v1",
        path = path, // Must end in .preferences_pb.
        scope = scope,
        maxPlaintextBytes = 1_048_576,
    )
```

Use one active DataStore per file, in one process. Keep the scope alive for the store's lifetime; cancel and join its Job before opening another instance. Paths are normalized and must be absolute. Do not use multiple symlink/hardlink aliases for the same store. Upstream AndroidX owns the actor, migration ordering, cached values, file synchronization, and atomic replacement. Preferences uses its public factory so values cannot be mutated outside `edit`/`updateData` transactions. Migrations are explicit `List<DataMigration<T>>` arguments, with AndroidX's retry/idempotency contract.

For custom storage, wrap a public serializer directly with `serializer.encrypted(cipher, storeId, maxPlaintextBytes)` and supply it to `OkioStorage`. No AndroidX internal declarations or friend-path tricks are used in the new module.

## Platform keys

Provisioning and opening are separate operations. Call a create function only during explicit first-time provisioning, then persist the alias/identity in application configuration and reopen it on later launches. An open failure must not lead automatically to key generation. Creation rejects duplicates; neither platform adapter deletes, rotates, or repairs keys. Cancellation can arrive after the OS persists a newly created key, so resolve that state by opening the same identity on retry.

| Platform | Provision | Reopen | Storage contract |
| --- | --- | --- | --- |
| Android | `createAndroidKeystoreCipher(alias)` | `openAndroidKeystoreCipher(alias)` | Nonexportable AES-256-GCM key; randomized encryption required; existing size/purposes/modes/padding validated |
| iOS/macOS | `createAppleKeychainCipher(service, account)` | `openAppleKeychainCipher(service, account)` | Generic-password item in data-protection Keychain; WhenUnlockedThisDeviceOnly; synchronization disabled |
| JVM | Provision through your OS vault/KMS/secret manager | `aesGcmCipher(keyFromSecureStorage)` | Imports exactly 32 bytes; this library never persists raw JVM keys |

Android provisioning is serialized within the process. If multiple processes can provision the same alias, coordinate that lifecycle outside this API. Keystore operations use `Dispatchers.IO`; hardware/StrongBox and biometric requirements are not implied.

Apple applications need valid Keychain entitlements. Keychain lock, unavailable service, missing entitlement, duplicate item, and missing key return `KeyStorageException`; its nullable `status` contains the OSStatus when one exists. The adapter requests failure rather than an authentication prompt while opening. It never falls back to the legacy macOS Keychain. The native AES key lives in process memory after import; it is not Secure Enclave-backed AES. Configure these platform helpers inside a Kotlin application facade if exporting a framework to Swift; a direct Swift SDK surface is not part of this module's distribution contract.

Raw keys and public cipher inputs remain caller-owned. Import copies the key without modifying the caller's array. Keep raw secrets out of source code, preferences, adjacent files, logs, and backups. Reuse a cipher wrapper per store lifetime; operation objects are not shared between calls. Temporary owned arrays are cleared best-effort, but runtime/provider copies and pooled buffers prevent a guaranteed zeroization claim. A custom `DataStoreCipher` implementation is trusted security code and must honor the documented AES-256-GCM layout, concurrent safety, input ownership, and full authentication contract.

## Format and resource limits

Version 1 is `45 44 53 4b | 01 | 01 | nonce(12) | ciphertext | tag(16)`. The six-byte header identifies this format/version/AES-256-GCM algorithm. Its 34-byte overhead applies even to an empty serialized value. Each write generates a fresh cryptographically random nonce through the platform provider.

AAD is `header | UTF8(storeId).size as UInt32 big endian | UTF8(storeId)`. Store identities must be nonblank and at most 1024 UTF-8 bytes. Use a stable logical identity scoped to the appropriate store/account. Reusing one key and one identity across unrelated stores permits swapping their valid records. Changing the identity prevents reading existing ciphertext, including when renaming a file; moving a file while preserving its logical identity works.

Serialized plaintext defaults to at most 1,048,576 bytes. A custom `maxPlaintextBytes` must be in `1..16_777_216`. The reader consumes at most that limit plus 34 record bytes plus one sentinel. Okio can prefetch one additional 8192-byte segment into the caller's buffered source. Serialization uses a bounded sink, so an unbounded serializer cannot grow this adapter's memory without limit. A complete bounded ciphertext is authenticated before the delegate can read even its first plaintext byte. The adapter does not close caller-owned sources/sinks.

This design holds a complete settings record in memory and makes copies during provider/Okio conversion. It is intended for small settings; retain the legacy Tink streaming API for large files. The benchmark reports allocation volume and latency rather than claiming streaming memory usage or device performance.

## Failure and recovery

Only a missing file returns the serializer's default. An existing zero-byte, truncated, plaintext, wrong-version, altered, wrong-key, wrong-identity, or oversized record fails. Neither convenience factory installs a corruption handler.

| Failure | Meaning and effect |
| --- | --- |
| `AuthenticationException` | Complete ciphertext/context could not be authenticated; no delegate plaintext is released |
| `UnsupportedFormatException` | Truncated or unsupported format/header; no format guessing or fallback |
| `PayloadTooLargeException` | Configured serialized limit exceeded; `maxPlaintextBytes` identifies the bound |
| `KeyStorageException` | Persisted key cannot be created/opened; inspect OS status/cause without recreating it |
| Other `EncryptionException` | Crypto initialization/encryption failed; preserve the existing data |
| Filesystem IO/cancellation | Original exception type propagates; upstream storage owns transaction cleanup |

Encryption exceptions inherit `okio.IOException`, deliberately not AndroidX `CorruptionException`. Delegate parse errors occur only after authentication and retain the delegate's own type. Do not translate encryption failures into default preferences or automatic destructive recovery. Failed writes and cancelled transforms preserve the previous committed record; the test suite injects serialization and atomic-move failures and reopens the store to verify this.

## Migration, rotation, and backup

The new format is not the old Tink AEAD or StreamingAead format, nor AndroidX `datastore-tink` ciphertext. Do not point a new serializer at an existing legacy path expecting an implicit migration. An application-controlled migration should stop writers, retain the old file/key, read and validate using the old serializer, write to a new path with the new key/identity, cancel/join and reopen that new store, validate the value, then commit the application's selection of the new store. Retain a recoverable old state until that selection is durable. Never let old and new stores write the same path concurrently.

For key rotation, keep the old key until all rewritten ciphertext has been reopened and validated. There is no automatic key-rotation engine or fallback key chain. Missing keys are a recovery decision for the application, not a signal to create replacements.

Apple device-only keys and Android Keystore keys are not portable ciphertext-backup keys. Exclude device-bound ciphertext from automatic restore unless the application has a deliberate key-recovery/reprovisioning policy. Never put its raw encryption key beside a backup. Ciphertext is portable between the supported providers only when the same key, store identity, and format are supplied explicitly.

## Threat model and validation boundary

The API protects stored plaintext against file disclosure and detects record/context modification. It exposes record length and ordinary filesystem metadata. It does not prevent deletion, replay of an older valid record, a compromised application process/device, or an attacker who has the key. Applications requiring rollback resistance need an external trusted version policy.

[Validation evidence](kmp-validation.md) separates actual JVM/macOS/simulator/emulator execution, physical-target compilation, and external device/signing acceptance. Signed macOS application Keychain acceptance, locked-device behavior, hardware backing, and production backup restoration require their respective application/device environments.

The format and provider choices are documented against [AndroidX's public KMP storage APIs](https://developer.android.com/kotlin/multiplatform/datastore), [cryptography-kotlin AEAD](https://whyoleg.github.io/cryptography-kotlin/primitives/operations/aead/), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), and [Apple device-only Keychain accessibility](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlockedthisdeviceonly).

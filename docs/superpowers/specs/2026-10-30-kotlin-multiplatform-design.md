# Reliable encrypted DataStore for Kotlin Multiplatform

## Intent and scope

Add a production-oriented Kotlin Multiplatform API for encrypted typed and Preferences DataStore. The existing Android/JVM APIs, Tink streaming ciphertext, test fixtures, and Maven coordinates continue to work. The implementation lives in a new `encrypted-datastore-kmp` module so existing consumers do not acquire a different cryptography backend or file format implicitly.

The user requested a plan, then explicitly requested execution immediately after writing it, all necessary validation, and atomic commits. Platform selection is an implementation assumption: Android, JVM, `iosArm64`, `iosSimulatorArm64`, and `macosArm64`. These are supported by the pinned AndroidX native artifacts. Browser storage, Linux native, Windows native, Intel Apple targets, Swift package distribution, and a published release are outside this change. JVM remains usable on desktop/server operating systems.

## Approaches

1. Port Tink's Java streaming implementation to Kotlin/Native: rejected; maintaining cryptographic primitives and matching an existing streaming protocol creates unnecessary security and maintenance risk.
2. Replace every existing serializer with a common AEAD serializer: rejected; this silently changes legacy ciphertext and dependency behavior.
3. Add an independent common Okio API with a vetted native cryptographic provider and keep the legacy modules: selected by the implementer. This gives a bounded, authenticated record suitable for settings and leaves large streaming files with the existing API. The tradeoff is an explicitly different format and an additional coordinate.

## Fixed constraints

- Retain Kotlin `2.4.21`, Gradle `9.8.2`, AGP `9.4.1`, AndroidX DataStore `1.3.0`, Android minSdk `24`, compileSdk `37`, and JVM toolchain `21`.
- Add `dev.whyoleg.cryptography:cryptography-core:0.6.0` and `cryptography-provider-optimal:0.6.0`; use platform implementations of AES-GCM rather than implementing cryptography.
- Pin the new module's public coroutine runtime and test library to `1.11.0`.
- New targets: Android, JVM, `iosArm64`, `iosSimulatorArm64`, and `macosArm64`; preserve legacy module targets and public signatures.
- Use only public AndroidX APIs in the new module: `OkioSerializer`, `OkioStorage`, `DataStoreFactory.create(storage=...)`, `PreferencesSerializer`, and `PreferenceDataStoreFactory.create(storage=...)`.
- New APIs use package `com.dayanruben.datastore.encrypted.kmp` and explicit public types.
- Accepted plaintext defaults to at most `1_048_576` bytes; configurable limit is `1..16_777_216` bytes. Enforce encrypted read limits and check every `RecordSerializer` write before adapter buffering/encryption. The Okio/Preferences compatibility bridge caps accepted records, not upstream buffering or private encoding allocations; caller inputs and serializer-private allocations remain outside the budget.
- Require a nonblank stable `storeId` whose UTF-8 encoding is at most `1024` bytes. Bind the entire format header and store identity as associated authenticated data.
- AES-256-GCM uses a `32` byte key, a fresh cryptographically random `12` byte nonce, and a `16` byte authentication tag for every write.
- Never accept plaintext, attempt legacy decryption, recreate a missing persisted key, or replace encrypted content automatically after an encryption/authentication/key failure.
- Cancellation and file-system IO failures retain their original types; encryption failures are IO exceptions distinct from AndroidX `CorruptionException`.
- No publication, push, merge, release tag, live credential changes, or production key operations are part of execution.

## Public API and ownership

`DataStoreCipher` is the narrow injected platform boundary: suspending `encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray` and `decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray`. Implementations must be concurrent-safe, return fully authenticated plaintext, and use the fixed nonce/ciphertext/tag layout. Custom implementations are trusted security code. No mutable `Cipher` operation object is shared across calls.

`suspend fun aesGcmCipher(key: ByteArray): DataStoreCipher` imports an externally managed 32 byte key. It does not generate or persist a replacement key. Copy input before import and erase temporary owned key bytes best-effort; do not mutate the caller's array. JVM applications supply a key from their own OS vault, KMS, or secret manager. Do not provide a plaintext file key store or embed a key in examples.

Android exposes `suspend fun createAndroidKeystoreCipher(alias: String): DataStoreCipher` and `suspend fun openAndroidKeystoreCipher(alias: String): DataStoreCipher`. Creation is explicit, rejects an existing alias, and generates a nonexportable AES-256-GCM key in Android Keystore with randomized encryption required. Open rejects a missing alias without generating anything. Keystore operations run on `Dispatchers.IO`; StrongBox and biometric authentication are not implied.

Apple exposes `suspend fun createAppleKeychainCipher(service: String, account: String, accessGroup: String): DataStoreCipher` and `suspend fun openAppleKeychainCipher(service: String, account: String, accessGroup: String): DataStoreCipher`. Creation stores a random 32 byte key using a generic-password Keychain item, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, and no synchronization. A required explicit entitled access group, including its signing prefix, is bound on both create and open; no other groups are searched. A duplicate create or failed open returns `KeyStorageException`; neither overwrites or repairs an item. Keychain lock, missing entitlement, and missing-item failures remain distinguishable via the exception cause/status. The imported native key is in process memory; this is not Secure Enclave-backed AES. Test cleanup deletes only uniquely named test items.

`RecordSerializer<T>` exposes `defaultValue`, `suspend readFrom(BufferedSource)`, and `suspend writeTo(T, RecordWriter)`. `RecordWriter` exposes remaining capacity, byte-array slice writes, and UTF-8 writes without a mutable buffer. `RecordSerializer<T>.encrypted(cipher, storeId, maxPlaintextBytes = 1_048_576): OkioSerializer<T>` provides the preferred bounded-write API. `OkioSerializer<T>.encrypted(...)` provides a compatible format bridge without a write-memory cap. The typed factory accepts either serializer through overloads. `createEncryptedDataStore(serializer, cipher, storeId, path, scope, fileSystem = systemFileSystem(), migrations = emptyList())` and `createEncryptedPreferencesDataStore(cipher, storeId, path, scope, fileSystem = systemFileSystem(), migrations = emptyList())` use the upstream transactional storage. A caller-supplied scope owns lifetime and dispatching; factories require an absolute path and Preferences requires `.preferences_pb`. Factories take no corruption handler, preventing accidental destructive recovery at this convenience boundary. Advanced users can construct upstream storage explicitly.

## Record format and failure semantics

Version 1 bytes are `[45 44 53 4b] [01 version] [01 AES-256-GCM] [12-byte nonce] [ciphertext] [16-byte tag]`; overhead is exactly `34` bytes. Header bytes are literal hex. AAD is the six-byte header, followed by a four-byte unsigned big-endian UTF-8 identity length, then the identity bytes. The identity is supplied by the application and is not stored in the file.

Only a missing file produces `defaultValue`, via upstream storage. An existing empty, short, unknown-version, altered, wrong-key, wrong-identity, or oversized file fails without calling the delegate or modifying the file. Use `EncryptionException : okio.IOException`, `AuthenticationException : EncryptionException`, `UnsupportedFormatException : EncryptionException`, `PayloadTooLargeException : EncryptionException`, and `KeyStorageException : EncryptionException` with non-sensitive messages. The delegated serializer can still report its own parse failures after successful authentication. No plaintext is released before verifying the whole tag, including when a delegate would read only a prefix. Serializer adapters do not close caller-owned sources/sinks.

On write, use `RecordWriter` to check before buffering, authenticate/encrypt completely, then write ciphertext. Latch size failures so swallowing one cannot publish a suffix. Arbitrary Okio/Preferences writers retain their public BufferedSink contract and can exceed the limit while buffering or encoding before the final record is rejected. On read, read at most the configured plaintext limit plus 34 bytes plus one sentinel byte, validate the header, authenticate the complete body, check plaintext length, then invoke the delegate. Clear owned byte arrays and buffers in `finally` blocks where possible; managed/runtime copies mean guaranteed zeroization is not claimed. Empty serialized plaintext is valid and has an authenticated 34 byte record. A truncated existing file is not an empty settings store.

## Reliability and compatibility

Keep the DataStore actor, migrations, caching, file locking, fsync, temporary file handling, and atomic rename in AndroidX. Document single-process use and one active DataStore per normalized absolute path. Reopening requires cancelling and joining the prior scope. A cancelled transform, failed serializer, failed cipher, or failed file replacement must preserve the previous readable value. Preferences must use the public factory so mutable preferences are frozen correctly.

Do not silently migrate any old file. Keep the existing checked-in Tink fixtures read-only and run their compatibility tests. Fix two focused legacy hazards if reproduced: `InputStream.available()` is not an EOF test, and a caller can currently mutate streaming associated data after constructing a serializer. New files require a new path or an application-controlled read/validate/write migration with both old and new keys available. No generic automatic migration or rotation engine is added. Key rotation and backup restoration are explicit application workflows: keep old keys until rewritten ciphertext has been reopened and validated; a device-only key is not restored with ciphertext backups. Never store the key alongside a backup.

## Performance and assurance

The preferred typed API deliberately holds one bounded settings record in memory; Okio/Preferences buffering and private serializer allocations are outside that bound; it does not claim streaming performance. Import each key once per store lifetime. Reuse a cipher wrapper, while obtaining per-operation provider state. Do not introduce global key or plaintext caches. Preserve the legacy Tink streaming option for large files. Add a reproducible JVM benchmark command covering 1 KiB, 64 KiB, and 1 MiB serialization/encryption/readback with warmup, per-case iterations, latency percentiles, and JVM thread allocation where available. Record runtime/provider/OS and report measurements, without flaky wall-clock CI thresholds or a claim about physical-device speed. Size-limit tests and exact ciphertext overhead are deterministic regression gates.

Tests run from commonTest on JVM, macOS, and the iOS simulator: independent AES-GCM known-answer decryption; random nonce behavior; wrong key/AAD and every envelope region; prefix-only delegates; lengths around the configured and default limits; bounded hostile sources; cancellation/IO propagation; stream ownership; actual DataStore reopen/concurrent updates/failed writes; all Preferences value types; old fixtures; explicit key lifecycle and access-group isolation tests. A JVM allocation-counter regression verifies rejection of a 16 MiB bulk argument without copying it into adapter storage. Native library compilation covers the physical iOS target but is not a device execution claim. Android emulator tests cover actual Keystore persistence and missing/duplicate aliases. Keychain lifecycle tests run in `iosSimulatorArm64Test` on a full simulator with test-only entitlements (KT-61470); the unsigned macOS command-line host does not permit data-protection Keychain creation, so signed macOS application acceptance is recorded separately. Any unsupported environment must be reported, never counted as passing.

Add Ubuntu JVM/Android build checks plus macOS ARM64 native tests and artifact validation. Produce JVM and Android API baselines, check native public metadata/compilation, and verify generated POM/Gradle module metadata includes the new module and the BOM constraint. Publishing native artifacts requires a macOS release runner; do not leave Ubuntu publishing a silently incomplete KMP module. No signing secrets are needed for local artifact generation.

## Evidence and sources

Inspected starting commit: `5482cde`. Existing JVM compatibility suites passed before changes on 2026-10-30. Existing Gradle 10 deprecation warnings predate this work.

- [AndroidX KMP DataStore guidance](https://developer.android.com/kotlin/multiplatform/datastore) and the published `1.3.0` source JARs confirm public Okio storage/serializer and Preferences factory APIs.
- [cryptography-kotlin project](https://github.com/whyoleg/cryptography-kotlin) and [AEAD documentation](https://whyoleg.github.io/cryptography-kotlin/primitives/operations/aead/) describe the provider boundary and default random IV layout; Maven Central metadata confirms `0.6.0`.
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore) documents nonexportable key use and randomized encryption.
- [Apple Keychain accessibility](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlockedthisdeviceonly) describes unlocked/device-only key storage.

# Encrypted DataStore Kotlin Multiplatform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reliable, authenticated, bounded-memory DataStore API for Android, JVM, iOS, and macOS while preserving existing encrypted files and APIs.

**Architecture:** Add `encrypted-datastore-kmp` with a common Okio serializer, upstream transactional factories, and a small cipher boundary backed by cryptography-kotlin. Android Keystore and Apple Keychain factories own platform key access. Keep the existing Tink modules and formats independent.

**Tech Stack:** Kotlin 2.4.21, Gradle 9.8.2, AGP 9.4.1, AndroidX DataStore 1.3.0, Okio 3.17.0, cryptography-kotlin 0.6.0, kotlin.test, kotlinx.coroutines.

**Spec:** `docs/superpowers/specs/2026-10-30-kotlin-multiplatform-design.md`

## Global Constraints

- Retain Kotlin `2.4.21`, Gradle `9.8.2`, AGP `9.4.1`, AndroidX DataStore `1.3.0`, Android minSdk `24`, compileSdk `37`, and JVM toolchain `21`.
- Add `dev.whyoleg.cryptography:cryptography-core:0.6.0` and `cryptography-provider-optimal:0.6.0`; use platform implementations of AES-GCM rather than implementing cryptography.
- Pin the new module's public coroutine runtime and test library to `1.11.0`.
- New targets: Android, JVM, `iosArm64`, `iosSimulatorArm64`, and `macosArm64`; preserve legacy module targets and public signatures.
- New APIs use package `com.dayanruben.datastore.encrypted.kmp` and explicit public types.
- Plaintext defaults to at most `1_048_576` bytes; configurable limit is `1..16_777_216` bytes. Enforce limits while serializing/reading, before an unbounded allocation or cryptographic operation.
- Require a nonblank stable `storeId` whose UTF-8 encoding is at most `1024` bytes. Bind the entire format header and store identity as associated authenticated data.
- AES-256-GCM uses a `32` byte key, a fresh cryptographically random `12` byte nonce, and a `16` byte authentication tag for every write.
- Never accept plaintext, attempt legacy decryption, recreate a missing persisted key, or replace encrypted content automatically after an encryption/authentication/key failure.
- Cancellation and file-system IO failures retain their original types; encryption failures are IO exceptions distinct from AndroidX `CorruptionException`.
- No publication, push, merge, release tag, live credential changes, or production key operations are part of execution.

## Review Focus

- Existing zero-byte/truncated/wrong-version ciphertext must fail without overwriting or returning defaults (Task 2/3).
- A prefix-only delegate must never receive bytes from ciphertext with a corrupt final tag (Task 2).
- Locked, missing, duplicate, or replaced platform keys must never trigger key recreation or data deletion (Task 4/5).
- Hostile unbounded sources and serializers must hit the byte limit before unbounded buffering or encryption (Task 2).
- Concurrent/cancelled/failed updates and mutable Preferences must preserve transactional values and reopen correctly (Task 3).

---

## File structure

- `encrypted-datastore-kmp/build.gradle.kts`: new independently published KMP artifact, native hierarchy, device tests, benchmark task.
- `encrypted-datastore-kmp/src/commonMain/kotlin/DataStoreCipher.kt`: cipher contract and raw-key factory backed by a native provider.
- `encrypted-datastore-kmp/src/commonMain/kotlin/EncryptionException.kt`: non-destructive typed failures.
- `encrypted-datastore-kmp/src/commonMain/kotlin/EncryptedOkioSerializer.kt`: versioned format, authenticated identity, limits, stream ownership.
- `encrypted-datastore-kmp/src/commonMain/kotlin/EncryptedDataStoreFactory.kt`: typed/Preferences factories using public AndroidX storage.
- `encrypted-datastore-kmp/src/commonJvmMain/kotlin/JcaDataStoreCipher.kt`: operation-local JCA cipher adapter for nonexportable keys.
- `encrypted-datastore-kmp/src/androidMain/kotlin/AndroidKeystoreCipher.kt`: explicit Keystore create/open.
- `encrypted-datastore-kmp/src/appleMain/kotlin/AppleKeychainCipher.kt`: explicit Keychain create/open.
- Tests follow each responsibility in `commonTest`, `jvmTest`, `androidDeviceTest`, and `iosSimulatorArm64Test`; Keychain needs the full simulator and embedded test entitlements. Common file-system tests use Okio FakeFileSystem with required leak checking.
- `SystemFileSystem.kt` in commonMain and its commonJvmMain/appleMain actuals: system-file defaults without leaking Okio's platform-only `SYSTEM` property into common metadata.
- `encrypted-datastore-kmp/src/jvmTest/kotlin/EncryptedDataStoreBenchmark.kt`: opt-in benchmark executable, excluded from ordinary tests.
- Existing legacy serializer files: only focused regression fixes, with compatibility fixtures preserved.
- Build catalog/settings/BOM, API baselines, CI/publish workflows, README, changelog, `docs/kmp-security.md`, and `docs/kmp-validation.md`: distribution, usage, security contract, evidence.

All Gradle commands below run through the `gradle-run` skill's standalone compact-output wrapper. Expected compiler failures for absent APIs are red evidence; once APIs exist, each behavioral regression must be observed red before its fix. Commit only the files owned by a task. Read the spec and this plan before execution.

### Task 1: Native targets and AES-GCM cipher boundary

**Files:** Create new module build script, common `DataStoreCipher.kt`, `EncryptionException.kt`, `commonTest/kotlin/DataStoreCipherTest.kt`; modify settings, catalog, BOM. Keep existing convention target sets unchanged.

**Interfaces:** Consumes public cryptography-kotlin `AES.GCM`/default provider. Produces `DataStoreCipher.encrypt/decrypt(ByteArray, ByteArray): ByteArray`, `suspend fun aesGcmCipher(key: ByteArray): DataStoreCipher`, and the exception types in the spec.

- [x] **Step 1:** Add the module using the existing convention, then add `iosArm64`, `iosSimulatorArm64`, `macosArm64`, manual `appleMain`/`appleTest`, kotlin.test and pinned dependencies. Add tests: `knownAnswerDecrypts` asserts NIST AES-256-GCM zero-key/zero-IV/empty-plaintext tag `530f8afbc74536b9a963b4f1c4cb738b` decrypts to `byteArrayOf()`; `noncesDiffer` checks two encryptions differ and both decrypt; `rejectsWrongKeyAndAad` expects `AuthenticationException`; `rejectsNon256BitKey` expects `IllegalArgumentException` for 0/16/24/33 bytes.
- [x] **Step 2:** Run `./gradlew :encrypted-datastore-kmp:jvmTest`. Expected: missing new cipher API fails compilation.
- [x] **Step 3:** Implement the exact boundary/factory and exceptions from the spec; copy key bytes during import, use default random IV/tag, propagate cancellation, normalize crypto failures without exposing inputs.
- [x] **Step 4:** Run `./gradlew :encrypted-datastore-kmp:jvmTest :encrypted-datastore-kmp:macosArm64Test :encrypted-datastore-kmp:compileKotlinIosArm64`. Expected: all tests/compile pass. Add the BOM constraint and inspect dependency variants for native artifacts.
- [x] **Step 5:** Commit `feat: add native KMP AES-GCM cipher support`.

### Task 2: Bounded authenticated serializer

**Files:** Create common serializer and `commonTest/kotlin/EncryptedOkioSerializerTest.kt` plus focused common test fixtures.

**Interfaces:** Consumes Task 1 `DataStoreCipher` and exceptions. Produces `fun <T> OkioSerializer<T>.encrypted(cipher: DataStoreCipher, storeId: String, maxPlaintextBytes: Int = 1_048_576): OkioSerializer<T>`.

- [x] **Step 1:** Add failing tests: roundtrip empty/UTF-8/binary payload; fixed header and 34 byte overhead; mutated header/nonce/ciphertext/tag, wrong storeId, zero/truncated/unknown-version inputs fail before a prefix-reading delegate runs; read/write 64 bytes succeed with `maxPlaintextBytes=64`, 65 fail; a never-ending source stops after at most limit+35 bytes; an oversized serializer leaves destination empty; IO and cancellation preserve exception identity; caller streams stay open; invalid identity and limits fail construction.
- [x] **Step 2:** Run `./gradlew :encrypted-datastore-kmp:jvmTest --tests '*EncryptedOkioSerializerTest*'`. Expected: absent extension fails compilation.
- [x] **Step 3:** Implement the six-byte header, exact AAD encoding, bounded source and sink, full authentication before delegation, plaintext length checks, and best-effort cleanup as specified. Do not close supplied source/sink or catch arbitrary delegate failures.
- [x] **Step 4:** Run `./gradlew :encrypted-datastore-kmp:jvmTest :encrypted-datastore-kmp:macosArm64Test`. Expected: all common tests pass on both providers. Verify partial-consumption tests corrupt the final tag and never invoke the delegate.
- [x] **Step 5:** Commit `feat: add bounded authenticated Okio serialization`.

### Task 3: Typed and Preferences transactional factories

**Files:** Create common `EncryptedDataStoreFactory.kt`, `commonTest/kotlin/EncryptedDataStoreTest.kt`, `commonTest/kotlin/EncryptedPreferencesDataStoreTest.kt`; add FakeFileSystem test dependency.

**Interfaces:** Consumes Task 2 extension. Produces the two public factories from the spec, each requiring caller scope/path and accepting optional file system/migrations; typed factory additionally takes `OkioSerializer<T>`.

- [x] **Step 1:** Write failing common integration tests with FakeFileSystem: update, cancel/join scope, reopen and read `"persisted"`; 100 concurrent increments return `100`; a failing transform, throwing serializer, cancelled update, and failed atomic move preserve the prior value on reopen; wrong key and empty existing file retain the same disk bytes; missing file returns default; relative path is rejected; Preferences persists Boolean/Int/Long/Float/Double/String/Set/ByteArray, freezes escaped mutable values, and rejects a path without `.preferences_pb`. Add a migration test from an initial value to a new schema value.
- [x] **Step 2:** Run `./gradlew :encrypted-datastore-kmp:jvmTest --tests '*DataStoreTest*'`. Expected: absent factories fail compilation.
- [x] **Step 3:** Implement `createEncryptedDataStore<T>` and `createEncryptedPreferencesDataStore` with `OkioStorage`, the public AndroidX factories, no corruption handler, an absolute normalized path, and the supplied scope/migrations. Keep production file ownership with AndroidX.
- [x] **Step 4:** Run `./gradlew :encrypted-datastore-kmp:jvmTest :encrypted-datastore-kmp:macosArm64Test :encrypted-datastore-kmp:iosSimulatorArm64Test`. Expected: integration/common suites pass with no leaked file-system resources or scopes. Also reopen a real temporary disk file in a JVM integration test.
- [x] **Step 5:** Commit `feat: add transactional multiplatform DataStore factories`.

### Task 4: Explicit Android Keystore lifecycle

**Files:** Create `commonJvmMain/kotlin/JcaDataStoreCipher.kt`, `androidMain/kotlin/AndroidKeystoreCipher.kt`, `jvmTest/kotlin/JcaDataStoreCipherTest.kt`, `androidDeviceTest/kotlin/AndroidKeystoreCipherTest.kt`; configure device tests and Android test dependencies in module build script.

**Interfaces:** Consumes Task 1 cipher contract. Produces `createAndroidKeystoreCipher(alias: String)` and `openAndroidKeystoreCipher(alias: String)`, both suspending and returning `DataStoreCipher`; missing/duplicate aliases fail as `KeyStorageException`.

- [x] **Step 1:** Add JVM known-answer/interoperability/concurrent-operation tests for JCA and Android instrumented tests that create a unique alias, encrypt, reopen, decrypt, reject duplicate creation, delete only that test alias, then reject open. Assert key bytes are nonexportable and invalid aliases fail before key operations.
- [x] **Step 2:** Run focused JVM tests and `./gradlew :encrypted-datastore-kmp:connectedAndroidDeviceTest` on an owned emulator. Expected: missing APIs fail compilation.
- [x] **Step 3:** Implement operation-local `Cipher.getInstance("AES/GCM/NoPadding")`, 128 bit tags, fresh provider-generated IVs, explicit alias lifecycle, and IO dispatch. Never use a shared mutable JCA Cipher or recreate missing keys.
- [x] **Step 4:** Run JVM cipher tests and Android device tests. Expected: reopen works, key `encoded` is null, 100 concurrent roundtrips remain correct, missing/duplicate keys fail. If task names differ in AGP, resolve once from its task list and record the actual command.
- [x] **Step 5:** Commit `feat: add explicit Android Keystore cipher lifecycle`.

### Task 5: Explicit Apple Keychain lifecycle

**Files:** Create `appleMain/kotlin/AppleKeychainCipher.kt`, `appleTest/kotlin/AppleKeychainCipherTest.kt`; add Native interop opt-ins locally.

**Interfaces:** Consumes Task 1 raw-key factory. Produces `createAppleKeychainCipher(service: String, account: String)` and `openAppleKeychainCipher(service: String, account: String)`, both suspending and returning `DataStoreCipher`.

- [x] **Step 1:** Write failing Apple tests using unique service/account names and test-owned cleanup: create/encrypt/open/decrypt; duplicate create fails and original ciphertext still decrypts; missing open fails and creates no item; wrong-sized stored key fails; blank service/account fail. Test helpers, not production methods, delete test items.
- [x] **Step 2:** Run `./gradlew :encrypted-datastore-kmp:macosArm64Test`. Expected: missing APIs fail compilation.
- [x] **Step 3:** Implement Security framework generic-password storage with WhenUnlockedThisDeviceOnly/no synchronization; secure key generation through the vetted provider; map OSStatus safely, reject duplicate/missing items, copy native bytes safely, erase temporary owned bytes. Do not use plaintext files or global caches.
- [x] **Step 4:** Run `./gradlew :encrypted-datastore-kmp:macosArm64Test :encrypted-datastore-kmp:iosSimulatorArm64Test :encrypted-datastore-kmp:compileKotlinIosArm64`. Expected: Keychain lifecycle tests pass where permitted and physical-target compilation succeeds; report entitlement/locked-key coverage separately from emulator roundtrips.
- [x] **Step 5:** Commit `feat: add explicit Apple Keychain cipher lifecycle`.

### Task 6: Preserve and harden legacy reads

**Files:** Modify `encrypted-datastore/src/commonJvmMain/kotlin/Aead.kt`, `EncryptingSerializer.kt`; extend their commonJvm tests. Preserve every file in `testAssets/`.

**Interfaces:** Consumes existing Tink `Serializer<T>.encrypted` overloads; produces identical signatures/wire formats with correct stream and AAD behavior.

- [x] **Step 1:** Write two regressions: a nonempty input whose `available()` always returns `0` still decrypts old AEAD data; mutating the original AAD array after serializer construction does not change the streaming serializer's binding. Keep previous-version fixture tests as the compatibility gate.
- [x] **Step 2:** Run `./gradlew :encrypted-datastore:jvmTest`. Expected: both new regressions fail behaviorally.
- [x] **Step 3:** Read to EOF rather than using `available()` to infer emptiness, and take an owned AAD copy at construction. Do not change legacy empty-input or corruption-handler behavior in this compatibility task.
- [x] **Step 4:** Run `./gradlew :encrypted-datastore:jvmTest :encrypted-datastore-preferences:jvmTest`. Expected: regressions and checked-in legacy fixtures pass, ignored fixture-generation tests stay ignored, and fixtures have no diff.
- [x] **Step 5:** Commit each independent fix separately: `fix: decrypt legacy streams without relying on available` and `fix: snapshot legacy streaming associated data`.

### Task 7: Benchmarks, distribution gates, and consumer documentation

**Files:** Create JVM benchmark, `docs/kmp-security.md`, `docs/kmp-validation.md`; modify README/changelog, module/build publication metadata, new API baselines, `.github/workflows/check.yml`, `.github/workflows/publish.yml`.

**Interfaces:** Consumes Tasks 1–6. Produces a documented common API, `:encrypted-datastore-kmp:benchmark`, native CI, and complete generated publication metadata without uploading it.

- [x] **Step 1:** Add an opt-in benchmark main for 1 KiB/64 KiB/1 MiB, warmup then measured iterations, p50/p95, throughput, and thread allocation when supported. It must verify roundtrip correctness and consume results. Run `./gradlew :encrypted-datastore-kmp:benchmark`; record runtime and actual figures without CI timing thresholds.
- [x] **Step 2:** Generate and inspect API dumps and all target publication metadata. Run `./gradlew :encrypted-datastore-kmp:apiDump :encrypted-datastore-kmp:apiCheck :encrypted-datastore-kmp:generatePomFileForKotlinMultiplatformPublication :encrypted-datastore-bom:generatePomFileForMavenPublication`. Expected: API baselines include only intentional public declarations, KMP variants and BOM contain the module. Adapt generated task names to observed plugin tasks if needed; do not upload or sign.
- [x] **Step 3:** Add Ubuntu tests/builds, macOS ARM64 host/simulator tests and physical-target compile, Android Keystore emulator tests, and change native publication runner to macOS ARM64. Keep release version checks and secret handling; no release is triggered.
- [x] **Step 4:** Document real common typed/Preferences examples, explicit platform key create/open and lifecycle, stable storeId, limits, threat model, backup/rotation/manual migration, target matrix, release status, and exact local/CI evidence. Mark the new module as unreleased; leave the released coordinates/version untouched. Document benchmarks as host measurements and untested device/key-lock behavior honestly.
- [x] **Step 5:** Run `./gradlew check :sample:assembleDebug :security-crypto-datastore:assembleRelease :security-crypto-datastore-preferences:assembleRelease :encrypted-datastore-kmp:assemble :encrypted-datastore-kmp:iosSimulatorArm64Test :encrypted-datastore-kmp:macosArm64Test`. Expected: all available target tests, API/lint/build gates pass; inspect JUnit summaries and run `git diff --check` and metadata checks. Reuse successful unchanged focused results rather than repeating tests without a reason.
- [ ] **Step 6:** Commit distinct benchmark, build/CI, and docs changes separately. Request the executing-plans skill's one fresh whole-branch review, address important findings with failing regressions and passing suites, record any remaining limits, and leave the branch/worktree available for the user.

## Self-review and execution authorization

All fixed constraints map to Tasks 1–7. Review Focus maps to the stated negative/integration tests. Method names, exception types, layouts, limits, target names, and factory ownership agree with the spec. The platform key managers are separate reviewable tasks; the common serializer/factories share one specified contract. The new module keeps unrelated legacy API internals out of the native graph. Exact native/device behavior and benchmark numbers are evidence to collect during execution, not assumed results.

Execution method: the user explicitly selected `superpowers:executing-plans` and requested execution immediately after writing. Proceed inline without a separate approval pause. Do not merge, push, or publish. A fresh whole-branch review is required at the end.

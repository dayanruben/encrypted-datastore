# KMP validation evidence

Local validation on 2026-10-30 used Apple Silicon macOS, Xcode 27.2, JDK 21.0.11, Kotlin 2.4.21, Gradle 9.8.2, AGP 9.4.1, DataStore 1.3.0, cryptography-kotlin 0.6.0, and kotlinx.coroutines 1.11.0 in the new module. Work started from `5482cde` on branch `codex/kmp-encrypted-datastore`.

## Executed checks

The complete gate passed, including `check`, sample APK assembly, both legacy Android release AARs, new KMP assembly, Android instrumented tests, native tests, and JVM/Android/Native API comparisons:

```bash
python3 scripts/ios_simulator.py create
./gradlew check :sample:assembleDebug \
  :security-crypto-datastore:assembleRelease \
  :security-crypto-datastore-preferences:assembleRelease \
  :encrypted-datastore-kmp:assemble \
  :encrypted-datastore-kmp:macosArm64Test \
  :encrypted-datastore-kmp:iosSimulatorArm64Test \
  :encrypted-datastore-kmp:connectedAndroidDeviceTest \
  -PiosSimulatorDevice="$(python3 scripts/ios_simulator.py id)"
python3 scripts/ios_simulator.py destroy
```

Agent-initiated Gradle commands ran through the standalone compact-output wrapper from the `gradle-run` skill. The command above is the equivalent developer/CI invocation. Android instrumented tests require a running emulator/device; this session used an API 35 ARM64 emulator in read-only mode. The iOS helper creates its own named device from an installed runtime and deletes only the recorded device after validating its identity.

| New module suite | Executed tests | Failures/errors/skips |
| --- | ---: | --- |
| JVM | 39 | 0 / 0 / 0 |
| Android host | 33 | 0 / 0 / 0 |
| macOS ARM64 | 33 | 0 / 0 / 0 |
| iOS ARM64 full simulator | 40 | 0 / 0 / 0 |
| Android API 35 device/emulator | 38 | 0 / 0 / 0 |

These are 183 test executions across targets, including shared tests repeated on each provider/runtime. The seven iOS Keychain tests and five Android Keystore tests exercised actual platform storage. Independent AES-256-GCM known-answer decryption runs in the shared suite. The suite also covers nonce freshness, concurrency, every record byte, truncation, wrong key/identity, a prefix-only reader, bounded infinite sources/serializers, bulk-write allocation rejection, exact 1 MiB default behavior, stream ownership, IO/cancellation, transactional reopen, injected write/atomic-move failures, migrations, and all Preferences value types.

Legacy JVM and Android host suites also passed: `encrypted-datastore` has 8 test cases per host with its fixture-regeneration test intentionally ignored; `encrypted-datastore-preferences` has 2 cases per host with regeneration ignored. Checked-in `testAssets/` has no diff. The two legacy regressions were observed failing before their fixes: zero `available()` on a nonempty AEAD stream, and mutation of the caller's streaming associated data.

The default-budget test was mutation-checked: reducing the default by one byte made the 1 MiB case fail, then the restored shared constant passed on all five runtimes. The aggregate API check was also mutation-checked: removing `openAndroidKeystoreCipher` from the expected Android baseline caused `androidApiCheck` to fail; restoring the baseline made the aggregate check pass. The explicit Android gate is necessary because BCV 0.18.2 does not discover AGP's new KMP Android target automatically.

## Artifact and consumer verification

Generated POM/Gradle module metadata and API dumps were inspected. Every advertised Android, JVM, iOS ARM64, iOS simulator ARM64, and macOS ARM64 variant has a corresponding artifact/target metadata entry. The BOM includes the new module. The new POM explicitly exposes the tested coroutine runtime, public DataStore APIs, and native crypto dependencies. JVM, Android, and merged three-target Native API baselines are checked in.

All new KMP publications and the BOM were staged into a private filesystem Maven repository under `build/kmp-verification-repo`. Only the temporary verification harness disabled signing; these were unsigned local test artifacts. No artifact was sent to an external publication service.

A separate KMP consumer refreshed and resolved the updated Maven artifacts after both review fixes and passed:

- Common metadata compilation using both typed serializer overloads, the Preferences factory, and their default arguments.
- Android compilation importing the public Keystore API.
- Physical iOS and simulator compilation importing the public Apple Keychain API with an explicit access group.
- JVM encrypted persistence/reopen and cipher roundtrip (2 tests).
- macOS native cipher roundtrip against the staged KLIB (1 test).

This catches metadata/publication failures that same-project tests can miss. During implementation it caught `FileSystem.SYSTEM` leaking into common metadata; the final default uses a narrow internal expect/actual function and the external consumer compiles it successfully.

GitHub workflows passed `actionlint`. They now define Ubuntu project checks, API 35 emulator Keystore tests, and macOS 26 ARM64 native/ABI checks. Native publication uses an Apple host to produce all advertised variants. Hosted GitHub CI and signed Maven Central publication were not run in this local task; the new artifact remains unreleased. [GitHub's supported runner labels](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job) identify macOS 26 as ARM64.

## Host benchmark

Run `./gradlew :encrypted-datastore-kmp:benchmark`. The opt-in executable warms each case, validates full roundtrips, measures latency percentiles/throughput and current-thread JVM allocation, and writes `encrypted-datastore-kmp/build/reports/benchmarks/encrypted-datastore.csv`. It runs with a 256 MiB heap and imports one key outside the timed region. Each measurement uses RecordSerializer and includes bounded serialization, encryption, authenticated read, and comparison of all plaintext bytes; file IO and key storage are excluded.

Observed with OpenJDK 21.0.11, macOS ARM64, and SunJCE:

| Plaintext | Measured iterations | p50 | p95 | Roundtrip throughput | Allocated bytes/roundtrip |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 KiB | 500 | 46.38 us | 91.92 us | 18.16 MiB/s | 10,473 |
| 64 KiB | 200 | 342.50 us | 459.71 us | 174.10 MiB/s | 470,426 |
| 1 MiB | 50 | 4.58 ms | 5.49 ms | 227.74 MiB/s | 10,321,537 |

Allocation is cumulative per operation, not peak retained memory. Throughput counts plaintext bytes per complete roundtrip. These are host measurements with warmup/JIT/GC effects, not physical-device or filesystem latency claims. CI has no wall-clock threshold; exact record overhead and resource-limit tests provide deterministic regression gates. The implementation holds/copies one bounded record rather than streaming it.


## Final review and regression evidence

A fresh-context reviewer examined the whole branch after implementation. Both important findings were reproduced with failing tests and fixed in one pass; no critical findings or deferred minors remain.

- Buffering before the write limit: ordinary Okio bulk/direct-buffer writes can allocate before the underlying sink sees them. The preferred typed API now uses RecordSerializer/RecordWriter without exposing a backing buffer. Bulk byte-array and UTF-8 tests failed before implementation. A mutation that restored a buffered intermediate allocated 16,829,400 bytes while rejecting a caller-owned 16 MiB argument at a 64-byte budget; the allocation regression failed. With the strict writer restored, all JVM tests passed. Separate tests preserve the Okio bridge's direct-buffer, outputStream, ByteBuffer, and charset behavior, and prevent a swallowed limit failure from publishing a suffix. The Okio/Preferences bridge explicitly does not promise a write-memory cap.
- Apple lookup identity: unscoped queries could search a different entitled group from the default creation group. Two real simulator tests failed before filtering by accessGroup. Explicit group queries now preserve independent keys for equal service/account pairs in two entitled groups and reject lookup in the wrong group. Both tests and the other five Keychain tests pass.

After these fixes, the full project gate passed again (managed Gradle run 51), API dumps and the bounded-writer benchmark passed (run 50), and updated artifacts were staged (run 52). All 183 new-module test executions passed with zero skips. The separate-consumer refresh passed (run 53): common/Android/physical-iOS/simulator compilation, 2 JVM tests and 1 macOS test validate the final public APIs and artifacts rather than earlier staged output.

The [execution decisions](kmp-decisions.md) record scope choices, costs, and every boundary the reviewer declined to judge.

## Environment and acceptance boundaries

- Apple Keychain lifecycle/protection tests run in a full iOS simulator with test-only entitlements embedded in the test binary. The standalone test process returned service-unavailable status `-25291`; the full runtime resolved it. This follows the test-host limitation documented in [KT-61470](https://youtrack.jetbrains.com/issue/KT-61470/Kotlin-Native-Keychain-operations-fail-in-iOS-tests-due-to-missing-entitlements).
- Unsigned macOS command-line access to the data-protection Keychain returned missing-entitlement status `-34018`. The adapter preserves this failure and does not use a weaker fallback. Signed macOS application Keychain acceptance remains an application signing check.
- `iosArm64` physical-target compilation passed. Physical iPhone execution, Android hardware/StrongBox qualification, locked-device behavior, and application backup/restore/rotation acceptance were not exercised.
- Existing legacy/Tink deprecation warnings and prebuilt Kotlin/Native debug-cache linker warnings were emitted. Compilation, API comparisons, lint, tests, and artifact assembly still passed; source warnings introduced during implementation were resolved.

The public recovery/security contract is in [kmp-security.md](kmp-security.md); the implementation decisions and task coverage are in the [plan](superpowers/plans/2026-10-30-kotlin-multiplatform.md) and [design](superpowers/specs/2026-10-30-kotlin-multiplatform-design.md).

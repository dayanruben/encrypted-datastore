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
| JVM | 32 | 0 / 0 / 0 |
| Android host | 28 | 0 / 0 / 0 |
| macOS ARM64 | 28 | 0 / 0 / 0 |
| iOS ARM64 full simulator | 33 | 0 / 0 / 0 |
| Android API 35 device/emulator | 33 | 0 / 0 / 0 |

These are 154 test executions across targets, including shared tests repeated on each provider/runtime. The five iOS Keychain tests and five Android Keystore tests exercised actual platform storage. Independent AES-256-GCM known-answer decryption runs in the shared suite. The suite also covers nonce freshness, concurrency, every record byte, truncation, wrong key/identity, a prefix-only reader, bounded infinite sources/serializers, exact 1 MiB default behavior, stream ownership, IO/cancellation, transactional reopen, injected write/atomic-move failures, migrations, and all Preferences value types.

Legacy JVM and Android host suites also passed: `encrypted-datastore` has 8 test cases per host with its fixture-regeneration test intentionally ignored; `encrypted-datastore-preferences` has 2 cases per host with regeneration ignored. Checked-in `testAssets/` has no diff. The two legacy regressions were observed failing before their fixes: zero `available()` on a nonempty AEAD stream, and mutation of the caller's streaming associated data.

The default-budget test was mutation-checked: reducing the default by one byte made the 1 MiB case fail, then the restored shared constant passed on all five runtimes. The aggregate API check was also mutation-checked: removing `openAndroidKeystoreCipher` from the expected Android baseline caused `androidApiCheck` to fail; restoring the baseline made the aggregate check pass. The explicit Android gate is necessary because BCV 0.18.2 does not discover AGP's new KMP Android target automatically.

## Artifact and consumer verification

Generated POM/Gradle module metadata and API dumps were inspected. Every advertised Android, JVM, iOS ARM64, iOS simulator ARM64, and macOS ARM64 variant has a corresponding artifact/target metadata entry. The BOM includes the new module. The new POM explicitly exposes the tested coroutine runtime, public DataStore APIs, and native crypto dependencies. JVM, Android, and merged three-target Native API baselines are checked in.

All new KMP publications and the BOM were staged into a private filesystem Maven repository under `build/kmp-verification-repo`. Only the temporary verification harness disabled signing; these were unsigned local test artifacts. No artifact was sent to an external publication service.

A separate KMP consumer resolved those Maven artifacts and passed:

- Common metadata compilation using both public factories and their default arguments.
- Android compilation importing the public Keystore API.
- Physical iOS and simulator compilation importing the public Apple Keychain API.
- JVM encrypted persistence/reopen and cipher roundtrip (2 tests).
- macOS native cipher roundtrip against the staged KLIB (1 test).

This catches metadata/publication failures that same-project tests can miss. During implementation it caught `FileSystem.SYSTEM` leaking into common metadata; the final default uses a narrow internal expect/actual function and the external consumer compiles it successfully.

GitHub workflows passed `actionlint`. They now define Ubuntu project checks, API 35 emulator Keystore tests, and macOS 26 ARM64 native/ABI checks. Native publication uses an Apple host to produce all advertised variants. Hosted GitHub CI and signed Maven Central publication were not run in this local task; the new artifact remains unreleased. [GitHub's supported runner labels](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job) identify macOS 26 as ARM64.

## Host benchmark

Run `./gradlew :encrypted-datastore-kmp:benchmark`. The opt-in executable warms each case, validates full roundtrips, measures latency percentiles/throughput and current-thread JVM allocation, and writes `encrypted-datastore-kmp/build/reports/benchmarks/encrypted-datastore.csv`. It runs with a 256 MiB heap and imports one key outside the timed region. Each measurement includes bounded serialization, encryption, authenticated read, and comparison of all plaintext bytes; file IO and key storage are excluded.

Observed with OpenJDK 21.0.11, macOS ARM64, and SunJCE:

| Plaintext | Measured iterations | p50 | p95 | Roundtrip throughput | Allocated bytes/roundtrip |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 KiB | 500 | 43.46 us | 65.67 us | 20.70 MiB/s | 10,433 |
| 64 KiB | 200 | 326.13 us | 366.54 us | 191.47 MiB/s | 470,386 |
| 1 MiB | 50 | 4.75 ms | 5.70 ms | 219.73 MiB/s | 10,321,407 |

Allocation is cumulative per operation, not peak retained memory. Throughput counts plaintext bytes per complete roundtrip. These are host measurements with warmup/JIT/GC effects, not physical-device or filesystem latency claims. CI has no wall-clock threshold; exact record overhead and resource-limit tests provide deterministic regression gates. The implementation holds/copies one bounded record rather than streaming it.

## Environment and acceptance boundaries

- Apple Keychain lifecycle/protection tests run in a full iOS simulator with test-only entitlements embedded in the test binary. The standalone test process returned service-unavailable status `-25291`; the full runtime resolved it. This follows the test-host limitation documented in [KT-61470](https://youtrack.jetbrains.com/issue/KT-61470/Kotlin-Native-Keychain-operations-fail-in-iOS-tests-due-to-missing-entitlements).
- Unsigned macOS command-line access to the data-protection Keychain returned missing-entitlement status `-34018`. The adapter preserves this failure and does not use a weaker fallback. Signed macOS application Keychain acceptance remains an application signing check.
- `iosArm64` physical-target compilation passed. Physical iPhone execution, Android hardware/StrongBox qualification, locked-device behavior, and application backup/restore/rotation acceptance were not exercised.
- Existing legacy/Tink deprecation warnings and prebuilt Kotlin/Native debug-cache linker warnings were emitted. Compilation, API comparisons, lint, tests, and artifact assembly still passed; source warnings introduced during implementation were resolved.

The public recovery/security contract is in [kmp-security.md](kmp-security.md); the implementation decisions and task coverage are in the [plan](superpowers/plans/2026-10-30-kotlin-multiplatform.md) and [design](superpowers/specs/2026-10-30-kotlin-multiplatform-design.md).

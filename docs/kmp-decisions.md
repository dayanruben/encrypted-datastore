# KMP execution decisions

These decisions were recorded during the authorized inline execution and final review. The plan, security contract and validation evidence reflect them. Each entry includes its remaining cost or failure risk.

1. Use a new encrypted-datastore-kmp artifact for Android/JVM/iOS ARM64/simulator/macOS ARM64 — avoids changing legacy formats/dependencies and follows published upstream target variants — users on other native targets need a later expansion.

2. User explicitly requested execution immediately after writing; no extra plan-approval pause — preserves explicit execution authorization — design assumptions remain visible in the spec.

3. Record task completion from standalone gradle-run wrapper receipts rather than nesting Gradle inside task-done — preserves the Gradle skill lifecycle and avoids duplicate validation — task-done automation is replaced by an equivalent ledger entry.

4. Apply the supported cryptography 0.6.0 Gradle plugin with configureSwiftLinkerOpts=true — the published CryptoKit cinterop hardcodes /Applications/Xcode.app and this host uses Xcode-27.2.0.app — consumers with renamed Xcode also need the documented plugin.

5. The reader consumes at most limit+35 bytes; Okio may prefetch one 8192-byte segment into its own buffer — the supplied BufferedSource controls prefetch — bounded memory includes that fixed segment.

6. Run Keychain lifecycle tests in iosSimulatorArm64Test on a full owned simulator with embedded test-only entitlements (KT-61470), not the unavailable command-line hosts — preserve data-protection Keychain policy and avoid a legacy fallback — signed macOS app Keychain acceptance remains a release/device verification gap.

7. Strict write budgets require RecordSerializer/RecordWriter; preserve the arbitrary Okio/Preferences bridge with an explicit accepted-record-only limit — sealed BufferedSink cannot intercept every bulk/direct-buffer write before allocation — callers needing bounded adapter writes must adopt the typed writer and bound their own private allocations.

8. Require an explicit entitled Apple accessGroup on creation and opening — Apple defaults creation to the first group but searches all groups on an unscoped lookup — callers must supply the full signed entitlement group; this intentional new-module API adjustment leaves legacy APIs unchanged.

9. Real-device, locked-key, hardware, signed macOS and backup acceptance — These need application entitlements, physical hardware or production recovery policy and were not exercised; report them as unverified acceptance rather than infer them from emulator results. — Applications must qualify those environments before release.

10. Cross-process provisioning, multiprocess stores and filesystem aliases — Keep the public single-process, one-active-instance-per-file contract and require external lifecycle coordination; normalized paths cannot identify every symlink/hardlink alias. — Applications violating this contract can race key creation or file updates.

11. Replay, deletion and compromised applications/devices — Retain the documented at-rest disclosure/tamper threat model; AEAD alone does not provide rollback resistance or survive key compromise. — Applications need an external trusted version/recovery policy for those threats.

12. Private serializer allocations and custom implementations violating contracts — Bound adapter writes through RecordWriter, but caller inputs, private encoding, injected cipher behavior and contract-violating sources remain trusted application code. — The record limit is not a process-wide memory/security sandbox; custom code can allocate or behave unsafely.

13. Guaranteed zeroization — Clear owned arrays best-effort without promising erasure of runtime/provider copies or pooled buffers. — Secrets may remain in process memory after an operation.

14. Legacy empty/corruption/partial-stream semantics — Preserve legacy formats and behavior except the two reproduced regressions; the new full-record authentication contract belongs to the new artifact. — Legacy streaming consumers retain their previous semantics and need explicit migration for the new contract.

15. Unadvertised targets, Swift distribution, hosted CI and external publication — Do not claim unsupported architectures/browser/Swift packages, hosted execution, or a release from local compilation and staging. — Those delivery paths require future implementation or separate validation.

16. Canonical ABI trailing blank lines — Retain the two generated JVM/Android dump EOF blank lines to match the API tool and existing baseline style. — git diff --check over the whole branch reports two cosmetic generator-format warnings.

Deferred minors: none. Both important review findings were fixed and regression-tested.

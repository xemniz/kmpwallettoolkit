# Plan — Spec G: Align iOS Trust Wallet Core error contract with WalletTest

## 1. Summary
`WalletTest.kt` (commonTest) encodes two error-message invariants that the JVM stub honors but the iOS actual does not:
1. `NotImplementedError.message` must contain `"Trust Wallet Core native bridge"`.
2. When `signTransaction` is reached on an unconfigured runtime, the thrown error must mention `"signTransaction"`.

The fix is localized to two files in `wallet-core`: widen `TrustWalletCoreRuntime.requireIosAdapter` to (a) embed the "native bridge" phrase and (b) accept an optional `method: String? = null` that gets appended, and reorder `TrustWalletCoreNativeBridge.ios.kt::signTransaction` so the adapter check fires before the type-check. The spec's approach is reasonable and does not touch the test, the interface, or any other platform actual.

## 2. Files to create / modify
| Path | Source set | Action |
|------|-----------|--------|
| `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreIosBridge.kt` | commonMain | Modify — widen `requireIosAdapter` signature + rewrite thrown message. |
| `wallet-core/src/iosMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.ios.kt` | iosMain | Modify — pass `method = "createMnemonic"/"deriveAddress"/"signTransaction"`; reorder `signTransaction` so `requireIosAdapter(...)` is called before `require(... is EvmTransactionData)`. |
| `wallet-core/src/commonTest/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreRuntimeTest.kt` | commonTest | **Optional (Phase 3).** New file. Asserts `requireIosAdapter("foo")` message contains both `"Trust Wallet Core native bridge"` and `"(method=foo)"`. Spec §Acceptance 6 permits; does not require. Must be a **new** file — never edit `WalletTest.kt`. |

**Hot files (CLAUDE.md §7):** none. `ChainRegistry.kt`, `Chain.kt`, `libs.versions.toml`, `settings.gradle.kts`, `CLAUDE.md`, `.claude/agents/*` are untouched.

**Forbidden to touch** (restating spec): `WalletTest.kt`, `TrustWalletCoreNativeBridge.jvm.kt`, `TrustWalletCoreNativeBridge.android.kt`, `TrustWalletCoreIosAdapter` interface, `TrustWalletCoreEvmSigningRequest`, anything outside `wallet-core`.

## 3. Phases

### Phase 0 — Baseline
Record current JVM test state so Phase 1's no-JVM-behavior-change claim is checkable.
- Run `./gradlew :wallet-core:jvmTest` and note pass/fail set. (Expected: all pass.)
- Run `./gradlew :wallet-core:compileKotlinIosX64` and capture the current iOS test failures for reference.

### Phase 1 — Reorder iOS `signTransaction` (ios.kt only)
**Goal:** establish that the adapter check throws before the type-check, independent of the message change.

Changes to `wallet-core/src/iosMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.ios.kt`:
- In `signTransaction`, move `val adapter = TrustWalletCoreRuntime.requireIosAdapter()` **above** the `require(transaction is EvmTransactionData) { ... }` block. Call `adapter.signEvmTransaction(...)` from the captured local.
- Do not change `createMnemonic` or `deriveAddress` yet.
- Do not change the `require(...)` message or `hexToByteArray()` call.

**Diff check (required):**
- `./gradlew :wallet-core:jvmTest` — must match Phase 0 baseline exactly. No JVM source was touched, so any change is a bug in the change set.
- `git diff` shows changes only in `ios.kt`. No other file touched.

**Green gate:**
- `./gradlew :wallet-core:allTests` — JVM side green (matches Phase 0). iOS side: the second failing test (`trustWalletCoreSignTransactionFallsBackUntilRuntimeIsAvailable`) still fails **for a different reason now** — the message lacks `"signTransaction"`. This is expected and resolved in Phase 2. Note this in the phase report.
- `./gradlew :wallet-core:compileKotlinJvm :wallet-core:compileKotlinIosX64` — green.

### Phase 2 — Widen `requireIosAdapter` in commonMain + wire `method` at call sites
**Goal:** make both iOS tests green by emitting contract-compliant error messages.

Changes to `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreIosBridge.kt`:
- Change `internal fun requireIosAdapter(): TrustWalletCoreIosAdapter` to `internal fun requireIosAdapter(method: String? = null): TrustWalletCoreIosAdapter`.
- Rewrite the thrown `NotImplementedError` message so it:
  - Contains the literal substring `"Trust Wallet Core native bridge"` (satisfies test 1).
  - Still mentions `TrustWalletCoreRuntime.installIosAdapter(...)` so integrators know how to fix it.
  - When `method != null`, appends `" (method=$method)"` at the end (mirrors JVM stub's `jvmNotSupported(method)` style).
- Default value `null` preserves backward-compat for any hypothetical zero-arg call site (spec §Acceptance 5).

Changes to `wallet-core/src/iosMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.ios.kt`:
- `createMnemonic` → `TrustWalletCoreRuntime.requireIosAdapter(method = "createMnemonic").createMnemonic()`.
- `deriveAddress` → `TrustWalletCoreRuntime.requireIosAdapter(method = "deriveAddress").deriveAddress(...)`.
- `signTransaction` → update the Phase 1 local to `requireIosAdapter(method = "signTransaction")`.

**Green gate:**
- `./gradlew :wallet-core:allTests` — fully green on JVM **and** iOS. All four `WalletTest` tests pass on iOS.
- `./gradlew :wallet-core:compileKotlinJvm :wallet-core:compileKotlinIosX64` — green.
- `git diff main -- wallet-core/src/commonTest/kotlin/xyz/wallet/toolkit/core/WalletTest.kt` prints nothing (spec §Acceptance 3).

### Phase 3 — Optional regression test for `requireIosAdapter(method)`
**Only execute if time permits; spec explicitly marks optional.**

Create `wallet-core/src/commonTest/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreRuntimeTest.kt`. Two tests:
1. `requireIosAdapterWithoutMethodIncludesNativeBridgePhrase` — calls `TrustWalletCoreRuntime.clearIosAdapter()` then asserts `requireIosAdapter()` throws `NotImplementedError` whose message contains `"Trust Wallet Core native bridge"` and **does not** contain `"(method="`.
2. `requireIosAdapterWithMethodIncludesMethodTag` — asserts the thrown message contains both `"Trust Wallet Core native bridge"` and `"(method=foo)"` when called with `requireIosAdapter("foo")`.

Notes:
- `requireIosAdapter` is `internal`. A commonTest in the same package + module can see it.
- Test must `clearIosAdapter()` in setup to ensure deterministic state (no cross-test coupling with any future iOS adapter-install test).
- Never edit `WalletTest.kt`.

**Green gate:**
- `./gradlew :wallet-core:allTests` — green on JVM and iOS.
- `./gradlew :wallet-core:compileKotlinJvm :wallet-core:compileKotlinIosX64` — green.

### Phase 4 — Final cross-platform compile + contract audit
- `./gradlew :wallet-core:allTests` — green.
- `./gradlew :wallet-core:compileKotlinJvm :wallet-core:compileKotlinIosX64` — green.
- `git diff main -- wallet-core/src/commonTest/kotlin/xyz/wallet/toolkit/core/WalletTest.kt` — empty.
- `git diff main -- wallet-core/src/jvmMain wallet-core/src/androidMain` — empty (no platform drift into out-of-scope actuals).
- Grep confirm: no new direct imports of `wallet.core.jni.*` anywhere, no `java.*`/`android.*` in commonMain, no `kotlin.random.Random` introduced.

## 4. Golden vectors
**Not applicable.** No signing, entropy, key derivation, or transaction serialization path is modified. The thrown error messages are the only observable change; there is no signature or hash to pin.

## 5. Crypto hygiene — CLAUDE.md §4 checklist
- Rule 1 (no logging secrets): error messages being added contain method names only (`createMnemonic`, `deriveAddress`, `signTransaction`). No mnemonic, key, or payload content is ever placed in the message.
- Rule 2 (SecureRandom): untouched.
- Rule 3 (constant-time compare): untouched.
- Rule 4 (nonces): untouched.
- Rule 5 (golden vectors): N/A — no signing path altered. The existing iOS adapter flow that would sign is still behind the same unchanged `adapter.signEvmTransaction(...)` call.
- Rule 6 (never weaken assertions): we are doing the opposite — bringing the iOS actual into compliance with the existing assertion. `WalletTest.kt` is not modified (verified via `git diff` gate in Phase 4).
- Rule 7 (address casing): untouched.
- Rule 8 (signing payload serialization): untouched.

## 6. Risks / Open questions
- **Grey-zone: iOS test execution on the CI host.** `:wallet-core:allTests` on Apple Silicon typically runs `iosSimulatorArm64Test`; on x64 it runs `iosX64Test`. Both execute commonTest. The implementer should confirm the host's `allTests` includes at least one iOS target and that it actually runs (not skipped). If iOS is skipped, Phase 2's claim of "fully green on iOS" is vacuous — stop and raise.
- **`TrustWalletCoreRuntime.iosAdapter` is process-global mutable state.** Phase 3's test must `clearIosAdapter()` in setup or it will flake on hosts where other tests install an adapter. Not a risk for Phases 1 and 2 because `WalletTest.kt` never calls `installIosAdapter`.
- **Message-substring contract fragility.** We are pinning `"Trust Wallet Core native bridge"` and `"signTransaction"` as substrings. Any future cosmetic change to the message risks breaking `WalletTest`. Acceptable — this is exactly the contract the spec wants to entrench. Mention in PR body so future maintainers know.
- **Zero-arg `requireIosAdapter()` call sites.** Grep confirms none outside `ios.kt`, but the `internal` visibility means a commonTest could call it. Defaulting `method` to `null` preserves compatibility regardless.

## 7. What this plan does NOT do
- Does not modify `WalletTest.kt` (load-bearing per CLAUDE.md §4 rule 6 and spec).
- Does not modify `TrustWalletCoreNativeBridge.jvm.kt` or `TrustWalletCoreNativeBridge.android.kt`.
- Does not modify the `TrustWalletCoreIosAdapter` interface or `TrustWalletCoreEvmSigningRequest`.
- Does not ship a real iOS Trust Wallet Core adapter — tests assert the "not configured" path only.
- Does not add `method` to the public `actual fun signTransaction(...)` signature or any other platform actual.
- Does not touch `gradle/libs.versions.toml`, `settings.gradle.kts`, any other module, or sample apps.
- Does not add DI, logging frameworks, or new dependencies.
- Does not run `./gradlew build` — every phase uses scoped `:wallet-core:*` tasks per CLAUDE.md §3.

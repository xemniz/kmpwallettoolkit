# S3 — Import-wallet flow — Implementation plan

## 1. Summary

Add a mnemonic-import screen to `sample-compose` (commonMain only) that does surface-level word-count validation, delegates real validation to `Wallet.fromMnemonicWithTrustWalletCore(mnemonic)`, stores the resulting `Wallet` in the S1 session, and routes to `Route.Home`. The spec's approach is reasonable: no new deps, no `wallet-*` edits, plain `MutableState` per CLAUDE.md §5, redaction-aware state holder per §4.1.

One spec drift to flag before execution: the spec says "all four files are new. No existing file... is modified." However `App.kt` currently routes **only** `Route.Welcome`; every other route (including `Route.Import`) falls through to `PlaceholderScreen`. AC #2 (`:sample-app:assembleDebug`) will build regardless, but the `Route.Import` entry will not actually render `ImportWalletScreen` unless `App.kt`'s `when` is extended. See Risks / Open questions #1 — I recommend extending `App.kt`, and I raise it here rather than silently expanding scope.

## 2. Pre-flight verification (already done by planner)

- `Wallet.fromMnemonicWithTrustWalletCore(mnemonic: String): Wallet` exists in `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/Wallet.kt` line 30. Signature matches spec. No adjustment needed.
- S1 session API: `WalletSession` exposes a `var wallet: Wallet?` (plain setter via Kotlin property). No `set(...)` function; assignment is the setter. The CompositionLocal is `LocalWalletSession` in `WalletSessionHolder.kt`. Use `LocalWalletSession.current.wallet = imported` from a `@Composable`.
- S1 `Navigator` exposes `push`, `pop`, `replace`. `replace(Route.Home)` is available (matches spec's navigation contract).
- `Route.Import` and `Route.Home` are already defined in `Route.kt`.
- `sample-compose/build.gradle.kts`: **no `commonTest` source set** is wired (only `commonMain.dependencies { ... }`). Per spec AC #4 guard, **do not** add the test file. Flag in PR body.
- CLAUDE.md §7 hot files (`gradle/libs.versions.toml`, `settings.gradle.kts`, `ChainRegistry.kt`, `Chain.kt`, `CLAUDE.md`): none touched by this plan.

## 3. Files to create / modify

All under source set `sample-compose/commonMain`.

**New files:**
- `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/MnemonicSurfaceValidation.kt`
- `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/ImportWalletState.kt`
- `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/MnemonicPasteField.kt`
- `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/ImportWalletScreen.kt`

**Modify (pending user decision, see Risks #1):**
- `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/App.kt` — extend the `when (route)` to handle `Route.Import -> ImportWalletScreen(navigator)`. Not a CLAUDE.md §7 hot file.

**Not touched:** any `wallet-*` module, `sample-app/*`, `gradle/libs.versions.toml`, `settings.gradle.kts`, any `build.gradle.kts`, S1 session / nav / theme / ui files.

## 4. Phases

### Phase 1 — Pure surface validation (no UI)

Create `MnemonicSurfaceValidation.kt`:
- `sealed interface ValidationResult { Empty; data class WrongWordCount(count: Int); data class Valid(words: List<String>) }`
- `fun validate(input: String): ValidationResult` — `trim()`, empty → `Empty`; split on `\s+` regex; lowercase each word; if `words.size in setOf(12,15,18,21,24)` → `Valid(words)`, else `WrongWordCount(words.size)`.
- File contains no imports from `androidx.*` (pure Kotlin), no `println`, no logging.

**Gate:** `./gradlew :sample-compose:compileKotlinJvm`.

(Skipping the "add failing tests first" phase because `commonTest` is not wired; see §5 / AC #4 guard. The function is small and pure; its correctness is exercised via the UI disabled-button rule in Phase 3.)

### Phase 2 — State holder

Create `ImportWalletState.kt`:
- Class `ImportWalletState` with:
  - `var input: String by mutableStateOf("")` — on change, also sets `submissionError = false` (use a setter wrapper method `setInput(newValue)` OR a derivedStateOf pattern; the setter-method approach is simpler and keeps redaction control explicit).
  - `val validation: ValidationResult get() = validate(input)` (derived; acceptable because `input` is a `MutableState`).
  - `var submissionError: Boolean by mutableStateOf(false)`.
  - `var isSubmitting: Boolean by mutableStateOf(false)`.
  - `override fun toString(): String = "ImportWalletState(input=REDACTED, validation=${validation::class.simpleName}, submissionError=$submissionError, isSubmitting=$isSubmitting)"` — never interpolates `input` or `Valid.words`.
- `@Composable fun rememberImportWalletState(): ImportWalletState = remember { ImportWalletState() }`.

**Gate:** `./gradlew :sample-compose:compileKotlinJvm`.

### Phase 3 — UI: paste field + screen + (optionally) App.kt wiring

Create `MnemonicPasteField.kt`:
- `@Composable fun MnemonicPasteField(value: String, onValueChange: (String) -> Unit, wordCountLabel: String, modifier: Modifier = Modifier)` using `OutlinedTextField` (material3) with `singleLine = false`, `keyboardOptions = KeyboardOptions(autoCorrect = false, capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii)`, text style from `MonoText` typography. Helper text below shows `wordCountLabel` (caller passes `"$N words"` computed from validation — this keeps the field file free of validation logic).
- No clipboard auto-read. No logging of `value`.

Create `ImportWalletScreen.kt`:
- `@Composable fun ImportWalletScreen(navigator: Navigator)`:
  - `val state = rememberImportWalletState()`
  - `val session = LocalWalletSession.current`
  - Wrap in `PhoneFrame { Column(...) { ... } }`.
  - Title `"Restore wallet"` via `Text` styled from theme.
  - Source-picker row: four chips (reuse `ChainChip` component if suitable, otherwise simple `Surface`+`Text` stubs). Only **Phrase** is selected/enabled. **Private key**, **Keystore**, **Watch-only** render with a "Coming soon" chip next to the label; tap is a no-op.
  - `MnemonicPasteField(value = state.input, onValueChange = state::setInput, wordCountLabel = when (val v = state.validation) { ValidationResult.Empty -> ""; is ValidationResult.WrongWordCount -> "${v.count} words"; is ValidationResult.Valid -> "${v.words.size} words" })`.
  - `PrimaryButton(text = "Restore", onClick = { ... }, enabled = state.validation is ValidationResult.Valid && !state.isSubmitting)`. Note: verify `PrimaryButton` exposes an `enabled` parameter; if not, gate via wrapping logic (no edits to `PrimaryButton` — it's an S1 file). If `PrimaryButton` has no `enabled`, substitute a direct `Button` from material3 for this screen; flag in PR.
  - On click: `state.isSubmitting = true`; `try { val phrase = (state.validation as ValidationResult.Valid).words.joinToString(" "); val w = Wallet.fromMnemonicWithTrustWalletCore(phrase); session.wallet = w; navigator.replace(Route.Home) } catch (t: Throwable) { state.submissionError = true; state.isSubmitting = false }` — do **not** reference `t.message`, `t.cause`, or re-throw with context. No logger calls at all.
  - If `state.submissionError`, render `Text("Could not restore wallet — check the phrase and try again.")` below the button.

Modify `App.kt` `when (val route = navigator.current)` to add `is Route.Import -> ImportWalletScreen(navigator)` above the `else` fallback. (Keep `Route.Welcome` branch as-is.)

**Gate:**
- `./gradlew :sample-compose:assemble`
- `./gradlew :sample-app:assembleDebug`

### Phase 4 — Manual AC verification

- AC #5 grep: `grep -R "println\|Log\." sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/` → expect zero matches.
- AC #6: mentally verify `ImportWalletState.toString()` text contains `input=REDACTED`.
- AC #7: disabled-button rule: eye-check the `enabled = ...` expression matches `{12,15,18,21,24}`-word inputs only.
- AC #8: on Android, paste a garbage-but-correct-word-count phrase, tap Restore, expect generic error, no phrase in logcat.

## 5. Golden vectors

Not applicable. This task does no signing, no serialization of signing input, no key derivation. The only cryptographic call is delegated to `Wallet.fromMnemonicWithTrustWalletCore`, whose correctness is guarded by `wallet-core`'s own tests. CLAUDE.md §4.5 (golden vectors) therefore does not apply here — surfacing this explicitly because the task is crypto-adjacent.

## 6. CLAUDE.md §4 items that DO apply

- **§4.1 (no logging secrets):** `ImportWalletState.toString()` redacts `input`; no `println` / `Log.*` under `.../flows/import/`; caught `Throwable` is not dereferenced for `.message`. Enforced by AC #5 grep.
- **§4.2 (no `kotlin.random.Random`):** this flow needs no randomness at all. No import of `kotlin.random.*`.
- **§4.7 (address casing):** not exercised on this screen (no address rendered). Noted for S4.
- **§4.3 / §4.4 / §4.6 / §4.8:** not exercised (no constant-time comparison, no nonces, no assertion-weakening, no transaction serialization).

## 7. Risks / Open questions

1. **`App.kt` routing extension.** `App.kt`'s `when` currently only handles `Route.Welcome`. Without editing it, `Route.Import` falls through to `PlaceholderScreen` — meaning the new `ImportWalletScreen` is dead code. Spec AC #3 says "No S1 file... is modified," but `App.kt` is the S1 composition root, and without editing it the feature is inert. **Recommendation:** extend `App.kt` with the single `is Route.Import ->` branch. This is a trivial, additive change. **Flag to user:** confirm this is acceptable, or treat it as a known follow-up (in which case this spec ships dead code).
2. **JVM actual for `TrustWalletCoreWalletEngine` throws `NotImplementedError`.** Per CLAUDE.md §2 jvmMain specifics and spec Open Question #1. `:sample-compose:compileKotlinJvm` passes (compile-only); Android path via `:sample-app:assembleDebug` compiles and links the real JNI. Runtime behaviour on JVM (desktop preview) will throw; that throw is caught by the generic-error handler, which is acceptable. No action required beyond this acknowledgement.
3. **`Wallet.fromMnemonicWithTrustWalletCore` validation timing.** If the Android JNI defers mnemonic validation until `deriveAddress`, an invalid phrase would be stored in session and only blow up on Home (S4). Spec Open Question #1 proposes forcing a `wallet.address(SupportedChain.Ethereum)` call inside the import action to surface invalidity immediately. **Recommendation for implementer:** include that forced-address call inside the `try` block. Its result is discarded; its throw triggers the generic error path. No session write happens until both `fromMnemonic...` and `address(...)` return normally.
4. **`PrimaryButton` API surface.** If the S1 `PrimaryButton` does not expose `enabled: Boolean`, the implementer must NOT edit `PrimaryButton.kt` (that would be an S1 edit and violate AC #3). Substitute a raw material3 `Button` on this screen only, and flag in the PR body.
5. **`commonTest` for `sample-compose` is not wired.** Confirmed by reading `build.gradle.kts`. Per spec AC #4 guard, the validation tests are punted to a future S1-scoped task. Flag in PR body.
6. **CLAUDE.md §7 hot-file check:** none of the files this plan touches are listed as hot. `App.kt` is not listed. Safe to proceed.

## 8. What this plan does NOT do

- Does **not** add `commonTest` source set to `sample-compose` (structural; needs its own spec).
- Does **not** add `MnemonicSurfaceValidationTest.kt` (gated on commonTest existing).
- Does **not** embed the BIP-39 wordlist anywhere.
- Does **not** implement private-key, keystore, or watch-only import paths (disabled UI stubs only).
- Does **not** add BIP-39 passphrase (25th word) support.
- Does **not** persist the session across app restarts.
- Does **not** render address or balance (S4 scope).
- Does **not** edit `wallet-core`, `wallet-utils`, `wallet-evm`, `wallet-rpc`.
- Does **not** edit `gradle/libs.versions.toml`, `settings.gradle.kts`, or any `build.gradle.kts`.
- Does **not** add DI (Koin/Hilt), ViewModel, Flow pipelines, or MVI plumbing.
- Does **not** add Compose UI tests, Paparazzi snapshots, or instrumented tests.
- Does **not** add analytics/telemetry.
- Does **not** add `PrimaryButton` enablement param if missing (falls back to raw `Button`).
- Does **not** touch any file outside `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/` except — pending user confirmation on Risk #1 — `App.kt`.

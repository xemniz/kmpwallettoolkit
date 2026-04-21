# S3 — Import-wallet flow (mnemonic; direction A · Classic)

## Goal

Give the sample app a mnemonic-import screen matching the Classic-A wireframe: a paste-area with surface validation (word count only: 12/15/18/21/24 words, trimmed and lowercased), a "Restore" CTA that constructs a `Wallet` via the toolkit's mnemonic-import factory, stores the result in the session, and navigates to Home. Dictionary / checksum validation is delegated to the toolkit. Error handling is deliberately opaque — the user-facing message must never include the raw input phrase.

This phase depends on the navigation, theme, and session scaffolding introduced in S1.

## Module(s) touched

- `sample-compose` (commonMain only; no platform source sets added).

No `wallet-*` module is edited by this spec. No `build.gradle.kts`, `settings.gradle.kts`, or `gradle/libs.versions.toml` edits.

## Files expected to change

All four files are **new**. No existing file in any module is modified.

- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/ImportWalletScreen.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/ImportWalletState.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/MnemonicPasteField.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/MnemonicSurfaceValidation.kt`

Optional (only if the `commonTest` source set for `sample-compose` already exists — see AC #4 guard):

- `sample-compose/src/commonTest/kotlin/xyz/wallet/toolkit/sample/flows/import/MnemonicSurfaceValidationTest.kt`

## Design

### Screen composition (Classic-A)

`ImportWalletScreen` is a single-column Compose screen wrapped in S1's `PhoneFrame`, styled via S1's `WalletTheme`:

1. Title: `"Restore wallet"`.
2. Source-picker tabs row — four chips: **Phrase** (selected, enabled), **Private key**, **Keystore**, **Watch-only**. The last three are disabled and render a small "Coming soon" chip next to their label. Tapping a disabled tab is a no-op. This row exists purely to set user expectation; the `Phrase` tab is the only wired path.
3. `MnemonicPasteField` — dominant element. Multi-line `TextField`-equivalent styled with S1's `MonoText` typography, `singleLine = false`, `autoCorrect = false`, `keyboardOptions` with `KeyboardCapitalization.None` and `KeyboardType.Ascii` (or the Compose Multiplatform equivalent). No clipboard auto-read. No autocomplete suggestions. A small helper label under the field shows `"<N> words"` as the user types (purely derived from the current input via `MnemonicSurfaceValidation.validate`); no phrase text is logged to construct this label.
4. "Restore" `PrimaryButton` — disabled unless the current input validates as `ValidationResult.Valid`. Tapping it invokes the import action (described below).
5. Generic inline error slot (below the CTA) — rendered when the toolkit's factory throws. Copy: `"Could not restore wallet — check the phrase and try again."` The exception message is **never** surfaced.

### State holder

`ImportWalletState` is a plain Compose state holder (no ViewModel, no DI). It exposes:

- `var input: String` (backed by `mutableStateOf`).
- `val validation: ValidationResult` (derived from `input`).
- `var submissionError: Boolean` (set to true when import throws, reset to false on any `input` change).
- `var isSubmitting: Boolean` — guards against double-tap while the synchronous factory runs.

`ImportWalletState.toString()` must be overridden to return a fixed redacted string, e.g.
`"ImportWalletState(input=REDACTED, validation=<kind>, submissionError=<bool>, isSubmitting=<bool>)"`.
The `input` value must not appear in `toString`, logs, or any exception rethrow. (CLAUDE.md §4.1.)

### Surface validation

`MnemonicSurfaceValidation` is a pure, stateless Kotlin file exposing:

```
sealed interface ValidationResult {
    data object Empty : ValidationResult
    data class WrongWordCount(val count: Int) : ValidationResult
    data class Valid(val words: List<String>) : ValidationResult
}

fun validate(input: String): ValidationResult
```

Semantics:

- `input.trim()` first. If empty → `Empty`.
- Split on one-or-more Unicode whitespace characters (regex `\s+`).
- Lowercase each resulting word (BIP-39 English wordlist is all lowercase; normalizing is defensive and matches what the toolkit expects).
- Valid word counts: exactly one of `{12, 15, 18, 21, 24}`. Any other count → `WrongWordCount(count)`.
- On success → `Valid(words)` where `words` is the normalized list.

What this function **does not** do, by design:

- No BIP-39 dictionary lookup. Embedding the 2048-word list in sample-compose is out of scope (non-goal) and overlapping with the toolkit's own check.
- No checksum validation. That is the toolkit's responsibility via its mnemonic-import factory.
- No logging. The function is pure; callers must not log its `Valid.words` either.

### Import action

On "Restore" tap (guarded by `!isSubmitting && validation is Valid`):

1. Set `isSubmitting = true`.
2. Reconstruct the normalized phrase as `words.joinToString(" ")`.
3. Call the toolkit's mnemonic-import factory. Based on `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/Wallet.kt` (inspected as part of this spec), the intended entry point is `Wallet.fromMnemonicWithTrustWalletCore(phrase)`. This runs synchronously on the calling thread; if future tasks make it suspend, update this step. See Open Questions for platform caveats.
4. On success: write the returned `Wallet` into S1's `WalletSessionHolder` (via the same setter S2 uses — do not duplicate the session API here) and invoke the S1 `Navigator` to route to `Route.Home`.
5. On any `Throwable`: set `submissionError = true`, reset `isSubmitting = false`. **Do not** propagate the exception's message to the UI, and do not log the exception's `message`/`cause` — it may echo the input. If logging is desired later, log only the exception class name.
6. On any subsequent change to `input`, reset `submissionError = false`.

### Crypto hygiene (CLAUDE.md §4)

- §4.1 redaction: `ImportWalletState.toString()` redacted; no `println`, `Log.*`, or logger call anywhere under `.../flows/import/` may interpolate `input`, `words`, or the rebuilt phrase. Exception handling strips message text.
- §4.2 randomness: the import path does **not** use `kotlin.random.Random`. It does not need randomness at all.
- §4.7 address casing: not exercised in this screen, but reiterated here so downstream screens (S4) keep the invariant — any EVM address rendered must be lowercased for equality checks.

### Navigation contract (S1)

- Entry: `Navigator.navigate(Route.ImportWallet)` (route presumed defined in S1).
- Exit (success): `Navigator.replace(Route.Home)` (replace, not push — the import screen must not sit on the back stack after success).
- Exit (back): standard S1 back handling.

### Why not a ViewModel / MVI / Flow pipeline

This sample deliberately uses plain `MutableState` holders per CLAUDE.md §5 ("No DI"). Reactive frameworks would add surface area the toolkit itself does not require and would complicate the no-log guarantee.

### Open questions

1. **Mnemonic-import factory (resolved inline, but confirm):** `wallet-core` exposes `Wallet.fromMnemonicWithTrustWalletCore(mnemonic: String): Wallet` in commonMain (confirmed via inspection of `Wallet.kt`). This is the intended call site for step 3 above. However, `TrustWalletCoreWalletEngine` is implemented via the expect/actual seam — on JVM it currently throws `NotImplementedError` (CLAUDE.md §2, jvmMain specifics). The implementer should verify that running `:sample-app:assembleDebug` (Android target) exercises the real Android JNI bridge, and that `:sample-compose:compileKotlinJvm` only needs compilation (not execution) to pass AC #1. If at implementation time the Android path does not validate mnemonics synchronously (i.e. `fromMnemonicWithTrustWalletCore` defers validation until the first `deriveAddress` call), the implementer must either (a) force a trivial `deriveAddress` call inside the import action to surface an invalid phrase, or (b) raise this back to the planner rather than shipping a screen that silently accepts garbage phrases. Do **not** extend `wallet-core` from this spec.
2. **Session setter API:** S1 owns `WalletSessionHolder`. This spec assumes the same setter used by S2 (`WalletSessionHolder.set(wallet)` or equivalent) is callable from commonMain. If S1 gated writes behind a factory-specific method, the implementer consumes whatever S1 exposes — no new session API added here.
3. **`commonTest` source set for `sample-compose`:** unknown at spec time. See AC #4 guard.

## Acceptance criteria

1. `./gradlew :sample-compose:compileKotlinJvm` is green.
2. `./gradlew :sample-app:assembleDebug` is green.
3. No file outside `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/` (and, conditionally, `sample-compose/src/commonTest/kotlin/xyz/wallet/toolkit/sample/flows/import/`) is modified. In particular: no S1 file, no other flow folder, no `wallet-*` module, no `settings.gradle.kts`, no `gradle/libs.versions.toml`, no `build.gradle.kts`.
4. **Conditional test coverage.** Before adding a test file, the implementer checks `sample-compose/build.gradle.kts` (owned by S1) for an existing `commonTest` source set wiring. If present: add `MnemonicSurfaceValidationTest.kt` covering:
   - Valid 12-word input (lowercase, single-space-separated) → `Valid(words.size == 12)`.
   - Valid 24-word input → `Valid(words.size == 24)`.
   - 7-word input → `WrongWordCount(7)`.
   - 13-word input → `WrongWordCount(13)`.
   - Empty string `""` → `Empty`.
   - Whitespace-only string `"   \n\t "` → `Empty`.
   - Mixed case input → `Valid` with all-lowercase words.
   - Extra internal whitespace (double spaces, newlines) → still `Valid` with the correct word count.
   If `commonTest` is not wired: do **not** add it here (see Non-goals); flag in the PR body so a future S1-scoped task enables it.
5. Grep guard: `grep -R "println\|Log\." sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/` returns no call whose argument interpolates `input`, `words`, the rebuilt phrase, or a caught exception's `message`. (It is fine for the grep to return zero matches overall — that is the preferred outcome.)
6. `ImportWalletState.toString()` (exercised manually or via a unit test if commonTest exists) does not include the current `input` value for any non-empty input.
7. The "Restore" button is disabled for: empty input, whitespace-only input, 7-word input, 13-word input, 11-word input. Enabled for 12/15/18/21/24-word input after trim+split.
8. Tapping "Restore" with a phrase the toolkit rejects results in the generic error string and leaves the user on the Import screen. The rejected phrase is not echoed anywhere in the UI or logs.

## Non-goals

- **Private-key import, keystore import, watch-only import.** The source-picker tabs exist only as disabled UI stubs with "Coming soon" chips.
- **Embedding the BIP-39 English wordlist in `sample-compose`.** Dictionary validation is the toolkit's job.
- **Mnemonic persistence across app restarts.** Session is in-memory only; on process death the user returns to the entry screen.
- **BIP-39 passphrase (25th word) support.** No UI, no plumbing.
- **Address / balance display after import.** That is S4 (Home).
- **Session infrastructure changes.** S1 owns the session APIs; this spec consumes them as-is.
- **Extending `wallet-core`** with a new mnemonic-import entry point. If the existing one is insufficient, this spec blocks rather than grows.
- **Adding a `commonTest` source set to `sample-compose`** if S1 did not already wire one. Validation unit tests move to a follow-up in that case.
- **Instrumented UI tests** (Compose UI test rule, Paparazzi snapshots, etc.).
- **Analytics / telemetry** on the import flow.

## Why this is interesting for the experiment

This phase is the first place in the sample app where user-supplied secret material meets the toolkit. Three axes of interest for the parallel-agent experiment:

1. **Redaction discipline under autocomplete pressure.** Agents often reach for `println`/string-template debug lines when a screen misbehaves. The grep guard (AC #5) and the redacted `toString` (AC #6) measure whether agents respect §4.1 without being told per-line.
2. **Boundary between "our validation" and "toolkit's validation."** The spec deliberately forbids embedding the BIP-39 wordlist here. An agent that over-delivers (adds a wordlist constant, writes a checksum check, etc.) is a rework signal — useful data for the no-wall-clock metrics in `feedback_no_wall_clock.md`.
3. **Handling of a platform-seam landmine.** The toolkit's JVM actual for `TrustWalletCoreWalletEngine` throws `NotImplementedError`. An agent that runs only `:sample-compose:compileKotlinJvm` will pass AC #1 without noticing; an agent that also exercises `:sample-app:assembleDebug` catches the real Android path. Which agents read §2 and §3 of `CLAUDE.md` closely enough to scope the verify step correctly is exactly the kind of difference the experiment wants to see.

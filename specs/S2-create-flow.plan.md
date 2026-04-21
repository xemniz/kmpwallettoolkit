# S2 — Create-wallet flow · Implementation Plan

## Summary

Build the three-step (Intro → Reveal → Confirm) Create-wallet onboarding flow in `sample-compose` using the S1 scaffolding: `PhoneFrame`, `PrimaryButton`, `MonoText`, `WalletTheme`, `Navigator`, and `LocalWalletSession`. On a correct confirm pick, install the generated `Wallet` on `WalletSession` and `navigator.replace(Route.Home)`. The spec's approach is reasonable: the flow is self-contained in one directory, uses only S1 primitives, and is deliberate about CLAUDE.md §4 hygiene (redaction + UI-only PRNG carve-out).

**One design wrinkle flagged below (see Risks §1): S2's acceptance criterion #3 forbids touching `App.kt`, and S1's `App.kt` already routes unknown routes to `PlaceholderScreen` via an `else ->` branch. So S2 can launch-and-test without editing `App.kt` — but then `Route.Create` renders a placeholder forever and the flow is never actually reachable from the app. This plan treats that as an intentional scope choice of the spec and documents it, but I want the user's call.**

## Scope confirmation (S1 artifacts)

Confirmed by reading the merged S1 code:

- **Routes (`nav/Route.kt`)**: `Welcome`, `Create`, `Import`, `Home`, `Send(chainId)`, `TxStatus(txHash, chainId)`. `Route.Create` and `Route.Home` both exist.
- **Navigator (`nav/Navigator.kt`)**: `push`, `pop`, `replace(route)`, `current` — used via `rememberNavigator()`.
- **Session (`state/WalletSession.kt`)**: `var wallet: Wallet? by mutableStateOf(null)` plus `selectedChain`, `lastTxHash`. `toString()` is hand-rolled and redacts the wallet. CompositionLocal is `LocalWalletSession` (in `WalletSessionHolder.kt`). S2 reads/writes via `LocalWalletSession.current`.
- **UI kit**: `PhoneFrame { ColumnScope content }`, `PrimaryButton(text, onClick, modifier, enabled)`, `MonoText(text, modifier, color)`, `WalletColors.{background,surface,outline,textPrimary,textSecondary,accent}`.
- **Wallet API (wallet-core)**: `Wallet.createWithTrustWalletCore(): Wallet` (synchronous) with `val mnemonic: String` and `fun address(chain)`. Confirmed at `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/Wallet.kt`.

## App.kt dispatch delta (the load-bearing question)

`sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/App.kt` lines 45–48:

```kotlin
when (val route = navigator.current) {
    is Route.Welcome -> WelcomeScreen(navigator)
    else -> PlaceholderScreen(route, navigator)
}
```

The `else` branch swallows `Route.Create`. Until `App.kt`'s `when` gets a `is Route.Create -> CreateWalletScreen(...)` arm, the new flow is **unreachable at runtime** — tapping "Create Wallet" from Welcome pushes `Route.Create` and shows "TODO: Create".

The spec's acceptance criterion #3 explicitly forbids edits outside `flows/create/`, naming `App.kt` in the forbidden list. So literally following the spec produces code that compiles, links, and is dead on the device. Three possible resolutions (user to pick — see Risks §1):

- **(a)** S2 adds a one-line branch to `App.kt`. This is scope creep by the spec's letter but the only way the flow is actually exercised. Delta is ~2 lines plus one import.
- **(b)** Hold the line on the spec; leave `App.kt` untouched. Ship the flow behind a placeholder, integration-wire in a follow-up spec. This matches the spec's "parallel-safety check" framing — `App.kt` is exactly the hot-ish file that every flow spec would otherwise race on.
- **(c)** Revise the spec to introduce a registry (e.g. a `Map<KClass<out Route>, @Composable (Route, Navigator) -> Unit>`) so individual flows register themselves. That is genuinely out of scope for S2.

**This plan defaults to (b)** — do not edit `App.kt`. Under (b), the acceptance test gates (`:sample-compose:assemble`, `:sample-app:assembleDebug`) pass and criterion #3 holds. Manual smoke-reach of the flow is blocked until a follow-up. If the user picks (a), add a Phase 3a below.

## Files to create / modify

All paths are under `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/create/` (source set: **commonMain**, `sample-compose` module). No `androidMain`/`iosMain` files — the flow is pure Compose + Kotlin.

| Path | Kind | Purpose |
|---|---|---|
| `flows/create/CreateWalletScreen.kt` | **new** | Top-level `@Composable fun CreateWalletScreen(navigator: Navigator)`. Pulls `LocalWalletSession.current`, holds a `remember { CreateWalletState() }`, renders `PhoneFrame` with a `Crossfade` between `IntroStep`, `RevealStep`, `ConfirmStep`. Hosts `LaunchedEffect(state.confirmed)` that writes `session.wallet = state.wallet` and calls `navigator.replace(Route.Home)` exactly once. |
| `flows/create/CreateWalletState.kt` | **new** | Plain class with `wallet`, `revealed`, `confirmIndex`, `confirmOptions`, `confirmed`, `error` as `by mutableStateOf(...)`; a method `prepareConfirm(mnemonic: String)` that splits words and draws index + 3 decoys via `kotlin.random.Random` (UI-only carve-out — see Phase 2); redacted `toString()`. |
| `flows/create/MnemonicRevealCard.kt` | **new** | `@Composable fun MnemonicRevealCard(mnemonic: String, revealed: Boolean, onReveal: () -> Unit, modifier: Modifier)`. 3-column word grid via `MonoText`, frosted overlay (semi-opaque `Box` with tap handler) until `revealed`. |
| `flows/create/ConfirmWordStep.kt` | **new** | `@Composable fun ConfirmWordStep(targetIndexOneBased: Int, options: List<String>, onPick: (String) -> Unit, errorHint: String?, modifier: Modifier)`. Four option chips; calls `onPick` with the tapped word. |

No edits to `App.kt`, `nav/`, `state/`, `theme/`, `ui/`, or any `wallet-*` module (per default resolution (b) above). No `gradle/libs.versions.toml` or `settings.gradle.kts` edits — all deps are already on `sample-compose`'s classpath via S1.

Hot-file check (CLAUDE.md §7): none of `libs.versions.toml`, `settings.gradle.kts`, `ChainRegistry.kt`, `Chain.kt`, `CLAUDE.md`, or `.claude/agents/*` is touched. `App.kt` is not listed in §7 but it is effectively hot for the sample-app parallel runs — another reason to default to (b).

## Phases

Each phase ends at a state where the per-module gate passes:
```
./gradlew :sample-compose:assemble
./gradlew :sample-app:assembleDebug
```
(Per CLAUDE.md §3, the module gate for `sample-compose` is `assemble` — there is no test source set wired for this module; no `allTests` equivalent. The spec's acceptance criteria name exactly these two gradle commands.)

### Phase 1 — State holder + redaction (compile-green)

1. Create `CreateWalletState.kt` with all `mutableStateOf` fields from the spec and a redacted `toString()` (`hasWallet=...`, `revealed`, `confirmed`, `error` only — no mnemonic, no options).
2. Create stub `MnemonicRevealCard.kt` and `ConfirmWordStep.kt` (signatures only, `TODO()` or simple placeholders using S1 primitives so the module still compiles).
3. Create `CreateWalletScreen.kt` with the `Navigator` parameter, `LocalWalletSession.current` read, `remember { CreateWalletState() }`, and a `PhoneFrame` rendering just the Intro step shell (title + `PrimaryButton` wired to a no-op).
4. Gate: `./gradlew :sample-compose:assemble` green.

### Phase 2 — Intro → Reveal → Confirm logic (flow works end-to-end)

1. Intro step: on button press, `remember { runCatching { Wallet.createWithTrustWalletCore() } }` and store the result (or `state.error = "Wallet creation failed"` on `isFailure`). Do **not** surface `throwable.message` anywhere (CLAUDE.md §4.1; spec §6).
2. On success, call `state.prepareConfirm(wallet.mnemonic)`. `prepareConfirm` splits by whitespace, validates length is 12 (if not, set `error = "Wallet creation failed"` and clear `wallet`), picks `confirmIndex = Random.nextInt(0, words.size)`, builds `confirmOptions` as the correct word + 3 distinct decoys, `.shuffled()`. Mnemonic words themselves never hit a log. Inline comment at the `Random` usage quoting CLAUDE.md §4.2: "UI-only confirmation over already-revealed words; not entropy, nonce, salt, IV, or key material." This is option (b) from the spec.
3. Reveal step: `MnemonicRevealCard` blurs behind a tap-target `Box` (alpha-fade `Box` over `Column` of 12 numbered `MonoText` rows in a 3-column `Row`/`FlowRow` layout). `PrimaryButton("I wrote it down")` `enabled = state.revealed`, advances to confirm.
4. Confirm step: `ConfirmWordStep` prompts `"Which was word #${state.confirmIndex + 1}?"`, four chips; `onPick` compares against `words[confirmIndex]`. Correct → `state.confirmed = true`. Incorrect → `state.error = "Try again"` (challenge is NOT regenerated, per spec §Design.3).
5. Commit: `LaunchedEffect(state.confirmed) { if (state.confirmed) { session.wallet = state.wallet!!; navigator.replace(Route.Home) } }`. Key is `confirmed` so the effect runs once on false→true.
6. Gate: `./gradlew :sample-compose:assemble` green; `./gradlew :sample-app:assembleDebug` green.

### Phase 3 — Visual polish + redaction audit

1. Classic direction: single column, `WalletColors.surface` cards, `WalletColors.textSecondary` subheads, accent only on `PrimaryButton`. `Crossfade` between steps (`animateContentSize` acceptable; no custom animation library).
2. Audit — grep-equivalents to run mentally before handoff:
   - no `println`, `Log.`, `Napier`, `Timber`, `System.out` in `flows/create/`;
   - no `.toString()` call on `Wallet` anywhere in `flows/create/`;
   - `CreateWalletState.toString()` does not reference `wallet.mnemonic`, `confirmOptions`, or words;
   - exception from `createWithTrustWalletCore()` is caught and discarded — `throwable.message` is never rendered, logged, or assigned to `state.error`.
3. Gate: both gradle assembles green.

### Phase 3a (only if user picks resolution (a)) — wire into App.kt

1. In `App.kt`, insert before the `else` branch: `is Route.Create -> CreateWalletScreen(navigator)` and add the import for `xyz.wallet.toolkit.sample.flows.create.CreateWalletScreen`. Two-line delta, no behavior change for other routes.
2. Re-run both assembles.

(Skip this phase if defaulting to (b). Do not edit `App.kt` without user go-ahead.)

## Golden vectors

Not applicable. S2 does no signing, no serialization of a signing payload, and no key derivation — `Wallet.createWithTrustWalletCore()` is invoked as an opaque factory and its output is not compared byte-for-byte. CLAUDE.md §4.5 applies to the signing flow (S5), not this spec. No test vectors are needed or appropriate.

## Crypto hygiene (CLAUDE.md §4 checklist)

- **§4.1 No log/toString of secrets.** `CreateWalletState.toString()` is overridden as `"CreateWalletState(hasWallet=${wallet != null}, revealed=$revealed, confirmed=$confirmed, error=$error)"`. No `println`/`Log.*` in `flows/create/`. `wallet.toString()` is never called. The exception from `createWithTrustWalletCore()` is caught; `throwable.message` is dropped; `state.error` is the flat string `"Wallet creation failed"`.
- **§4.2 SecureRandom.** The only PRNG usage in S2 is `kotlin.random.Random` for the confirm-word index and decoy shuffle. This is the spec's deliberate option (b) carve-out: the input set is 12 words already held in memory and already shown to the user; the output is UX, not entropy. A short inline comment at each `Random` call references §4.2 and restates the carve-out rationale. No `secureRandomBytes` expect is introduced.
- **§4.3 Constant-time comparisons.** The confirm check is `picked == words[confirmIndex]` — plain-text UI equality, not a secret comparison. `String.==` is fine here.
- **§4.4 No `Random` for nonces/nonces-of-any-kind.** Not applicable — S2 touches no nonces, no request IDs tied to signing, no transaction nonces.
- **§4.5 Golden vectors for signing.** Not applicable — S2 does not sign.
- **§4.6 Never weaken an existing assertion.** No existing tests to weaken; S2 adds none.
- **§4.7 Address case.** Not applicable — no address comparison in S2.
- **§4.8 Signing-input serialization.** Not applicable.

## Risks / Open questions

1. **App.kt dispatch (highest-priority).** As above. Default plan: do NOT edit `App.kt`; accept that `Route.Create` renders `PlaceholderScreen` until a follow-up. Alternative: take the two-line App.kt edit. **Needs user decision before Phase 2 is useful on-device.**
2. **Exception surface of `createWithTrustWalletCore()`.** The factory is synchronous and may throw `NotImplementedError` on JVM (`wallet-core` JVM stubs per CLAUDE.md §2.jvmMain) and platform-specific errors on Android/iOS. Wrapping in `runCatching` on the compose thread is fine for JVM builds; on Android the first-ever `HDWallet` JNI load can be slow (tens of ms) but not blocking-dangerous here. No move to a coroutine is planned — spec says `runCatching { ... }` on the main path. Flagging because if the JVM assemble hits the `NotImplementedError` at composition time that would be visible in tooling previews — but not in the `:sample-compose:assemble` gate.
3. **Mnemonic word count.** `TrustWalletCoreWalletEngine.createMnemonic()` produces 12 words in practice, but the spec codes `confirmIndex ∈ [0, 11]` as a hard assumption. Plan guards via `if (words.size != 12) state.error = "Wallet creation failed"` rather than throwing.
4. **"Try again" reset.** Spec says "do not regenerate the challenge". Plan respects that. It does mean a user who taps wrong three times sees the same four options — acceptable per spec reasoning (all options come from the same mnemonic, so no information leak).
5. **`PhoneFrame` is fixed 9:16 aspect-ratio.** Three-step content must fit; this plan uses a `Crossfade` so only one step is laid out at a time, keeping vertical budget predictable.

## What this plan does NOT do

- Does not edit `App.kt`, `Route.kt`, `Navigator.kt`, `WalletSession.kt`, `WalletSessionHolder.kt`, or any file in `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/` or `.../theme/`.
- Does not edit any `wallet-*` module, `settings.gradle.kts`, `gradle/libs.versions.toml`, `sample-compose/build.gradle.kts`, `gradle.properties`, or `gradle-daemon-jvm.properties`.
- Does not introduce a ViewModel, coroutines scope owner, DI framework (Koin/Hilt), navigation library (Voyager/Decompose), or persistence (Keystore/Keychain).
- Does not add tests. `sample-compose` has no configured test source set; the spec's gates are `assemble`/`assembleDebug` only. If a test source set is desired, it is a separate infra task.
- Does not implement Import (S3), Home/balance (S4), Send/sign (S5), or TxStatus (S6).
- Does not add iOS source sets or actuals — `sample-compose` is Android + JVM host of Compose Multiplatform as configured; no new `expect`/`actual` is introduced.
- Does not persist the mnemonic. It lives in `WalletSession` and dies on process death, per spec §Non-goals.
- Does not use `secureRandomBytes` or add an `expect` for randomness — the PRNG path is UI-only and documented inline.
- Does not render `throwable.message` from `createWithTrustWalletCore()` under any condition.

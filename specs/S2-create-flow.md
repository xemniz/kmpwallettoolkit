# S2 — Create-wallet flow (direction A · Classic)

## Goal

Implement the onboarding Create-wallet flow for the sample Compose app. Generate a new mnemonic via `Wallet.createWithTrustWalletCore()`, show it behind a tap-to-reveal overlay, verify the user captured it with a single random-word confirmation, then install the wallet on `WalletSession` and route to `Route.Home`. Visual direction: Classic — single column, stock card layout, subdued palette, matching the wireframe's direction A.

## Module(s) touched

`sample-compose` only. No toolkit module (`wallet-core`, `wallet-evm`, `wallet-rpc`, `wallet-utils`) is edited. No `settings.gradle.kts`, no `gradle/libs.versions.toml`, no `sample-compose/build.gradle.kts`.

## Files expected to change

- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/create/CreateWalletScreen.kt` **(new)** — top-level `@Composable` for the flow. Consumes the `WalletSession` from S1's `WalletSessionHolder` CompositionLocal and the S1 `Navigator`. Owns the step-state machine (Intro → Reveal → Confirm), wraps content in `PhoneFrame`, uses `PrimaryButton` / `MonoText` from S1.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/create/CreateWalletState.kt` **(new)** — plain state holder class with `mutableStateOf` fields. No ViewModel framework, no coroutines beyond Compose's. Exposes an `override fun toString()` that redacts the mnemonic.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/create/MnemonicRevealCard.kt` **(new)** — blurred-until-tap reveal composable. Takes `mnemonic: String`, `revealed: Boolean`, `onReveal: () -> Unit`. Renders 12 words in a 3-column grid; overlay blocks the text until tap.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/create/ConfirmWordStep.kt` **(new)** — pick-the-Nth-word sub-screen. Takes the target index, the correct word, four shuffled options, and `onPick: (String) -> Unit`.

## Design

**Screen flow.**

1. **Intro card** — welcome copy ("Create a new wallet"), short security note, `PrimaryButton("Create new wallet")`. Tapping calls `Wallet.createWithTrustWalletCore()` inside a `remember { runCatching { ... } }`. On success the returned `Wallet` is stored in `CreateWalletState.wallet`; on failure, `state.error = "Wallet creation failed"` and the button becomes a retry. The exception's `message` is **not** propagated — toolkit internals may reference mnemonic-derived data, and we prefer a flat redacted string per CLAUDE.md §4.1.
2. **Reveal step** — `MnemonicRevealCard` shows a frosted overlay reading "Tap to reveal your recovery phrase". Tapping flips `state.revealed = true` and unblurs the word grid. A `PrimaryButton("I wrote it down")` enables only when `revealed` is true and advances to step 3. Words render via `MonoText` (S1) numbered 1…12.
3. **Confirm step** — on first entry, the state holder picks a random word index `confirmIndex ∈ [0, 11]` and assembles `confirmOptions`: the correct word plus three other distinct words from the same mnemonic, shuffled. `ConfirmWordStep` prompts "Which was word #${confirmIndex + 1}?" with four tappable option chips. Correct pick → `state.confirmed = true`; incorrect pick → stay on the step, surface a subdued "Try again" hint, do not regenerate the challenge (prevents guess-spam noise but doesn't leak information — all four options are drawn from the same mnemonic).
4. **Commit** — once `confirmed`, the `LaunchedEffect(confirmed)` in `CreateWalletScreen` sets `walletSession.wallet = state.wallet!!` and calls `navigator.replace(Route.Home)`. The `LaunchedEffect` key is `confirmed` so the effect fires exactly once.

**`CreateWalletState` shape** (all fields `by mutableStateOf`):

- `wallet: Wallet?` — the created wallet, held in memory only.
- `revealed: Boolean` — whether the mnemonic overlay has been tapped.
- `confirmIndex: Int` — which mnemonic word the user must identify (1-based in UI, 0-based in field).
- `confirmOptions: List<String>` — four words to choose from, already shuffled.
- `confirmed: Boolean` — set true on a correct pick.
- `error: String?` — a flat, redacted failure label ("Wallet creation failed"). Never a raw exception message.

`toString()` is overridden to return `"CreateWalletState(hasWallet=${wallet != null}, revealed=$revealed, confirmed=$confirmed, error=$error)"` — the mnemonic and confirm word options are **not** included. Per CLAUDE.md §4.1, nothing in this module calls `println`, `Log.*`, or includes the mnemonic in any exception message.

**Randomness for the confirm challenge.** The confirm-word index and the three decoy words are chosen using `kotlin.random.Random` with a carve-out comment documenting why this is acceptable per CLAUDE.md §4.2: the selection happens over a set of 12 words that are already held in memory and already revealed to the user; the purpose is UX confirmation, not entropy generation. No key material, nonce, salt, or IV flows through this path. The inline comment must explicitly reference §4.2 and explain the carve-out. This choice is option (b) from the spec brief.

**Classic visual direction.** Single-column layout inside `PhoneFrame`. One card per step, large typography headline, subdued accent color from `WalletTheme`. No animations beyond a simple cross-fade on step transitions and an alpha fade on the reveal overlay. No dark-mode-specific work — defer to `WalletTheme`.

## Acceptance criteria

1. `./gradlew :sample-compose:compileKotlinJvm` is green.
2. `./gradlew :sample-app:assembleDebug` is green (the Android host app compiles and links against the new flow).
3. No file outside `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/create/` is created or modified by this spec. In particular: `App.kt`, `nav/`, `theme/`, `ui/`, `state/`, `rpc/`, `format/`, other `flows/*` directories, and all toolkit modules remain untouched.
4. `grep -R "println.*mnemonic\|log.*mnemonic" sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/create/` returns zero matches. `CreateWalletState.toString()` does not include the mnemonic string or its derived words.
5. The confirm step blocks navigation on an incorrect pick: `confirmed` remains `false`, `state.wallet` is not written to `WalletSession`, `navigator.replace(Route.Home)` is not called. Only a correct pick advances.
6. `Wallet.createWithTrustWalletCore()` is invoked exactly once per Intro → Reveal transition (wrapped in `remember`), and its thrown exception — if any — is converted to the flat `"Wallet creation failed"` string without the exception message being rendered anywhere.

## Non-goals

- Navigation host, `Route` definitions, `Navigator` implementation — owned by **S1**.
- `PhoneFrame`, `PrimaryButton`, `MonoText`, `WalletTheme` — owned by **S1**. S2 consumes, never edits.
- `WalletSession` and `WalletSessionHolder` CompositionLocal — owned by **S1**.
- Import-from-mnemonic flow and BIP-39 wordlist validation — owned by **S3**.
- Fetching and displaying balances — owned by **S4**.
- Signing transactions (EIP-1559) — owned by **S5**.
- Transaction status polling — owned by **S6**.
- Persistent storage of the mnemonic (Keystore / Keychain). The sample is demo-only; the mnemonic lives in `WalletSession` for the session lifetime and is dropped on process death. A real app would encrypt and persist it; that is out of scope for this spec set.
- No new Gradle dependency, no new module, no change to the toolkit's public API.

## Why this is interesting for the experiment

- **Parallel-safety check.** S2 is the first flow that sits entirely inside a dedicated directory. If the spec's forbidden-edits list holds, S2 can run in parallel with S3/S4/S5/S6 with zero hot-file contention — a clean test of the parallel-agent scaffolding.
- **Crypto-discipline signal.** The mnemonic is the single most sensitive artifact in the app. Whether the implementer avoids `println`, redacts `toString()`, and correctly invokes the §4.2 carve-out for the UI-only PRNG is a direct, gradable signal on whether CLAUDE.md is being internalized.
- **Expect/actual temptation.** A naive implementer may reach for `expect fun secureRandomBytes` to pick a word index. The spec asking them to deliberately *not* do that (and to document why) is a good probe of judgement — the rule isn't "always use SecureRandom", it's "use SecureRandom for security material".
- **Redacted error contract.** Choosing a flat failure string over the raw `Throwable.message` is a tiny design decision with an outsized correctness impact. Whether the agent picks it up from the spec without further prompting is a useful data point.

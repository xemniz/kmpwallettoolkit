# S6 — Tx-status flow: pending → confirmed

## Goal

Show the post-broadcast lifecycle of a transaction submitted from S5. Given a `(txHash, chainId)` pair delivered via navigation, poll `eth_getTransactionReceipt` on a cadence, render a muted pending state with speed-up/cancel UI stubs, and on confirmation transition to a celebration + timeline receipt view. On reverted status surface a failed state. On long silence stay pending with an explanatory subtext. Navigating away cancels polling cleanly.

This is the final screen in the Classic-A showcase slice (S1→S6). No real speed-up or cancel is implemented — those are permanent UI stubs for this showcase.

## Module(s) touched

- `sample-compose` — UI only. No `wallet-*` module is touched.

Per CLAUDE.md §1 and §7 this spec stays entirely inside `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/`. Hot files (`settings.gradle.kts`, `gradle/libs.versions.toml`, `ChainRegistry.kt`, `Chain.kt`, `CLAUDE.md`, `.claude/agents/*`) are out of scope.

## Files expected to change

All new, all under `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/`:

- `TxStatusScreen.kt` — screen composable; reads `(txHash, chainId)` from `Route.TxStatus`, resolves `RpcClientFactory.forChain(chain)`, hosts state, dispatches to `PendingState` / `ConfirmedState` / failure branch, wraps everything in `PhoneFrame` + `WalletTheme`.
- `TxStatusState.kt` — sealed class:
  - `Pending(elapsedSeconds: Int, stalled: Boolean)` — `stalled=true` means we crossed the 5-minute soft-timeout.
  - `Confirmed(receipt: TransactionReceipt)`
  - `Failed(reason: String)`
- `PendingState.kt` — composable rendering the pending card: animated "…" dots, elapsed clock, shortened tx hash (`MonoText`), `ChainChip`, stubbed `Speed up` and `Cancel` buttons (both show a "Coming soon" snackbar/toast on tap).
- `ConfirmedState.kt` — composable rendering the confirmed card: checkmark glyph, small accent moment, block number (decoded from `receipt.blockNumber` hex), shortened tx hash, `PrimaryButton("View on Home")` → `Navigator.replace(Route.Home)`.
- `TxReceiptTimeline.kt` — composable showing three vertical steps: `Submitted` (always filled), `Pending` (filled while `Pending`, check-marked on `Confirmed`), `Included in block N` (filled on `Confirmed`, shows block number). Used by both `PendingState` and `ConfirmedState`.
- `TxPollingEffect.kt` — `@Composable fun TxPollingEffect(txHash: String, rpc: RpcClient, onState: (TxStatusState) -> Unit)` wrapping a `LaunchedEffect(txHash)` that runs the polling loop described in Design and cancels on leave-composition.

No other files are created or edited.

## Design

### Navigation + inputs

`Route.TxStatus(txHash: String, chainId: ...)` is defined in S1 (or an earlier phase). `TxStatusScreen` reads both arguments, looks up the `Chain` via the registry exposed by S1, and obtains a `RpcClient` via `RpcClientFactory.forChain(chain)`. Nothing secret flows through: tx hash and receipt are public (§4).

### State machine

```
Pending(elapsed=0, stalled=false)
      │  receipt == null, elapsed ≤ 300s
      ▼
Pending(elapsed=n, stalled=false)   ── elapsed > 300s ──▶  Pending(elapsed=n, stalled=true)
      │
      │ receipt != null, status indicates success
      ▼
Confirmed(receipt)                                     ── terminal
      │
      │ receipt != null, status indicates revert
      ▼
Failed("Transaction reverted")                         ── terminal
```

`TxStatusState` is a `sealed class`. `Confirmed` wraps a non-null `TransactionReceipt` — the null-check happens in `TxPollingEffect`, so no nullable-receipt shape leaks into UI branches (acceptance criterion 6).

### Polling loop (`TxPollingEffect`)

Inside `LaunchedEffect(txHash)`:

- Track `startedAt` via `kotlinx.datetime.Clock.System.now()` — no `System.currentTimeMillis()` (§2), no `kotlin.random.Random` (§4.2).
- Loop:
  1. `val receipt = rpc.getTransactionReceipt(txHash)`.
  2. If `receipt != null`:
     - Decode `receipt.status`. Per `JsonRpcModels.TransactionReceipt` the field is a `String` (hex-quantity, `"0x1"` for success, `"0x0"` for revert). Treat `"0x1"` as success, `"0x0"` as revert. Any other value → stay pending (defensive; forward-compatibility with EIP-658 pre-Byzantium nodes is not a goal here).
     - On success → emit `Confirmed(receipt)` and `return@LaunchedEffect` (ends the loop; composable stays mounted for the celebration render).
     - On revert → emit `Failed("Transaction reverted")` and `return@LaunchedEffect`.
  3. If `receipt == null`:
     - Compute elapsed in whole seconds from `startedAt`.
     - Emit `Pending(elapsed, stalled = elapsed > 300)`.
     - `delay(nextBackoff)`.
- **Backoff schedule:** start at `3_000L` ms, multiply by `1.5` on each empty result, cap at `15_000L` ms. This matches the spec's "every few seconds" with exponential backoff to 15s. After the 5-minute soft-timeout we keep polling at the capped 15s cadence; we do **not** stop the loop — acceptance criterion 5 calls out that we stay in `Pending` with a "still waiting — network may be congested" subtext rather than crashing or ending.
- **Hard ceiling:** none. The screen may sit in `Pending(stalled=true)` indefinitely until the user backs out. Cancellation is handled by structured concurrency — see below.
- **Cancellation:** `LaunchedEffect(txHash)` is scoped to the composable. When the user navigates back (`Route.Home` via back-stack or `Navigator.replace`), the effect's coroutine is cancelled by Compose; the `delay` is cancellable, and `rpc.getTransactionReceipt` is a cooperative `suspend fun`. No `GlobalScope`, no `rememberCoroutineScope().launch` that outlives composition. Acceptance criterion 4.

### Rendering

Classic-A direction (per wireframe):

- Single-column layout inside `PhoneFrame`.
- Muted palette (greys, low-saturation accent) while `Pending` — the screen should feel calm, not urgent.
- On transition to `Confirmed`, a small accent moment: checkmark glyph in accent colour, one-shot (no looping animation required — a static rendered check is acceptable for the showcase).
- `TxReceiptTimeline` is the spine of both states. Three rows, connected by a vertical line:
  1. **Submitted** — filled always.
  2. **Pending** — filled while `Pending`, replaced with a check on `Confirmed`.
  3. **Included in block N** — unfilled while `Pending`, filled on `Confirmed` with the decoded block number (`receipt.blockNumber.removePrefix("0x").toLong(16)`).
- Shortened tx hash uses `MonoText` — first 6 + last 4 chars with ellipsis.
- Addresses in the receipt, if rendered, are lowercased per §4.7 (S4 convention). For S6 we only need the receipt's `from`/`to` if the timeline or confirmed card chooses to show them — keep them lowercase.

### Pending UI details

- Animated dots: three dots fading in sequence via `rememberInfiniteTransition` + `animateFloat`. No random timing.
- Elapsed time rendered as `mm:ss`.
- Tx hash row with copy-to-clipboard via the S1 helper if one exists; otherwise plain text (do not introduce new helpers — out of scope).
- `Speed up` and `Cancel` buttons call a local `showStubToast("Coming soon")` — a small `AnimatedVisibility` snackbar inside the screen; no new navigation target. These are permanent UI-only stubs (see Open questions).
- When `Pending.stalled == true`, show an italic subtext under the dots: `"Still waiting — the network may be congested."`

### Confirmed UI details

- Checkmark + short headline (e.g. `"Confirmed"`).
- `Included in block #N` line inside the timeline.
- `PrimaryButton("View on Home")` → `Navigator.replace(Route.Home)` so the back-stack doesn't strand the tx-status screen behind Home.

### Failed UI details

- Simple error card with the `reason` string. No retry button in this showcase. `PrimaryButton("Back to Home")` → `Navigator.replace(Route.Home)`.

### What this spec imports from S1 (read-only)

`Navigator`, `Route.Home`, `Route.TxStatus`, `WalletSession`, `WalletTheme`, `PhoneFrame`, `PrimaryButton`, `ChainChip`, `MonoText`, `RpcClientFactory`, `EthFormat`. None of these files are edited.

## Acceptance criteria

1. `./gradlew :sample-compose:compileKotlinJvm` — green.
2. `./gradlew :sample-app:assembleDebug` — green.
3. No file outside `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/` is modified. `git diff --name-only` on the implementer's branch must match this directory prefix only.
4. Polling cancellation: leaving the screen via back-nav cancels the `LaunchedEffect`; no coroutine survives composition exit. Verified by code review — the polling lives inside `LaunchedEffect(txHash)` with no escape (`GlobalScope`, detached `CoroutineScope`, or `rememberCoroutineScope().launch` that outlives the screen are grounds for rejection).
5. Timeout behaviour: after 5 minutes without a receipt, state is `Pending(stalled = true)` and the UI shows the "still waiting — network may be congested" subtext. No crash, no exception, no loop exit.
6. `TxStatusState` is a `sealed class` (or `sealed interface`). `Confirmed` carries a non-null `TransactionReceipt`; the UI branches never see a nullable receipt.
7. No `kotlin.random.Random` usage anywhere in the new files (§4.2). Grep for it as part of review.
8. No `println`, `Log.d`, or other logging of the tx hash or receipt in production code paths. The tx hash is public, but the house style (§4.1) is to avoid stdout noise in showcase flows.
9. Addresses rendered from the receipt (if any) are normalised to lowercase (§4.7).
10. `Navigator.replace(Route.Home)` is used on the confirmed and failed CTAs — not `Navigator.push` — so the tx-status screen is not left in the back-stack.

## Non-goals

- **Actual speed-up implementation.** No replacement tx construction, no gas-bump logic. UI stub only.
- **Actual cancel implementation.** No zero-value self-send, no nonce replacement. UI stub only.
- **Broadcast / sign.** That belongs to S5. S6 assumes the hash already exists on-chain (or will shortly).
- **Receipt caching or tx history.** No persistence layer. If the user leaves and returns, polling restarts from scratch — there is no "was this already confirmed?" lookup.
- **Push or background notification on confirmation.** Foreground polling only; if the app is backgrounded, Compose will pause the effect and the user sees the result when they return.
- **Mempool introspection.** No `txpool_*` RPC. We can't tell whether a tx is dropped vs. pending; we just keep polling.
- **Failure-reason decoding beyond the status bit.** `status == "0x0"` → generic `"Transaction reverted"`. No `debug_traceTransaction`, no revert-reason ABI decode.
- **New chains, new RPC methods, new wallet-core APIs.** Everything routes through existing S1/S5 surface area.
- **iOS-specific behaviour.** `sample-compose` is Compose Multiplatform but this showcase targets the JVM/Android preview path per CLAUDE.md §1 table. iOS parity is out of scope for this spec.

## Open questions

- **Receipt status field type.** `JsonRpcModels.TransactionReceipt.status` is declared as a non-null `String` (hex-quantity). Implementation should treat `"0x1"` as success, `"0x0"` as revert, and any other value as "keep polling" (defensive). If a real node ever returns something else we'd rather keep showing pending than crash.
- **Speed-up / Cancel future.** Are these slated for a future spec set, or permanent UI-only stubs? **Answer for this showcase: treat as permanent stubs.** A follow-up spec set (S7+) can replace them.
- **Toast/snackbar primitive.** If S1 didn't ship a shared snackbar host, the "Coming soon" feedback can be inline `AnimatedVisibility` text under the button — do not introduce a new shared primitive as part of S6.

## Why this is interesting for the experiment

S6 is the only phase that combines (a) a long-running `suspend` loop tied to a Compose lifecycle, (b) a sealed-class state machine with a soft-timeout sub-state, and (c) a hard no-op constraint ("speed-up and cancel are stubs — don't implement them"). It's a stress test for whether the parallel-agent harness can:

1. Hold the non-goal line under pressure. A free-form agent will be tempted to "implement speed-up properly" because the RPC surface is right there. The spec's explicit non-goal + the CLAUDE.md "don't widen scope" rule should suffice, but this is the phase most likely to produce rework if an agent overreaches.
2. Keep the cancellation contract. The wrong shape here (a `rememberCoroutineScope().launch` that outlives composition, or a `GlobalScope.launch`) passes compile and looks fine in a screenshot, but leaks on every back-nav. The baseline vs. parallel run can be compared on whether reviewers catch this without a runtime test harness.
3. Respect the file-scope envelope. S6 is downstream of S1 and has zero legitimate reason to edit any wallet-* module or hot file. If an agent edits `RpcClient.kt` to "add a polling helper," that's a scope violation worth logging per the experiment's intervention-count metric.

No new crypto surface, no new signing path, no golden vectors — this is a UI/lifecycle phase. The CLAUDE.md §4 hygiene rules apply trivially (tx hash and receipt are public), which makes S6 a good control for measuring rework driven purely by scope discipline and lifecycle correctness rather than crypto correctness.

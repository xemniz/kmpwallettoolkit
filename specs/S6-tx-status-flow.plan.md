# S6 — Tx-status flow — Implementation Plan

## 1. Summary

S6 adds the final screen of the Classic-A showcase slice: a `TxStatusScreen` that polls `eth_getTransactionReceipt` for a given `(txHash, chainId)`, renders a muted pending timeline with UI-only "Speed up" / "Cancel" stubs, and transitions to a confirmed or failed terminal state. The spec's approach (sealed state machine + `LaunchedEffect`-scoped polling with 3s→15s backoff, no crypto, no new RPC surface) is reasonable and matches the existing `wallet-rpc` public API. One scope issue surfaces immediately: routing `Route.TxStatus` requires an edit to `App.kt`, which the spec does not list in "Files expected to change" (see §5 Risks).

## 2. Verified facts from code reading

Absolute paths read: `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/wallet-rpc/src/commonMain/kotlin/xyz/wallet/toolkit/rpc/JsonRpcModels.kt`, `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/wallet-rpc/src/commonMain/kotlin/xyz/wallet/toolkit/rpc/RpcClient.kt`, `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/App.kt`, `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/nav/Route.kt`, `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/nav/Navigator.kt`, `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/rpc/RpcClientFactory.kt`.

- `TransactionReceipt.status: String` — non-null hex quantity (`"0x1"` success, `"0x0"` revert). `blockNumber: String` (hex). `from: String` non-null, `to: String?` nullable (contract creation). Spec's assumptions hold.
- `RpcClient.getTransactionReceipt(txHash: String): TransactionReceipt?` — nullable while pending. Suspend. Good.
- `Route.TxStatus(txHash: String, chainId: Long)` — already declared in S1. `chainId` is `Long`, so `RpcClientFactory.forChain(...)` needs `ChainRegistry.byId(chainId)` to resolve to `SupportedChain`.
- `Navigator.replace(route)` exists and resets stack. Good.
- `App.kt` currently dispatches only `Route.Welcome`; all other routes fall to `PlaceholderScreen`. Reaching `TxStatusScreen` requires an `App.kt` branch.

## 3. Files to create / modify

All under `sample-compose` (commonMain source set — this module has no iOS targets per CLAUDE.md §1, but commonMain compiles to both JVM and Android). No `wallet-*` module is touched.

New files, source set `sample-compose/src/commonMain`:

- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/TxStatusState.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/TxPollingEffect.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/TxReceiptTimeline.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/PendingState.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/ConfirmedState.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/TxStatusScreen.kt`

Modify (flagged — see §5):

- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/App.kt` — add a single `is Route.TxStatus -> TxStatusScreen(route, navigator)` branch in the existing `when`.

Hot-file check (CLAUDE.md §7): none. `gradle/libs.versions.toml`, `settings.gradle.kts`, `ChainRegistry.kt`, `Chain.kt`, `CLAUDE.md`, `.claude/agents/*` — untouched.

## 4. Phases

Each phase ends at a green `./gradlew :sample-compose:assemble && ./gradlew :sample-app:assembleDebug` (the spec's test gate — there is no unit-test surface for this UI module; acceptance criteria 1 and 2 are assemble-only).

### Phase 1 — State + polling skeleton (no UI yet)

1. Create `TxStatusState.kt` as a `sealed class` with `Pending(elapsedSeconds: Int, stalled: Boolean)`, `Confirmed(receipt: TransactionReceipt)`, `Failed(reason: String)`. Import `xyz.wallet.toolkit.rpc.TransactionReceipt`.
2. Create `TxPollingEffect.kt`:
   - Signature: `@Composable fun TxPollingEffect(txHash: String, rpc: RpcClient, onState: (TxStatusState) -> Unit)`.
   - Body: `LaunchedEffect(txHash) { ... }`.
   - Start time via `kotlinx.datetime.Clock.System.now()`. Elapsed in whole seconds.
   - Loop: call `rpc.getTransactionReceipt(txHash)`. Branch on null/`status`:
     - `"0x1"` → emit `Confirmed(receipt)`, `return@LaunchedEffect`.
     - `"0x0"` → emit `Failed("Transaction reverted")`, `return@LaunchedEffect`.
     - else (null or unknown string) → emit `Pending(elapsed, stalled = elapsed > 300)`, `delay(nextBackoff)`.
   - Backoff: start `3_000L` ms, multiply by `1.5` after every empty poll, cap at `15_000L` ms. Reset on state change is unnecessary — loop either terminates or stays in pending.
   - No `GlobalScope`, no `rememberCoroutineScope().launch`. Structured concurrency only. (Acceptance 4.)
   - No `kotlin.random.Random`. (Acceptance 7, CLAUDE.md §4.2.)
   - No logging of `txHash` or `receipt`. (Acceptance 8, CLAUDE.md §4.1.)
3. Gate: `./gradlew :sample-compose:assemble` — green. Files compile in isolation.

### Phase 2 — Rendering components

1. `TxReceiptTimeline.kt` — composable taking `state: TxStatusState`. Three vertical rows (Submitted / Pending / Included in block N) with a connecting vertical line. Block number decoded via `receipt.blockNumber.removePrefix("0x").toLong(16)` when `state is Confirmed`.
2. `PendingState.kt` — composable taking `state: TxStatusState.Pending`, `txHash: String`, `chain: SupportedChain`. Renders:
   - `TxReceiptTimeline(state)`.
   - Animated dots via `rememberInfiniteTransition` + `animateFloat` (no random timing).
   - Elapsed clock formatted `mm:ss`.
   - Shortened tx hash via `MonoText` (first 6 + last 4 chars + ellipsis).
   - `ChainChip(chain)`.
   - Two outlined buttons `Speed up` / `Cancel` → local `showStubToast("Coming soon")` via `AnimatedVisibility` inline (no new shared primitive per spec Open Q).
   - When `state.stalled`, italic subtext `"Still waiting — the network may be congested."`.
3. `ConfirmedState.kt` — composable taking `receipt: TransactionReceipt`, `navigator: Navigator`. Renders checkmark glyph, headline `"Confirmed"`, `TxReceiptTimeline` with the confirmed receipt, shortened tx hash, and `PrimaryButton("View on Home") { navigator.replace(Route.Home) }`. Any address rendered (`receipt.from`, `receipt.to`) is `.lowercase()` per CLAUDE.md §4.7.
4. Gate: `./gradlew :sample-compose:assemble` — green.

### Phase 3 — Screen wiring + App.kt routing

1. `TxStatusScreen.kt`:
   - Signature: `@Composable fun TxStatusScreen(route: Route.TxStatus, navigator: Navigator)`.
   - Resolve `chain = ChainRegistry.byId(route.chainId) ?: return` (or render a small error card). Use `RpcClientFactory.forChain(chain)` — `remember(chain)` to avoid rebuilding the client on recomposition.
   - Host `var state by remember { mutableStateOf<TxStatusState>(Pending(0, false)) }`.
   - Call `TxPollingEffect(route.txHash, rpc) { state = it }`.
   - Wrap in `PhoneFrame` + rely on ambient `WalletTheme` (already applied at `WalletSampleApp` level — do not double-wrap).
   - `when (val s = state)`:
     - `is Pending` → `PendingState(s, route.txHash, chain)`.
     - `is Confirmed` → `ConfirmedState(s.receipt, navigator)`.
     - `is Failed` → small error card + `PrimaryButton("Back to Home") { navigator.replace(Route.Home) }`.
2. `App.kt` — add `is Route.TxStatus -> TxStatusScreen(route, navigator)` before the `else -> PlaceholderScreen(...)` fallthrough. Also add the `xyz.wallet.toolkit.sample.flows.tx.TxStatusScreen` import.
3. Gate: `./gradlew :sample-compose:assemble && ./gradlew :sample-app:assembleDebug` — green. Acceptance 1+2.

### Phase 4 — Self-review pass

1. Grep the new directory: `kotlin.random.Random`, `GlobalScope`, `rememberCoroutineScope`, `println`, `Log.d`, `Log.i`, `System.currentTimeMillis` — all must be zero hits. (Acceptance 4, 7, 8.)
2. Confirm every `navigator.replace(Route.Home)` call site and zero `navigator.push(Route.Home)` from the terminal CTAs. (Acceptance 10.)
3. Confirm `Confirmed` constructor takes a non-null `TransactionReceipt`; no branch in the UI `when` sees a nullable receipt. (Acceptance 6.)
4. Confirm `git diff --name-only` is a subset of `{sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/tx/*, sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/App.kt}`. (Acceptance 3 — noting the App.kt deviation.)
5. Final: `./gradlew :sample-compose:assemble && ./gradlew :sample-app:assembleDebug` — green.

## 5. Risks / Open questions

1. **App.kt routing — spec gap.** Acceptance criterion 3 says "no file outside `flows/tx/` is modified", but `App.kt` currently sends every non-Welcome route to `PlaceholderScreen`. Without a one-line edit to `App.kt`, `TxStatusScreen` is unreachable and acceptance criterion 2 (`:sample-app:assembleDebug`) passes but the screen is dead code. Three options: (a) edit `App.kt` and flag the deviation in PR description, (b) assume an earlier spec (S2–S5) already wired routing — inspection of `App.kt` shows it has not, (c) get spec clarification. **Recommendation: (a), edit `App.kt` with a single `is Route.TxStatus -> TxStatusScreen(...)` branch, call this out explicitly in the PR.** This is the minimal change consistent with the spec's functional goals.
2. **`chainId` → `SupportedChain` resolution.** `Route.TxStatus.chainId: Long`, but `RpcClientFactory.forChain` takes `SupportedChain`. The plan uses `ChainRegistry.byId(chainId)`. If resolution fails (unknown chain), the spec is silent — plan renders an inline error card and keeps the back-nav working. Flagging in case the spec wants a different fallback.
3. **`kotlinx.datetime` availability.** CLAUDE.md §2 lists `kotlinx.datetime` as commonMain-allowed, but `sample-compose` may not have an explicit dependency. If `Clock.System.now()` fails to resolve at compile time, fall back to `withFrameNanos` / a monotonic counter driven by the `delay` schedule itself (accumulate elapsed from the backoff totals). Do **not** use `System.currentTimeMillis()`. Verify at start of Phase 1 by attempting the import; if absent, use the delay-accumulator approach and note it.
4. **Copy-to-clipboard for tx hash.** Spec says "use the S1 helper if one exists; otherwise plain text". No such helper is listed in S1's file set — plan renders plain `MonoText` with no clipboard action. If a reviewer asks for copy, it's a follow-up.
5. **Toast primitive.** Spec explicitly forbids introducing a new shared snackbar. Plan uses inline `AnimatedVisibility` text under the stub buttons with a short auto-dismiss via a `LaunchedEffect(show) { delay(2000); show = false }`.

## 6. What this plan does NOT do

- Does not implement real Speed up / Cancel — UI stubs only, forever (per spec non-goals).
- Does not add signing, broadcasting, or wallet-core surface.
- Does not add persistent tx history, caching, or push notifications.
- Does not add revert-reason decoding beyond mapping `status == "0x0"` → `"Transaction reverted"`.
- Does not add mempool introspection (`txpool_*`).
- Does not add new chains, new RPC methods, or edits to `RpcClient.kt` / `JsonRpcModels.kt`.
- Does not add `kotlinx.datetime` or any other library to `gradle/libs.versions.toml` — if the dep is absent, use the delay-accumulator fallback.
- Does not add iOS parity (sample-compose `iosMain` is untouched).
- Does not add unit tests — this is a UI/lifecycle phase with no testable pure surface, and the spec's acceptance gates are assemble-only.
- Does not modify `ChainRegistry.kt`, `Chain.kt`, `settings.gradle.kts`, `libs.versions.toml`, or any other hot file.
- Does not edit `App.kt` beyond the single `is Route.TxStatus` branch and its import — flagged above as the only deviation from the spec's file list.

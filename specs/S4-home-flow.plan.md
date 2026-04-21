# S4 — Home flow — implementation plan

## Summary

Build the Home portfolio screen in `sample-compose` (commonMain only). The screen reads `WalletSession.wallet`, renders a shortened+copyable address header, fans out one coroutine per chain (Ethereum + Base) to fetch `eth_getBalance` through `RpcClientFactory`, and exposes Send/Receive CTAs. The spec's approach is sound: it leans entirely on the S1 seam, isolates per-chain failures, and avoids any crypto-sensitive surface. One routing-scope conflict to flag up front (see Risks §1).

## Files to create

All **new**, no existing file edited except where flagged:

| Path (all under `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/home/`) | Role |
|---|---|
| `HomeScreen.kt` | Composable entry. Reads `LocalWalletSession`, wires `HomeState`, hosts `LoadBalancesEffect`, renders `AddressHeader`, `PortfolioList`, Send/Receive CTAs. |
| `HomeState.kt` | `class HomeState` holding `balances: SnapshotStateMap<Long, ChainBalance>` and `selectedChainId: Long` (mutable state). `sealed interface ChainBalance { object Loading; data class Value(val weiHex: String); data class Error(val message: String) }`. `toString()` redacts. |
| `AddressHeader.kt` | Displays lowercased shortened address via `MonoText`; tap-to-copy full lowercase via `LocalClipboardManager`. Shows transient "Copied" label (state-driven, no toast). |
| `PortfolioList.kt` | `Column` of per-chain cards. Each card composes `ChainChip`/chain label + state-switched content: spinner (Loading), `BalanceRow` (Value), "Unavailable" + Retry link (Error). Tap selects the chain for Send. |
| `LoadBalancesEffect.kt` | `@Composable` effect keyed on `session.wallet`. Returns a small handle (e.g. `HomeLoader`) with `suspend fun refetch(chain: SupportedChain)` for per-row retry. Uses `rememberCoroutineScope()` + independent `launch` per chain; each wrapped in try/catch over `Throwable`. |

### Hot-file check (CLAUDE.md §7)

- `gradle/libs.versions.toml` — not touched.
- `settings.gradle.kts` — not touched.
- `ChainRegistry.kt`, `Chain.kt` — not touched.
- `CLAUDE.md`, `.claude/agents/*` — not touched.

### App.kt routing — CONFLICT, flagged

`sample-compose/src/commonMain/.../App.kt` currently dispatches only `Route.Welcome` and sends everything else to `PlaceholderScreen`. For the Home flow to be reachable, App.kt's `when` must add `is Route.Home -> HomeScreen(navigator)`. That is a one-line edit **outside** the `flows/home/` directory, which contradicts spec §25 ("No existing file is edited") and acceptance criterion 3.

**Recommendation:** add the one-line `Route.Home` branch to App.kt. Without it, criterion 5 (keying on `session.wallet`) cannot be exercised because the screen is unreachable. This plan proceeds on that basis and treats the App.kt edit as the minimum viable wiring. Raise with user before executing.

## Phases

Each phase ends on `./gradlew :sample-compose:assemble` (and `:sample-app:assembleDebug` for Phase 4) green. `sample-compose` has no commonTest configured; there is no KMP unit-test harness to write UI tests against. The "done gate" is **assemble**, not `allTests`, because the spec is UI-only.

### Phase 1 — State + effect skeleton

1. Create `HomeState.kt` with `ChainBalance` sealed interface, `balances` snapshot state map, `selectedChainId` backed by `mutableStateOf`, default `SupportedChain.Ethereum.id`. Override `toString()` to redact nothing sensitive (no address in state) but return a stable form that does not leak balance hex.
2. Create `LoadBalancesEffect.kt` with:
   - `@Composable fun rememberHomeLoader(session: WalletSession, state: HomeState, chains: List<SupportedChain>): HomeLoader`
   - `HomeLoader.refetch(chain)` launches on a `CoroutineScope` captured via `rememberCoroutineScope()`.
   - `LaunchedEffect(session.wallet)` inside the composable: for each chain, set `Loading`, then `launch` an independent coroutine that calls `RpcClientFactory.forChain(chain).getBalance(session.wallet.address(chain).lowercase())`, wraps in `try { Value } catch (t: Throwable) { Error(t.message ?: "rpc error") }`. `session.wallet?` null-guard returns early.
   - Each chain's fetch is its own `launch` — **not** `awaitAll` over a list; first-throw must not cancel siblings. Use `SupervisorJob` or explicit per-launch try/catch (the try/catch is sufficient; no throw escapes the coroutine).

**Gate:** `./gradlew :sample-compose:assemble` green.

### Phase 2 — UI surfaces

1. Create `AddressHeader.kt`: `fun shorten(addr: String) = addr.lowercase().let { "${it.take(6)}…${it.takeLast(4)}" }`. Use `LocalClipboardManager.current.setText(AnnotatedString(addr.lowercase()))` on tap. Track `var copied by remember { mutableStateOf(false) }` + `LaunchedEffect(copied)` that flips back after ~1500 ms.
2. Create `PortfolioList.kt`: `Column(verticalArrangement = Arrangement.spacedBy(12.dp))`. Per chain, a card surface (use `WalletColors.surface`, no new colors) containing a `Row` with chain name + state-switched right side:
   - `Loading` → `CircularProgressIndicator` sized small + text "Loading…".
   - `Value(hex)` → reuse S1 `BalanceRow(chain, weiHex = hex)` — BalanceRow already handles formatting + ticker.
   - `Error` → muted text "Unavailable" + `TextButton("Retry") { loader.refetch(chain) }`.
   - Whole row is `.clickable { state.selectedChainId = chain.id }` to set Send target. Visually indicate selection (border or tint using existing `WalletColors.accent`).
3. Create `HomeScreen.kt`:
   - Read `LocalWalletSession.current`.
   - `val state = remember { HomeState() }`, `val loader = rememberHomeLoader(session, state, homeChains)`.
   - Private top-level `val homeChains = listOf(SupportedChain.Ethereum, SupportedChain.Base)`.
   - `PhoneFrame { Column { AddressHeader(addr); PortfolioList(...); Spacer(weight=1f); Row { PrimaryButton("Send", enabled = currentBalanceNotLoading) { navigator.push(Route.Send(state.selectedChainId)) }; OutlinedButton("Receive") { showDialog = true } } } }`.
   - Receive dialog: Material3 `AlertDialog` with title "Receive", text "Coming soon", single OK button.
   - If `session.wallet == null`, render a minimal "No wallet" fallback (unreachable in normal flow but defensive).

**Gate:** `./gradlew :sample-compose:assemble` green.

### Phase 3 — App.kt wiring + edge cases

1. Edit `App.kt`: add `is Route.Home -> HomeScreen(navigator)` to the `when`. (See Risks §1 — flag before committing.)
2. Verify edge cases by code review:
   - Ethereum RPC fails, Base succeeds → Base row `Value`, Ethereum row `Error` + Retry. Both render.
   - Swap `session.wallet` → `LaunchedEffect(session.wallet)` re-runs; all rows return to `Loading` and re-fetch.
   - Retry on one chain writes only that chain's entry in `balances`; other entries untouched.
   - `Wallet.address(chain)` is called for both Ethereum and Base even though EVM addresses match — call `address(chain).lowercase()` at the RPC call site and at the `AddressHeader` display site. Grep check: every `.address(` in `flows/home/` is followed by `.lowercase()`.
   - `HomeState.toString()` does not include address or wei hex.

**Gate:** `./gradlew :sample-compose:assemble` green.

### Phase 4 — Final cross-platform compile check

1. `./gradlew :sample-compose:assemble` — green.
2. `./gradlew :sample-app:assembleDebug` — green.
3. `git diff --name-only` shows only `flows/home/*.kt` + `App.kt` (the flagged one-line edit).

`sample-compose` is Android-only (per `EthFormat.kt` comment). No iOS compile check needed for this module. No `wallet-core` or `wallet-rpc` change → no per-module `allTests` rerun required by CLAUDE.md §3.

## Golden vectors

**N/A.** This spec contains no signing, serialization-of-signing-input, or key derivation. The only crypto-adjacent behavior is address display casing, which is covered by a code-review grep (Phase 3).

## CLAUDE.md §4 applicability

- §4.1 (no log/toString of sensitive material): `HomeState.toString()` must not include balance hex or address. `Wallet` already has a redacted `toString()` per S1. No `println` of addresses or hex anywhere in `flows/home/`.
- §4.2 (SecureRandom): **no randomness used.** No retry backoff randomization, no request IDs generated here (RPC request IDs are internal to wallet-rpc).
- §4.3 (constant-time compare): **N/A** — no secret comparisons.
- §4.4 (no `Random.nextLong` for nonces): **N/A** — this flow does not build transactions.
- §4.5 (golden vectors for signing): **N/A** — no signing.
- §4.6 (never weaken assertions): no tests to weaken.
- §4.7 (address casing): enforced. Lowercase at RPC call site and at display.
- §4.8 (tx serialization): **N/A** — no transaction serialization here.

## Risks / Open questions

1. **App.kt edit conflicts with spec's "files only under flows/home/" rule.** Without it, `Route.Home` falls through to `PlaceholderScreen` and the screen is unreachable. Raising to user: is a one-line `when`-branch edit in `App.kt` acceptable, or should `Route.Home` wiring be deferred to a subsequent integration spec? The plan above assumes acceptable.
2. **BalanceRow does not support Loading/Error states** — it takes `weiHex: String?`. Plan handles this by rendering Loading/Error states *around* BalanceRow in `PortfolioList`, only invoking `BalanceRow` for the `Value` case. This keeps S1 untouched.
3. **RpcClientFactory.forChain signature confirmed** as `forChain(chain: SupportedChain): RpcClient`. Spec's open question resolved.
4. **Public RPC endpoints** — confirmed hardcoded in `RpcClientFactory`; Home flow does not touch them.
5. **ChainChip is a multi-chain filter** — takes `selected` + `onSelect`. Spec mentions using it per-row, but ChainChip renders the whole list. Plan uses per-row chain label (not ChainChip) inside PortfolioList; ChainChip is not the right primitive for a single-row identifier. Flag to user if PortfolioList is required to literally embed ChainChip.
6. **Receive dialog import** — Material3 `AlertDialog` is already transitively available via `ButtonDefaults`/`Text` imports elsewhere in `sample-compose`. No new dependency needed.
7. **Clipboard on iOS** — `sample-compose` is Android-only post-S1 (per EthFormat comment), so `LocalClipboardManager` suffices. If the module later gains iOS, clipboard handling needs a re-review.

## What this plan does NOT do

- Does **not** implement the Send flow (`Route.Send`) — remains a route handoff. That is S5.
- Does **not** show a QR code on Receive.
- Does **not** fetch ERC-20/token balances.
- Does **not** perform USD conversion — the "—" placeholder is literal.
- Does **not** cache balances, pull-to-refresh, or auto-refresh on a timer.
- Does **not** add Polygon/Arbitrum/Optimism/BnbSmartChain to the Home filter.
- Does **not** surface RPC error messages to the UI ("Unavailable" only).
- Does **not** modify `wallet-utils`, `wallet-core`, `wallet-evm`, `wallet-rpc`, `settings.gradle.kts`, `gradle/libs.versions.toml`, or any module's `build.gradle.kts`.
- Does **not** add DI, Koin, Hilt, or any framework.
- Does **not** add `ktlintCheck` or `detekt` to the gate (CLAUDE.md §3 — not configured in this repo).
- Does **not** add commonTest for `sample-compose` (module has no test source set; adding one is out of scope).
- Does **not** modify `CLAUDE.md`, `.claude/agents/*`, or any S1 file beyond the one-line `App.kt` wiring (flagged).

# S4 — Home flow — portfolio for Ethereum + Base

## Goal

Build the Home (portfolio) screen for `sample-compose` following wireframe direction A (Classic). The screen shows:

- A shortened, copyable address header for the active `WalletSession`.
- A vertical list of per-chain balance rows — one per registered EVM chain (Ethereum `id=1`, Base `id=8453`) — fetched independently via `wallet-rpc`'s `RpcClient.getBalance(address)`.
- Primary CTAs: **Send** (navigates to `Route.Send(chainId)`) and **Receive** (stub alert — "Coming soon").

Balance state per chain is `Loading` / `Value(weiHex)` / `Error(message)`. Failures in one chain's fetch must never blank the other chain's row or crash the screen.

This spec is UI-only, depends entirely on the S1 seam (Navigator, WalletSession(Holder), WalletTheme, PhoneFrame, PrimaryButton, MonoText, ChainChip, BalanceRow, RpcClientFactory, EthFormat), and does not touch any `wallet-*` module.

---

## Module(s) touched

- `sample-compose` (commonMain only).

No other module is modified. No `wallet-utils`, `wallet-core`, `wallet-evm`, `wallet-rpc`, `settings.gradle.kts`, `gradle/libs.versions.toml`, or `build.gradle.kts` edit is permitted by this spec.

---

## Files expected to change

All new, all under `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/home/`:

| File | Role |
|------|------|
| `HomeScreen.kt` | Composable entry point. Wires `HomeState`, `LoadBalancesEffect`, `AddressHeader`, `PortfolioList`, and the two CTAs. Reads `WalletSessionHolder.current` and `Navigator` from S1. |
| `HomeState.kt` | Plain `MutableState` holder. Holds a `Map<Long, ChainBalance>` (key = `Chain.id`) plus `selectedChainId: Long` for the Send CTA. `ChainBalance` is a sealed interface with `Loading`, `Value(weiHex: String)`, `Error(message: String)`. No persistence. |
| `AddressHeader.kt` | Renders the shortened address (`0xabcd…1234`, first 6 + last 4, **lowercase**) via S1 `MonoText`, with a tap-to-copy affordance (uses `LocalClipboardManager`). |
| `PortfolioList.kt` | Vertical column of cards — one per chain — composing S1 `BalanceRow`. Maps `ChainBalance.Value` → `EthFormat.weiToEth(weiHex)` + `"—"` USD placeholder; `Loading` → spinner; `Error` → inline **retry** link that invokes a re-fetch callback for just that chain. |
| `LoadBalancesEffect.kt` | `@Composable` `LaunchedEffect(session.wallet)`. Fans out N coroutines (one per chain in the Ethereum + Base filter). Each calls `RpcClientFactory.forChain(chain).getBalance(session.wallet.address(chain))`, catches any `Throwable`, and writes the result into `HomeState` keyed by `chain.id`. Also exposes a `suspend fun refetch(chain: Chain)` used by the per-row retry link. |

No existing file is edited.

---

## Design

### Chain set

The Home flow iterates `SupportedChain.values()` and filters to `{ SupportedChain.Ethereum, SupportedChain.Base }`. The chain set is a constant list in `HomeScreen.kt` (or a private top-level `val homeChains` in the same file). If S1 provides a different helper (e.g. `ChainRegistry.evmSupported()`), the implementer may substitute it — the filter set is the contract, not the accessor.

### AddressHeader

- Input: `address: String` (already the output of `wallet.address(chain)` — any chain works, EVM addresses are the same across EVM chains).
- Display: `address.lowercase()` shortened to `"${first6}…${last4}"` where `first6 = address.take(6)` (includes `0x`) and `last4 = address.takeLast(4)`.
- Tap → copy the **full lowercase** address (not the shortened form) to the system clipboard via `LocalClipboardManager.current.setText(AnnotatedString(fullLower))`. A short Snackbar/inline "Copied" confirmation is acceptable; no toast on iOS required.
- Uses S1 `MonoText` for the monospace presentation.
- No QR code (see Non-goals).

### PortfolioList + BalanceRow usage

- Single column, Classic-A palette: one card per chain, quiet surface, no dividers between cards, 12 dp gap.
- Per row, from S1 `BalanceRow`:
  - `chain` → passed through to render S1 `ChainChip` (icon + display name).
  - `balance` → derived from `HomeState[chain.id]`:
    - `Loading` → spinner + "Loading…".
    - `Value(weiHex)` → `EthFormat.weiToEth(weiHex)` followed by chain's `ticker`; secondary line `"—"` as USD placeholder.
    - `Error(msg)` → muted "Unavailable" label plus a clickable "Retry" affordance that calls `loadBalancesEffect.refetch(chain)`. The message itself is **not** surfaced to the user (it can contain RPC internals); optionally include it as a content description for debugging.
- Tapping a row sets `HomeState.selectedChainId = chain.id` so the Send CTA targets that chain. The default selection is `SupportedChain.Ethereum.id`.

### LoadBalancesEffect

```
@Composable
fun LoadBalancesEffect(
    session: WalletSession,
    state: HomeState,
    chains: List<Chain>,
)
```

- Wrapped in `LaunchedEffect(session.wallet)` so switching/importing a wallet re-fetches.
- For each chain, launches a child coroutine on the effect's scope:
  1. Sets `state[chain.id] = ChainBalance.Loading`.
  2. Calls `RpcClientFactory.forChain(chain).getBalance(session.wallet.address(chain).lowercase())`.
  3. On success → `ChainBalance.Value(weiHex)`.
  4. On `Throwable` → `ChainBalance.Error(t.message ?: "rpc error")`. Never rethrows.
- Exposes a per-row retry through a small object returned from a companion composable (or via a `remember { HomeLoader(...) }` pattern) — the exact shape is an implementation choice, but the retry must not re-trigger the other chains' fetches.

**Open question:** S1 spec says `RpcClientFactory` but the exact signature (`forChain(chain: Chain)` vs `forChainId(id: Long)`) is decided in S1. This spec assumes `forChain(chain: Chain)`; re-align on implementation if S1 ships something else — do not edit S1.

**Open question:** public RPC endpoints (e.g. `https://ethereum-rpc.publicnode.com`, `https://base-rpc.publicnode.com`) are owned by S1's `RpcClientFactory`. Home flow must not hardcode URLs.

### CTAs

Two primary buttons at the bottom of `PhoneFrame`, side-by-side, using S1 `PrimaryButton`:

- **Send** → `Navigator.push(Route.Send(chainId = state.selectedChainId))`. Disabled while the currently-selected chain's balance is `Loading` (UX affordance; not a hard requirement — but do not send with a stale/unknown balance).
- **Receive** → shows a Material `AlertDialog` with title `"Receive"` and body `"Coming soon"` and a single "OK" button.

### Visual direction (Classic-A)

Single-column layout, quiet palette (use `WalletTheme` tokens from S1 only — do not introduce new colors). `PhoneFrame` wraps the screen. Typography from `WalletTheme`. No gradients, no hero illustrations.

### Security / hygiene

- Home flow reads **only** `session.wallet.address(chain)` and `session.wallet` (for the `LaunchedEffect` key). It must not touch any mnemonic, private key, or seed-bearing API.
- No `kotlin.random.Random` usage anywhere in this flow — there is no need for randomness here.
- Addresses are lowercased at the point of display and at the point of RPC call (CLAUDE.md §4.7). The chosen convention for this flow is **lowercase throughout**.
- No logging of addresses or balance hex in `toString()` or `println`. If `HomeState` gets a debug `toString()`, it must redact the address.

---

## Acceptance criteria

1. `./gradlew :sample-compose:compileKotlinJvm` — green.
2. `./gradlew :sample-app:assembleDebug` — green.
3. `git diff --name-only` after the change shows **only** files under `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/home/`. No other file is touched.
4. Balance fetch failures do not crash the screen: with Ethereum's RPC failing and Base's succeeding (or vice versa), the succeeding chain's row renders `Value(...)` while the failing chain's row renders the `Error` state with a Retry affordance.
5. The `LaunchedEffect` is keyed on `session.wallet` — swapping `WalletSessionHolder.current` (e.g. post-import) re-triggers the fetch for every chain.
6. The displayed address and the address passed to `getBalance(...)` are both lowercase. A test or code review should be able to confirm this by grep for `.address(` call sites — every one is followed by `.lowercase()` (or routes through a helper that lowercases).
7. Retry on a single `Error` row re-fetches **only** that chain; other chains' rows are untouched during the retry.
8. Send CTA calls `Navigator.push(Route.Send(chainId = <id of currently selected chain>))`; Receive CTA shows an `AlertDialog` and does not navigate.
9. No `wallet-*` module, no `settings.gradle.kts`, no `gradle/libs.versions.toml`, no `build.gradle.kts`, no S1 file, no other flow file is modified.

---

## Non-goals

- **Send transaction build/sign** — that is S5. This spec only wires the Send CTA to the route.
- **Tx list / history** — out of scope for the entire S1–S6 set.
- **Token balances** — ERC-20/721 balances are not fetched. Only native ETH via `eth_getBalance`.
- **Price lookup / USD conversion** — the USD column is a literal `"—"` placeholder; no price oracle, no currency conversion.
- **Receive-address QR code** — the Receive CTA is a "Coming soon" stub. QR generation belongs to a future phase.
- **RPC endpoint configuration UI** — S1 hardcodes public endpoints in `RpcClientFactory`; Home does not expose settings.
- **Caching / offline mode** — balances are fetched each time the screen is opened (or `session.wallet` changes). No persistence layer, no SWR, no stale-while-revalidate.
- **Additional chains** — Polygon/Arbitrum/Optimism/BNB are registered in `SupportedChain` but are explicitly excluded from the Home filter for this spec. Adding them is a future task.
- **Pull-to-refresh, auto-refresh on interval** — refresh happens on session change and on per-row Retry. No timer.
- **Error-message surfacing** — raw RPC error text is not shown to users; rows show "Unavailable" + Retry.

---

## Why this is interesting for the experiment

The Home flow is the first screen where the sample app **does something asynchronous and fallible per chain in parallel**. It stresses three axes that matter for the parallel-agent experiment:

1. **Concurrent failure isolation** — the spec forces a per-chain `try/catch` boundary, which is a common place where a single agent will over-share state (collect into one `Result`, cancel siblings on first throw, etc.) and regress. A parallel-agent run will expose whether the isolation invariant is preserved.
2. **Seam discipline** — Home is downstream of S1 but depends on half the S1 surface (`Navigator`, `WalletSession(Holder)`, `RpcClientFactory`, `EthFormat`, `MonoText`, `ChainChip`, `BalanceRow`, `PhoneFrame`, `PrimaryButton`, `WalletTheme`). It is a good test of whether the S1 contract is stable enough for a parallel agent to consume without reaching back into S1 files.
3. **Crypto hygiene at the UI layer** — the casing rule (§4.7), the "no mnemonic/private-key near UI" rule (§4), and the "no `Random` for anything" rule together constrain implementation choices that a less careful agent would otherwise take (e.g. using `Random` for a retry backoff, or logging the full address). Review effort on this spec is a useful signal for whether agents are internalizing CLAUDE.md §4.

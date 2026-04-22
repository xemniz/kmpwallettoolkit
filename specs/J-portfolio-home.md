# J — Portfolio Home — Zerion-backed token list

## Goal

Replace the Home screen's native-balance-only display with a real portfolio
view backed by the Zerion REST API. The new layout matches the Classic-A
design from the handoff:

- Total USD balance across the selected chain + 24h delta
- Per-token list (symbol, quantity, USD value) — native + ERC-20
- Existing Send CTA + the chain filter (Ethereum / Base) preserved

The Zerion client is HTTP/REST (not JSON-RPC), so it is **not** added to
`wallet-rpc`. It lives in `sample-compose` as a showcase concern.

The API key is loaded from an object whose file is **gitignored**; a
`Secrets.kt.template` is checked in so contributors know the shape.

---

## Module(s) touched

- `sample-compose` (commonMain only — no new expect/actual)

No other module is modified. `wallet-*` modules are **not** touched.

---

## Files expected to change

| File | Role |
|------|------|
| `.gitignore` | Add `Secrets.kt` ignore rule (already added in prep). |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/secrets/Secrets.kt.template` **(new, checked in)** | Documents the shape: `internal object Secrets { const val ZERION_API_KEY: String = "REPLACE_ME" }`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/secrets/Secrets.kt` **(new, gitignored)** | Local copy with the real key. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/portfolio/ZerionClient.kt` **(new)** | Ktor client + DTOs. One method: `suspend fun fetchPortfolio(address, chain): PortfolioSnapshot`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/portfolio/PortfolioModels.kt` **(new)** | `PortfolioSnapshot`, `TokenPosition`, `PortfolioChange24h` — plain data classes with redacted `toString()` where appropriate. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/home/HomeState.kt` | Add `portfolios: Map<chainId, PortfolioState>` where `PortfolioState` is `Loading` / `Value(snapshot)` / `Error`. Remove the native-only `balances` field — nothing else reads it after this change. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/home/LoadBalancesEffect.kt` | Rewrite the body to call `ZerionClient.fetchPortfolio`. Keep the function name `rememberHomeLoader` and signature for minimal churn. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/home/HomeScreen.kt` | Rebuild around design A: address header, total USD + 24h delta, 4 action tiles (Send enabled, Receive stub, Swap/Buy disabled), Assets list, Sign out. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/home/PortfolioList.kt` | Replace the existing chain-card list with a per-token list. Selected chain drives which snapshot it reads. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/send/SendScreen.kt` | Update to read the new `PortfolioState.Value.nativeWeiHex` (Zerion exposes native balance in its position list — we extract it for Send's fee check). |

`sample-compose/build.gradle.kts` already depends on Ktor core + content-negotiation + kotlinx-serialization; no new dep.

---

## Design

### Zerion API surface

One endpoint suffices:

```
GET https://api.zerion.io/v1/wallets/{address}/positions/
    ?filter[chain_ids]=ethereum           (or 'base')
    ?filter[position_types]=wallet
    ?currency=usd
Authorization: Basic base64("{API_KEY}:")
```

Returns a JSON-API document whose `data[]` entries have:

- `attributes.fungible_info.symbol`, `name`, `icon.url`
- `attributes.fungible_info.implementations[]` — mark `native = true` for
  the chain's native coin
- `attributes.quantity.numeric` (decimal string, human units)
- `attributes.value` (USD, nullable when Zerion has no price)
- `attributes.changes.absolute_1d` + `percent_1d` (nullable)
- Filter the list to drop dust (configurable threshold, start at `$0.01`)

Portfolio totals are the sum of `attributes.value` where non-null, and
24h delta the sum of `changes.absolute_1d`.

**Not using** `/v1/wallets/{address}/portfolio` — its chain breakdown
shape is more rigid, and we already sum client-side.

### Chain mapping

| SupportedChain | Zerion `chain_id` |
|----------------|-------------------|
| Ethereum (1)   | `ethereum`        |
| Base (8453)    | `base`            |

Wrap in `SupportedChain.zerionChainId` — single mapping file inside the
portfolio package, not on `SupportedChain` itself (keeps wallet-core free
of sample concerns).

### State shape

```kotlin
data class PortfolioSnapshot(
    val tokens: List<TokenPosition>,
    val totalUsd: Double,
    val change24h: PortfolioChange24h?,
) {
    val nativeWeiHex: String?  // derived: the native token's wei, for SendScreen
}

data class TokenPosition(
    val symbol: String,
    val name: String,
    val quantity: String,   // numeric decimal from Zerion, display-ready
    val valueUsd: Double?,
    val iconUrl: String?,
    val isNative: Boolean,
)

data class PortfolioChange24h(val absoluteUsd: Double, val percent: Double)

sealed interface PortfolioState {
    object Loading : PortfolioState
    data class Value(val snapshot: PortfolioSnapshot) : PortfolioState
    data class Error(val message: String) : PortfolioState
}
```

### UI — Home (Classic-A)

```
┌──────────────────────────────────┐
│ [M] Main wallet      [◇ Ethereum▾]│
│     0x7a2f…c41b                   │
├──────────────────────────────────┤
│        TOTAL BALANCE              │
│         $2,847.19                 │
│       +$42.31 · 1.5% today        │
├──────────────────────────────────┤
│  [Send] [Receive] [Swap·] [Buy·]  │   (Swap/Buy disabled)
├──────────────────────────────────┤
│  Assets                        3  │
│  ─────────────────────────────── │
│  [E] ETH    0.892 ETH   $2,499.60 │
│  [U] USDC   312.5 USDC  $312.50   │
│  [L] LINK   1.86 LINK   $35.09    │
├──────────────────────────────────┤
│             Sign out              │
└──────────────────────────────────┘
```

- Address stays tap-to-copy (existing `AddressHeader` — may be
  restyled slightly but not rewritten).
- Chain chip becomes a tap target that opens a two-item bottom sheet
  (Ethereum / Base). Tapping switches `state.selectedChainId`.
- Send is enabled only when the selected chain's `PortfolioState` is
  `Value` and native balance > 0 (mirrors today's gate).
- Swap/Buy render as disabled action tiles — no nav, no dialog.

### Error + loading surfaces

- `PortfolioState.Loading` → skeleton shimmer on total + 3 greyed rows.
- `PortfolioState.Error` → inline "Couldn't load portfolio — tap to retry"
  in place of the list. Tap triggers a re-fetch.
- Network exceptions from Zerion are caught at the loader and mapped to
  a generic message. The exception is not logged (CLAUDE.md §4.1 —
  Zerion includes the wallet address in error responses; we treat the
  address as tracking-grade sensitive, not secret, but still keep it out
  of logs).

### API key handling

- `Secrets.kt` (gitignored) provides `ZERION_API_KEY`. Build fails fast
  if the value equals `"REPLACE_ME"` — a compile-time `check` at the top
  of `ZerionClient.init` is sufficient; no runtime-only surprise.
- The client never logs the key. `ZerionClient.toString()` is redacted.
- No Basic-auth header ever appears in a `println`.

---

## Acceptance criteria

1. `./gradlew :sample-compose:compileKotlinJvm :sample-compose:compileKotlinIosSimulatorArm64` green.
2. `./gradlew :sample-app:assembleDebug` green.
3. `./gradlew :sample-compose:linkDebugFrameworkIosSimulatorArm64` green, then `xcodebuild … -scheme iosApp` green.
4. Running the Android app with a real Ethereum address (e.g. the
   existing Trust Wallet Core dev wallet in the showcase), Home renders:
   total USD, a 24h delta (positive or negative), at least the native
   ETH row, and Sign out.
5. Chain chip switches between Ethereum and Base; the token list
   repopulates.
6. Send button enabled state follows `PortfolioState.Value` + native > 0.
7. Sign out still clears the mnemonic (no regression of spec I).
8. No new logs of the API key, the wallet address, or any token balance
   (`grep -n 'Log\\|println\\|print(' sample-compose/src/commonMain/.../portfolio/` returns nothing that references `address`, `key`, or `quantity`).
9. `Secrets.kt` is gitignored; `Secrets.kt.template` is checked in;
   `git ls-files` does not list `Secrets.kt`.
10. `HomeState` no longer holds a native-only `balances` map; SendScreen
    compiles against the new `PortfolioSnapshot.nativeWeiHex`.

---

## Non-goals

- NFTs / DeFi positions / activity feed — design C's tabs are deferred.
- Portfolio refresh on pull-to-refresh — loader fires once per entry
  + once per chain switch, same rhythm as today.
- Historical charts / sparklines.
- Fiat-currency picker — USD only.
- Widening `wallet-rpc` with REST support. Kept inside sample-compose.
- Price feed for chains beyond Ethereum + Base. Zerion supports many,
  but we only render the two the showcase advertises.

---

## Why this is interesting for the experiment

- First REST (non-JSON-RPC) HTTP client in the repo — exercises Ktor
  content-negotiation in commonMain with iOS as a target (Darwin engine
  already wired for wallet-rpc).
- Secret-in-source-file pattern with a gitignored split: the repo has
  no existing story for this, so the solution is observable to future
  readers.
- Replacing an existing, test-covered screen (`HomeScreen`) without
  weakening the per-chain native-balance path that `SendScreen` depends
  on — a real integration-surface test, not a greenfield phase.
- Proves the design-handoff → spec → implement pipeline end-to-end on
  a UI-shaped task (contrast with the crypto-heavy A–H tasks).

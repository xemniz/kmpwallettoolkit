# U — Swap — 0x-backed token swap with Zerion search

## Goal

Turn Home's disabled "Swap" action tile into a working flow: pick a sell
token + buy token, enter an amount, fetch a live quote from the 0x Swap
API, sign the returned EVM transaction with the user's wallet, and
broadcast it via our existing RPC layer.

The flow is "anything to anything" on the two chains the showcase already
supports (Ethereum, Base). Users land in the picker with a short list of
sensible defaults (ETH / USDC / USDT / DAI / WETH / WBTC on Ethereum; ETH
/ USDC / USDbC / DAI / WETH on Base), and a search field that autocompletes
against Zerion's fungibles index — free-form "link", "pepe", "wbtc" work.

---

## Module(s) touched

- `sample-compose` (commonMain only — no new expect/actual)

No `wallet-*` module is touched. 0x and Zerion are sample concerns.

---

## Files expected to change

| File | Role |
|------|------|
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/secrets/Secrets.kt` | Add `ZEROX_API_KEY: String`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/secrets/Secrets.kt.template` | Mirror the new field. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/portfolio/ZerionClient.kt` | Add `suspend fun searchTokens(query, chain): List<TokenRef>`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/swap/TokenRef.kt` **(new)** | Cross-client token identity — symbol, name, address (null = native), decimals, chain, iconUrl. Shared by picker + quote. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/swap/DefaultTokens.kt` **(new)** | Per-chain list of 5–6 canonical tokens for the empty-search state. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/swap/ZeroExClient.kt` **(new)** | Ktor client: `fetchQuote(chain, sell, buy, sellAmountRaw, taker)` against `/swap/allowance-holder/quote`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/swap/SwapQuote.kt` **(new)** | Quote DTOs + domain model (`SwapQuote`, `AllowanceIssue`, `QuoteTransaction`). |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/swap/Erc20.kt` **(new)** | Tiny ABI helpers: `allowanceCallData(owner, spender)`, `approveCallData(spender, amount)`, `decodeUint256(hex)`. Pure string/hex — no BigInteger dependency. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/swap/SwapAssembler.kt` **(new)** | The execution state machine: allowance-check → (approve-if-needed) → sign+broadcast. Mirrors `SendTxAssembler`'s secrecy contract. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/swap/SwapViewModel.kt` **(new)** | Holds UI state (selected tokens, amount text, quote flow). Exposes `StateFlow<SwapUiState>`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/swap/SwapScreen.kt` **(new)** | Composable — sell card, flip button, buy card, quote summary, review+sign. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/swap/TokenPickerSheet.kt` **(new)** | Modal search + list — opens from either sell/buy slot. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/nav/Route.kt` | Add `data class Swap(val chainId: Long)`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/App.kt` | Route `Route.Swap` to `SwapScreen`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/home/HomeScreen.kt` | Enable the Swap tile — `navigator.push(Route.Swap(chainId = ui.selectedChainId))`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/di/AppModule.kt` | Register `ZeroExClient` (single), `SwapViewModel` (factory — per-entry state). |

No Gradle edits — Ktor + serialization are already on the commonMain
classpath.

---

## Design

### 0x API surface

Use the **allowance-holder** swap endpoint, not permit2. AllowanceHolder
uses a classic ERC-20 `approve(spender, amount)` to a single 0x-controlled
contract; no EIP-712 typed-data signing required (Trust Wallet Core's
EIP-712 surface is not currently exposed through `wallet-core`, and the
sample should not widen the library API for a showcase feature).

```
GET https://api.0x.org/swap/allowance-holder/quote
    ?chainId={1|8453}
    &sellToken={address|'ETH'}
    &buyToken={address|'ETH'}
    &sellAmount={raw integer, base units}
    &taker={wallet address}
    &slippageBps=100
0x-api-key: {ZEROX_API_KEY}
0x-version: v2
```

Response (subset the sample uses):

```jsonc
{
  "buyAmount": "499321...",          // raw integer, buy-token base units
  "sellAmount": "1000000000000000000",
  "minBuyAmount": "494328...",       // after slippage
  "transaction": {
    "to": "0x...",
    "data": "0x...",
    "value": "0",                    // hex or decimal — normalize to hex
    "gas": "250000",
    "gasPrice": "..."
  },
  "issues": {
    "allowance": { "actual": "0", "spender": "0x..." } | null,
    "balance":   { "actual": "...", "expected": "..." } | null
  }
}
```

`issues.allowance` is the signal for "sell token is ERC-20 and the user
hasn't approved 0x yet". When present we split the flow into an approve
tx followed by the swap tx.

### Token search surface (Zerion)

```
GET https://api.zerion.io/v1/fungibles/
    ?filter[search_query]={query}
    &filter[implementation_chain_id]={ethereum|base}
    &page[size]=20
Authorization: Basic base64("{ZERION_API_KEY}:")
```

Projection:

```kotlin
data class TokenRef(
    val symbol: String,
    val name: String,
    val address: String?,   // null = chain-native (ETH)
    val decimals: Int,
    val chain: SupportedChain,
    val iconUrl: String?,
)
```

`address` maps to the chain-matching entry in
`attributes.implementations[]`. `null` address + symbol==native-symbol
(per chain) is the native coin.

### Default tokens

Hardcoded in `DefaultTokens.kt`, one list per chain. They render before
the user types into the search box, and as a fallback when search fails.

- **Ethereum**: ETH, USDC, USDT, DAI, WETH, WBTC
- **Base**: ETH, USDC, USDbC, DAI, WETH

Addresses + decimals in the list are sourced from the canonical
deployments. The list is intentionally short — this is discovery UX, not
a token directory.

### State shape

```kotlin
data class SwapUiState(
    val chain: SupportedChain,
    val sell: TokenRef,                 // defaults to native
    val buy: TokenRef,                  // defaults to stablecoin (USDC)
    val sellAmountInput: String,        // human-units, free-form text
    val quote: QuoteState,
    val submission: SubmissionState,
)

sealed class QuoteState {
    object Idle : QuoteState()          // no quote fetched yet / amount empty
    object Loading : QuoteState()
    data class Ready(val quote: SwapQuote) : QuoteState()
    data class Error(val userMessage: String) : QuoteState()
}

sealed class SubmissionState {
    object Idle : SubmissionState()
    object Approving : SubmissionState()
    object Submitting : SubmissionState()
    data class Error(val userMessage: String) : SubmissionState()
}

data class SwapQuote(
    val sellAmountRaw: String,
    val buyAmountRaw: String,
    val minBuyAmountRaw: String,
    val transaction: QuoteTransaction,
    val allowanceIssue: AllowanceIssue?,  // null = no approval needed
)

data class QuoteTransaction(
    val to: String,
    val dataHex: String,
    val valueWei: String,   // decimal string
    val gasLimit: String,   // decimal string
)

data class AllowanceIssue(val spender: String)
```

Quote refresh is debounced 400 ms on `sellAmountInput`. Token changes
trigger an immediate refetch. Amount <= 0 or empty → `QuoteState.Idle`.

### Execution — `SwapAssembler`

Mirrors `SendTxAssembler`:

1. If `allowanceIssue` present:
   - `eth_call allowance(taker, spender)` — double-check (the issue field
     can lag). If actual >= sellAmountRaw, skip approve.
   - Build an EIP-1559 tx: `to = sellToken.address`,
     `data = approve(spender, MAX_UINT256)`, `value = 0`, `gasLimit = 60_000`.
   - `maxFeePerGas` / `maxPriorityFeePerGas`: lift from a
     `eth_maxPriorityFeePerGas` + padded `baseFee` — same helper we'll
     add as `Fees.suggestEip1559(rpc)`. Nonce from
     `eth_getTransactionCount latest`.
   - Sign with `wallet.signEip1559Transaction`, broadcast with
     `rpc.sendRawTransaction`. Surface `Approving` state throughout.
   - Wait for receipt via `eth_getTransactionReceipt` polling (2 s,
     max 60 s). Success when `status == "0x1"`; failure otherwise.
2. Fetch the quote again (a stale quote's `transaction.data` may have
   encoded the old allowance state). Then:
   - Build a 1559 tx from `quote.transaction`, with fresh nonce and the
     same fee suggestion.
   - Sign + broadcast.
3. On success, `navigator.replace(Route.TxStatus(txHash, chainId))`.

Error mapping matches `SendTxAssembler`: fixed user strings, no
interpolation of node responses, no logging of signing payloads. A
failed approve does not advance to step 2.

### UI

```
┌──────────────────────────────────┐
│ ‹  Swap             Ethereum ▾   │
├──────────────────────────────────┤
│  You pay                          │
│  [0.25        ]     ETH     ▾     │   ← tap opens picker
│                balance 0.89       │
├──────────────────────────────────┤
│               [ ⇅ ]                │   ← flip sell/buy
├──────────────────────────────────┤
│  You receive                      │
│  [~ 499.32    ]    USDC     ▾     │   ← read-only, from quote
│                                   │
├──────────────────────────────────┤
│  1 ETH ≈ 1997.28 USDC             │
│  Slippage 1.00%  ·  Min out 494.3 │
├──────────────────────────────────┤
│                                   │
│                                   │
│           [  Review  ]            │
└──────────────────────────────────┘
```

- The sell amount is the only text input. Buy amount updates from the
  quote.
- The chain selector in the header is **read-only** here — routed from
  the Home chain pick, same as Send.
- Review opens a confirmation sheet listing the two tx steps if approval
  is required ("1. Approve USDC for 0x · 2. Swap 200 USDC → 0.09 ETH").
  Hold-to-sign reuses the pattern from `ReviewAndSignSheet`.
- `TokenPickerSheet` is opened from either slot; it shows the default
  list when the search field is empty, and the remote results otherwise.
  Tapping the native chip filters `results` to just the native coin.

### Secrecy

- `Secrets.ZEROX_API_KEY` is never logged. `ZeroExClient.toString()`
  returns `"ZeroExClient"`.
- 0x responses include the user's wallet address in the `taker` field
  and in `transaction.to/data` — these are not secret but are
  tracking-grade. Do not interpolate quote responses or allowance
  responses into user-facing error strings.
- `SwapAssembler` does not log the signing payload or the signed
  `ByteArray`, same as `SendTxAssembler` (CLAUDE.md §4.1).

---

## Acceptance criteria

1. `./gradlew :sample-compose:compileKotlinJvm :sample-compose:compileKotlinIosSimulatorArm64` green.
2. `./gradlew :sample-app:assembleDebug` green.
3. `./gradlew :sample-compose:linkDebugFrameworkIosSimulatorArm64` green.
4. From Home, tapping the Swap tile navigates to the Swap screen with
   the chain pinned to the Home-selected chain.
5. Sell and Buy token pickers open; the empty-state shows the
   `DefaultTokens` list; typing "link" returns LINK from Zerion within
   a few seconds; selection updates the card.
6. Entering a positive sell amount fetches a quote — buy amount,
   price-per-sell, and min-out all render from the 0x response.
7. For a **native → ERC-20** swap with sufficient ETH, Review → hold to
   sign → TxStatus screen; the submitted raw tx lands on the chain's
   public RPC and `eth_getTransactionReceipt` returns non-null.
8. For an **ERC-20 → native** swap where the token has no prior 0x
   allowance, the flow submits the approve tx first, polls for receipt,
   then submits the swap tx. Mid-flow the UI reflects
   `SubmissionState.Approving` and then `Submitting`.
9. Amount ≤ 0 / empty → `QuoteState.Idle`, Review disabled, no network
   request fired.
10. Secrets grep: `grep -rn 'println\|Log\\.' sample-compose/src/commonMain/.../flows/swap/` contains no references to `ZEROX_API_KEY`, `taker`, `signed`, or `dataHex`.
11. `Secrets.kt` remains gitignored; `Secrets.kt.template` is updated
    with the new field stub.

---

## Non-goals

- Cross-chain swaps (LiFi-style). Same-chain only.
- Permit2 flow. AllowanceHolder is simpler for this showcase; if we
  later want gasless-style UX, that's a separate spec.
- Slippage slider. Fixed at 100 bps (1%). A later task can add a
  settings sheet.
- "Max" button on the sell card for native coin. The sell amount input
  is free-text only — native balance gating on "Max" requires a wei-
  level conversion the portfolio layer doesn't expose today.
- Recurring/allowance refresh. When the swap tx itself is rejected for
  a balance reason, we surface a generic error, not a retry loop.
- Buying tokens that aren't on Zerion's index (they won't appear in
  search, so the picker can't reach them). Advanced users can paste
  addresses in a later iteration.
- Fee/gas token mismatches beyond native (e.g. Base's gas is ETH; we
  assume native-gas on both supported chains — accurate for Ethereum
  and Base).

---

## Why this is interesting for the experiment

- First multi-step write flow in the showcase — approve-then-swap
  exposes the reliability seam (receipt polling, partial success) that
  a single-tx Send doesn't.
- First consumer of a second external REST API (0x) alongside the
  existing Zerion — stress-tests the Ktor setup and the Secrets pattern
  at plural keys, not just one.
- Exercises the non-trivial path where our signing layer hands raw,
  untrusted transaction calldata from an aggregator to Trust Wallet
  Core. Crypto-hygiene implication: the signing payload now contains
  opaque contract calls; the assembler must not log or echo it.
- Token search is a realistic async-UX problem (debounce, cancel,
  empty-state, error-state) that proper ViewModel + `StateFlow`
  composition handles cleanly — good contrast against the one-shot
  Send flow.
- Default-token hardcoding demonstrates the "showcase convention" lane:
  deliberate deviation from data-driven purity for UX clarity, with
  the boundary (DefaultTokens file) drawn so a future task can swap
  it for a real token list without touching the UI.

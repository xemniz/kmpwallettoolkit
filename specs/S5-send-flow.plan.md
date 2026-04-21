# S5 — Send flow: Implementation Plan (re-plan, post-T merge)

## 1. Summary

S5 builds the Compose Send flow in `sample-compose`: form (Recipient / Amount / Gas) → Review bottom sheet with hold-to-sign → fetch nonce via `eth_getTransactionCount` → build `Eip1559Transaction` → sign via `Wallet.signEip1559Transaction(chain, tx)` (merged in spec T) → `0x`-hex-prefix the signed envelope bytes → broadcast via `eth_sendRawTransaction` → `navigator.replace(Route.TxStatus(txHash, chainId))`. The spec's previous open question #1 (no EIP-1559 signing entry on `Wallet`) is RESOLVED on `main` — `WalletEip1559Extensions.kt` provides exactly the signature we need, validates `tx.chainId == chain.id`, and returns raw signed EIP-2718 envelope bytes. No STOP remains. Scope stays sample-compose only; no wallet-* edits.

## 2. Load-bearing findings

### 2.1 Signing API (spec open question #1 — CLOSED)

`wallet-evm/commonMain/.../WalletEip1559Extensions.kt` exports:

```
fun Wallet.signEip1559Transaction(chain: SupportedChain, tx: Eip1559Transaction): ByteArray
```

- Requires `chain.id == tx.chainId` (throws `IllegalArgumentException` otherwise).
- Internally routes through `tx.toSigningPayload()` and the engine seam. Returns the **raw signed transaction** (EIP-2718 type-2 envelope) ready for `0x`-prefix + `eth_sendRawTransaction`.
- Visible from sample-compose (`sample-compose/build.gradle.kts` declares `api(project(":wallet-evm"))`).

Implementer must import `xyz.wallet.toolkit.evm.signEip1559Transaction`.

### 2.2 Eip1559Transaction shape (pinned)

```
data class Eip1559Transaction(
    chainId: Long, to: String, valueWei: String = "0",
    maxFeePerGasWei: String, maxPriorityFeePerGasWei: String,
    gasLimit: String, nonce: Long,
    dataHex: String? = null, accessList: List<AccessListEntry> = emptyList(),
)
```

All wei/gas fields are **decimal** strings. `EthFormat.ethDecimalToWei` / `EthFormat.gweiToWei` already return decimal strings — they match.

### 2.3 RPC surface pinned

- `RpcClient.getNonce(address, blockTag="latest"): String` → returns the raw `result` from the node, e.g. `"0x1a"`. Parse: strip `0x` / `0X`, reject empty, `toLong(radix=16)`.
- `RpcClient.sendRawTransaction(rawTransaction: String): String` → returns the tx hash.
- `RpcException(code: Int, message: String)` carries RPC-level errors.
- `RpcClientFactory.forChain(SupportedChain)` hands out pinned clients.

### 2.4 SupportedChain.id

`chain.id` is the EVM chain ID (Long). Use it for both the `Eip1559Transaction.chainId` field and for `Route.TxStatus.chainId`.

### 2.5 Hex encoding

Use `xyz.wallet.toolkit.utils.toHexString(prefix = true)` from wallet-utils (re-exported transitively via wallet-evm → wallet-core → wallet-utils; if not transitively reachable, add `implementation(project(":wallet-utils"))` — but that is a build.gradle edit and therefore **forbidden by this spec's scope**; confirm transitive visibility in Phase 2 before planning any dep edit).

### 2.6 sample-compose target surface

Only `androidLibrary` declared. `commonMain` is effectively Android. `java.math.*` is fine here. **No `commonTest` source set exists** — AC 6 permits defer; no test bootstrap from S5.

### 2.7 App.kt routing

`App.kt` currently routes only `Route.Welcome`; `Route.Send` falls into `PlaceholderScreen`. Per AC 3 (no edits outside `flows/send/`), **App.kt is not edited from S5**. The Send screen will not be reachable via the in-app UI until a routing spec wires it. This is acceptable per the S2–S6 sequencing pattern — flag in PR description.

### 2.8 Navigator / Route.TxStatus

`Route.TxStatus(txHash: String, chainId: Long)` already exists (S1). `Navigator.replace(...)` already exists. Use `replace`, not `push`, so back from TxStatus does not return to the submitted form.

## 3. Files to create

All **new**, source set `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/send/`. No hot files (CLAUDE.md §7) touched. No existing file modified.

| File | Purpose |
|------|---------|
| `SendState.kt` | Holder (NOT data class). Fields: `recipientRaw`, `recipientNormalized: String?`, `amountEth`, `maxFeeGwei`, `maxPriorityGwei`, `chainId: Long`, `submission: SubmissionStatus`. Sealed `SubmissionStatus { Idle; Submitting; Error(userMessage: String) }`. Hand-rolled `override fun toString() = "SendState(redacted)"`. |
| `RecipientField.kt` | Pure validator `validateRecipient(raw: String): ValidationResult` + `normalizeRecipient(raw: String): String` (`.lowercase()`), plus the `@Composable` field (text input + Paste + QR stub button). Regex `^0x[0-9a-fA-F]{40}$`, accepts mixed case. |
| `AmountField.kt` | `@Composable` decimal input with "ETH" suffix. Conversion deferred to assembler. |
| `GasFields.kt` | Two `@Composable` gwei inputs. Inline error "priority must be ≤ max fee" (compare as `BigDecimal`, not string). |
| `ReviewAndSignSheet.kt` | `ModalBottomSheet`. Displays From (truncated wallet address), To (full, as typed casing OK), Amount ETH, Max fee gwei, Priority gwei, Chain chip, computed max-fee display (`BigInteger("21000") * maxFeePerGasWei` → ETH via a local helper or `weiHexToEthDecimal` adapter — display only, never feeds signing). Hold-to-sign button using `detectTapGestures { onPress { tryAwaitRelease(); ... } }` with 1500 ms timer + circular progress ring. Early release cancels. |
| `SendScreen.kt` | Host. `PhoneFrame` wrapper, read-only `ChainChip` driven by `Route.Send(chainId)`, three fields, "Review" `PrimaryButton` (enabled iff: recipient regex passes, `ethDecimalToWei` does not throw on amount with `> 0` check, both gas fields parse, priority ≤ max). Opens `ReviewAndSignSheet`. Accepts a `SendTxAssembler` and `Navigator` from the call site. |
| `SendTxAssembler.kt` | `class SendTxAssembler(private val wallet: Wallet, private val rpc: RpcClient, private val chain: SupportedChain, private val navigator: Navigator)`. `suspend fun assembleAndBroadcast(state: SendState): SendResult`. Sealed `SendResult { data class Success(txHash: String); data class Failure(kind: FailureKind, userMessage: String) }`. Private `parseNonceHex(String): Long` that rejects empty / non-hex, never logs input. |

## 4. Phases

Per-phase gate: `./gradlew :sample-compose:assemble` (and `:sample-compose:compileKotlinJvm` as smoke). Full AC gate: `./gradlew :sample-compose:assemble` green, `:sample-app:assembleDebug` green.

### Phase 1 — State, validation, redaction (no UI, no RPC, no signing)

1. Create `SendState.kt`. Hand-rolled `toString()` returns exactly `"SendState(redacted)"`. Do not use `data class`.
2. Create `RecipientField.kt` with `validateRecipient` + `normalizeRecipient` as top-level pure functions (regex `^0x[0-9a-fA-F]{40}$`, normalize = `.lowercase()`). Composable goes in Phase 2.
3. No test source set exists; do not bootstrap one. Record AC 6 deferral in PR description.
4. Gate: `:sample-compose:assemble` green.

### Phase 2 — UI composition (no RPC, no signing)

1. Create `RecipientField.kt` (composable part), `AmountField.kt`, `GasFields.kt`.
2. Create `SendScreen.kt` with `PhoneFrame`, read-only `ChainChip`, fields, and "Review" `PrimaryButton`. Enablement predicate lives in a pure helper `fun canReview(state: SendState): Boolean`.
3. Create `ReviewAndSignSheet.kt` with display + hold-to-sign. Hold callback `onSign: suspend () -> Unit` is wired but in this phase invokes a no-op stub that sets `SubmissionStatus.Error("Not yet wired")`.
4. Gate: `:sample-compose:assemble` green.

### Phase 3 — Assembler: nonce → build → sign → broadcast → navigate

1. Create `SendTxAssembler.kt`. `assembleAndBroadcast(state)`:
   - `val address = wallet.address(chain)` — never log, never interpolate into errors.
   - Parse `ethDecimalToWei(state.amountEth)` / `gweiToWei(maxFee)` / `gweiToWei(priority)`; on `IllegalArgumentException` → `Failure(InvalidAmount, "Amount is not valid.")` or `Failure(InvalidGas, "Gas values are not valid.")`. No `.message` passthrough.
   - Validate recipient again (defensive; UI already gates). On miss → `Failure(InvalidRecipient, "Recipient address is not valid.")`.
   - `val nonceHex = rpc.getNonce(address, "latest")` inside try/catch(RpcException, Exception) → `Failure(NonceFetchFailed, "Could not fetch account nonce. Try again.")`. Do not include `nonceHex` or `e.message` in the user string.
   - `val nonce = parseNonceHex(nonceHex)`. `"0x0"` → `0L`. Empty body (`"0x"`) or non-hex → `Failure(NonceFetchFailed, ...)`.
   - Build:
     ```
     val tx = Eip1559Transaction(
         chainId = chain.id,
         to = state.recipientNormalized!!,
         valueWei = EthFormat.ethDecimalToWei(state.amountEth),
         maxFeePerGasWei = EthFormat.gweiToWei(state.maxFeeGwei),
         maxPriorityFeePerGasWei = EthFormat.gweiToWei(state.maxPriorityGwei),
         gasLimit = "21000",
         nonce = nonce,
         dataHex = null,
         accessList = emptyList(),
     )
     ```
   - `val signed: ByteArray = wallet.signEip1559Transaction(chain, tx)` inside try/catch(Throwable) → `Failure(SigningFailed, "Signing failed.")`. Do not interpolate `e.message`, `tx`, or `signed`.
   - Local variable holding the hex must not be named in any log/exception path. Encode with `signed.toHexString(prefix = true)` from wallet-utils (verify transitive visibility; if not reachable, STOP and raise — do not edit build.gradle from this spec).
   - `val txHash = rpc.sendRawTransaction(rawHex)` inside try/catch(RpcException) → `Failure(BroadcastFailed(e.code), "Broadcast failed (code ${e.code}).")`. Never put `rawHex` in the message.
   - On success: `navigator.replace(Route.TxStatus(txHash = txHash, chainId = chain.id))`. Return `Success(txHash)`.
2. Wire `ReviewAndSignSheet`'s `onSign` to launch a coroutine that calls the assembler and maps `Failure` to `state.submission = SubmissionStatus.Error(userMessage)`.
3. Gate: `:sample-compose:assemble` + `:sample-app:assembleDebug` green. AC 4/5 greps pass.

### Phase 4 — Final compile smoke

1. `./gradlew :sample-compose:compileKotlinJvm` — fast smoke.
2. `./gradlew :sample-compose:assemble` — AC 1.
3. `./gradlew :sample-app:assembleDebug` — AC 2.
4. Verify PR diff touches zero files outside `flows/send/` (AC 3).
5. Run AC greps:
   - `grep -r "rpc\.getNonce" sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/send/` → match in `SendTxAssembler.kt`.
   - `grep -rE "Random\(|kotlin\.random" sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/send/` → zero.
   - `grep -rE "println|Log\." sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/send/` → manual inspect; no interpolation of `mnemonic`, `privateKey`, `payload`, `signed`, `signedHex`, `rawHex`.

## 5. Crypto constraints — rule-by-rule (CLAUDE.md §4)

- **§4.1 (no logging of secret material).** `SendState.toString()` hard-coded `"SendState(redacted)"`. `SendTxAssembler` never calls `println` / `Log.*` / `throw` with messages interpolating: the mnemonic, the wallet address, the signing payload JSON, `signed: ByteArray`, the `0x`-prefixed raw hex, or the node's nonce response. All `Failure.userMessage` strings are the fixed literals from the spec's table. `BroadcastFailed` carries only the RPC `code` (numeric); `e.message` from `RpcException` is **not** passed through (may echo our raw hex). `SigningFailed` never leaks `e.message` (TWC can echo payload). `ReviewAndSignSheet` must not render the signed hex or the signing payload anywhere.
- **§4.2 (SecureRandom never Random).** No randomness anywhere in `flows/send/`. AC 4 grep enforces.
- **§4.3 (constant-time comparisons).** Not applicable — no secret-byte comparisons in this flow.
- **§4.4 (nonce from RPC, never PRNG).** Every submit fetches `rpc.getNonce(address, "latest")`. No caching, no fallback. `parseNonceHex` rejects malformed input rather than defaulting. AC 4 grep enforces no `Random` in `flows/send/`.
- **§4.5 (golden vectors).** `wallet-evm` owns the `Eip1559Transaction.toSigningPayload()` golden and the signing golden (via spec T). AC 8 forbids duplicating here. No sample-side signing assertions.
- **§4.6 (never weaken existing assertion).** If `wallet.signEip1559Transaction` throws for a reason other than bad user input (e.g. chain-mismatch — should be impossible since we pass `chain.id`), surface `SigningFailed` rather than mapping around the guard. Do not catch `IllegalArgumentException` from the `require(chain.id == tx.chainId)` and retry with massaged data.
- **§4.7 (no case-sensitive address compare).** `recipientNormalized` is lowercase; all internal compares use it. User-entered casing may be preserved in the Review display (presentation only). Regex is case-insensitive.
- **§4.8 (tx serialization is security-relevant).** `Eip1559Transaction` is constructed with **named arguments** in the exact spec-pinned order. `EvmSigningPayload.kt`, `Eip1559Transaction.kt`, `WalletEip1559Extensions.kt` are **not modified**. No re-ordering of fields, no changed defaults. PR diff against `wallet-evm/` must be empty.

## 6. Golden vectors

None in S5. AC 8 explicitly forbids duplicating wallet-evm's golden vector in sample tests. Signing correctness is owned by wallet-evm (`Eip1559Transaction.toSigningPayload()` golden + spec T's `signEip1559Transaction` golden). Do not add a "sanity check" signing assertion in the sample.

## 7. Risks / Open questions

1. **`wallet-utils.toHexString` visibility from sample-compose.** wallet-utils is pulled transitively via wallet-core/wallet-evm `api(...)`. Confirm in Phase 3.1 the import resolves. If it does not, **do not edit** `sample-compose/build.gradle.kts` from this spec (build files are out of scope per AC 3). Surface as a follow-up and inline a local 2-line hex encoder only if explicitly unblocked.
2. **App.kt routing.** Send screen is not reachable from in-app UI post-merge (App.kt routes `Route.Send` to `PlaceholderScreen`). Spec AC 3 forbids editing outside `flows/send/`. Document in PR. Manual reachability deferred to a routing spec.
3. **Computed "Est. max fee" display in Review.** Uses `BigInteger("21000") * maxFeePerGasWei(BigInteger)` and formats via a local `weiDecimalToEthDecimal` (either inline or adapt `weiHexToEthDecimal`). Pure presentation — never fed back into signing.
4. **Coroutine scope for `onSign`.** Use `rememberCoroutineScope()` in the screen; assembler is `suspend` and can be called from any dispatcher. Network call is dispatched by Ktor; do not force a `Dispatchers.IO` hop (multiplatform — `IO` does not exist on iOS and while sample-compose is Android-only now, avoid platform-specific dispatchers in commonMain).
5. **Hold-to-sign gesture.** `androidx.compose.foundation.gestures.detectTapGestures { onPress { ... tryAwaitRelease() ... } }` with `withTimeoutOrNull(1500)` is the pattern. Standard on Android.
6. **Transient `e.message` from Ktor on `getNonce` / `sendRawTransaction`.** Never passthrough — Ktor may include the request URL and body (our raw hex) in its exceptions.

## 8. What this plan does NOT do

- Does **not** edit anything outside `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/send/`.
- Does **not** edit `wallet-core`, `wallet-evm`, `wallet-rpc`, or `wallet-utils`. In particular: does not touch `Wallet.kt`, `WalletEngine.kt`, `Transaction.kt`, `EvmTransactionData.kt`, `Eip1559Transaction.kt`, `EvmSigningPayload.kt`, `WalletEip1559Extensions.kt`, `WalletEvmExtensions.kt`.
- Does **not** edit `App.kt`, `Route.kt`, `Navigator.kt`, `EthFormat.kt`, `RpcClientFactory.kt`, `WalletSession.kt`, S1 UI components (`PhoneFrame`, `ChainChip`, `PrimaryButton`, `MonoText`), or theme files.
- Does **not** edit `gradle/libs.versions.toml`, `settings.gradle.kts`, any `build.gradle.kts`, `gradle.properties`, or `gradle-daemon-jvm.properties`.
- Does **not** add a new dependency.
- Does **not** introduce Koin / Hilt / any DI. `SendTxAssembler` is constructor-injected from `SendScreen`'s call site.
- Does **not** implement QR scanning (stub button only), fee auto-suggest / `eth_feeHistory`, EIP-712, token approvals, ERC-20 transfers, legacy txs, receipt polling (S6), or chain switching inside Send.
- Does **not** add a golden signing vector in sample tests (AC 8).
- Does **not** bootstrap a `commonTest` source set in sample-compose. AC 6 is deferred.
- Does **not** run `./gradlew build` or any repo-wide task. Only `:sample-compose:assemble`, `:sample-compose:compileKotlinJvm`, `:sample-app:assembleDebug`.
- Does **not** normalize casing of the recipient in the Review sheet display (storage/comparison only).
- Does **not** cache the nonce across submits.
- Does **not** pass `RpcException.message` or any underlying exception `.message` into user-facing strings.

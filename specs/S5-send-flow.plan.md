# S5 — Send flow: Implementation Plan

## 1. Summary

S5 builds the Compose Send flow in `sample-compose`: form → review bottom sheet → nonce fetch → tx build → sign → broadcast → navigate to `Route.TxStatus`. The spec's scope (sample-compose only, no wallet-* edits, manual gas, fixed gasLimit=21000, hold-to-sign UX, total SendState redaction) is coherent and matches CLAUDE.md §4/§8.

**However, the spec's open question #1 is fatal for the signing step.** After reading the relevant code (see §5 below), the canonical `Wallet.signTransaction` cannot consume an `Eip1559Transaction` or its `toSigningPayload()` output without editing wallet-core. The spec itself mandates STOP in that case (lines 89–94, 176, and non-goal "Any changes to wallet-core's signing API. ... implementation stops and surfaces"). This plan honours that: the implementer MUST NOT wire signing, MUST NOT invent `wallet.sign(payload)`, and MUST surface to the user before Phase 3.

## 2. Load-bearing findings from reading the code

### 2.1 Signing API shape (spec open question #1)

- `Wallet.signTransaction(chain: SupportedChain, transaction: Transaction): ByteArray` — the only signing entry on `Wallet` (wallet-core/commonMain/.../Wallet.kt).
- `Transaction` is a marker interface. `EvmTransactionData : Transaction` declares **legacy** fields only: `chainId, to, valueWei, gasPriceWei, gasLimit, nonce, dataHex`. **No** `maxFeePerGasWei`, `maxPriorityFeePerGasWei`, or `accessList`.
- `wallet-evm` provides `EvmTransaction` (legacy) which implements `EvmTransactionData`, and `Wallet.signEvmTransaction(chain, EvmTransaction)` — both legacy-only.
- `Eip1559Transaction` is a standalone `@Serializable` data class in wallet-evm. It does **not** implement `Transaction` / `EvmTransactionData`. Its `toSigningPayload()` returns a JSON `ByteArray` — there is no consumer on `Wallet` that accepts it.

**Answers to the prompt's four sub-questions:**

1. Canonical signing API: `Wallet.signTransaction(chain, transaction: Transaction): ByteArray` (plus the legacy convenience `signEvmTransaction`).
2. Can it consume `Eip1559Transaction` / `EvmSigningPayload` output directly? **No.** Neither type satisfies `Transaction`, and the engine receives a `Transaction`, not a `ByteArray` payload.
3. Can the sample bridge it without editing wallet-core? **No, not safely.** Options considered and rejected:
   - "Make `Eip1559Transaction` implement `Transaction` from the sample side" — impossible; `Transaction` lives in wallet-core and `Eip1559Transaction` is in wallet-evm.
   - "Map `Eip1559Transaction` → `EvmTransaction` (legacy) in the sample" — loses `maxFeePerGas`, `maxPriorityFeePerGas`, `accessList`. The legacy signer would produce a *different* signature for a *different* (legacy-typed) transaction than what the user reviewed. This silently weakens security properties (CLAUDE.md opening paragraph; §4.6 "never weaken an existing assertion"). **Forbidden.**
   - "Create a local `Transaction` subtype in sample-compose and pass it in" — the Trust Wallet Core engine actual (androidMain / iosMain) dispatches on known subtypes (`EvmTransactionData`). An unknown `Transaction` subtype is a silent no-op or throws. Not a bridge.
   - "Call `toSigningPayload()` and hand the bytes to an iOS/Android adapter directly" — bypasses `Wallet`, requires adapter internals, and no such entry point exists in sample-compose's dependency surface.
4. **STOP-AND-RAISE.** The plan's Phase 3 is a hard stop: the implementer builds the UI and the assembler up to (not including) `wallet.sign(...)`, then surfaces the gap to the user. No workaround.

### 2.2 RPC pinning

- `RpcClient.getNonce(address, blockTag="latest"): String` — returns the raw JSON-RPC string result, e.g. `"0x1a"` (hex with `0x` prefix, no padding guarantees). Parse: `nonceHex.removePrefix("0x").removePrefix("0X").toLong(radix = 16)`. Empty string after prefix removal is a failure → `NonceFetchFailed`.
- `RpcClient.sendRawTransaction(rawTransaction: String): String` — returns the tx hash string from the node.
- `RpcException(code: Int, message: String)` is thrown for RPC-level errors; catch to produce `BroadcastFailed(rpcCode)` / `NonceFetchFailed`.

### 2.3 EthFormat pinning

Already in S1:
- `ethDecimalToWei(decimal: String): String` — decimal wei (e.g. `"1000000000000000000"`), throws `IllegalArgumentException` on malformed input. (Spec references `ethToWei`; the actual name is `ethDecimalToWei` — use the real name.)
- `gweiToWei(gwei: String): String` — decimal wei, same throwing contract.
- `weiHexToEthDecimal(hex: String): String` — display-only, returns `"—"` on bad input; **not** for signing inputs.

Neither returns hex. The `Eip1559Transaction` fields (`valueWei`, `maxFeePerGasWei`, `maxPriorityFeePerGasWei`, `gasLimit`) are decimal strings per wallet-evm's contract, which matches.

### 2.4 App.kt routing

Current `App.kt` only branches `Route.Welcome` vs `PlaceholderScreen`. S5 needs `Route.Send(chainId)` to resolve to `SendScreen`. **FLAG:** per spec acceptance criterion 3 ("No file outside `.../flows/send/` is modified"), editing `App.kt` is forbidden. Yet without editing it, the Send screen is unreachable and `:sample-app:assembleDebug` succeeding gives no guarantee the flow is actually mounted.

**Resolution for this plan:** build all files under `flows/send/` as specified. Do not edit `App.kt`. Add this as a cross-spec follow-up note — wiring is S1's or a dedicated routing spec's job. The assemble gate remains meaningful (code compiles, UI components are well-formed). This mirrors what S2–S4 must also defer.

### 2.5 sample-compose target surface

`EthFormat` uses `java.math.*`. `sample-compose` is Android/JVM only (no iosMain) after S1. All new files live in `commonMain` but the module effectively compiles JVM + Android. Do not add iOS-specific idioms.

## 3. Files to create

All **new**, source set `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/send/`:

| File | Purpose |
|------|---------|
| `SendState.kt` | Holder class (not data class). `toString()` returns exactly `"SendState(redacted)"`. Fields: `recipientRaw`, `recipientNormalized`, `amountEth`, `maxFeeGwei`, `maxPriorityGwei`, `chainId`, `submission: SubmissionStatus` (sealed: Idle / Submitting / Error(userMessage) — Error carries only the scrubbed user string). |
| `RecipientField.kt` | Text field + paste + QR stub. `validate(String): ValidationResult`. Regex `^0x[0-9a-fA-F]{40}$`. Accepts mixed case, stores lowercased normalized copy. |
| `AmountField.kt` | Decimal input with "ETH" suffix. No conversion here — assembler calls `EthFormat.ethDecimalToWei`. |
| `GasFields.kt` | Two decimal inputs ("Max fee (gwei)", "Priority (gwei)"). Inline "priority must be ≤ max fee" error. |
| `ReviewAndSignSheet.kt` | `ModalBottomSheet`. Displays From (truncated address), To (full), Amount, Max fee, Priority, Chain chip (read-only), computed max fee display (`gasLimit * maxFeePerGasWei` → ETH display, pure presentation). Hold-to-sign button: `pointerInput` with press-detect, 1500 ms timer, circular progress ring. Release-early cancels. |
| `SendScreen.kt` | Screen host. Wraps in `PhoneFrame`, wires three fields + chain chip + "Review" `PrimaryButton` (enabled iff validations pass). Opens `ReviewAndSignSheet`. |
| `SendTxAssembler.kt` | `class SendTxAssembler(private val wallet: Wallet, private val rpc: RpcClient, private val chain: SupportedChain)` with `suspend fun assembleAndBroadcast(state: SendState): SendResult`. Sealed result: `Success(txHash: String) \| Failure(variant: FailureKind, userMessage: String)`. See §5 for crypto rules the implementation must respect. |

Source set note: all files go in `commonMain`. No hot files (CLAUDE.md §7) are touched. `gradle/libs.versions.toml`, `settings.gradle.kts`, `ChainRegistry.kt`, `Chain.kt`, `CLAUDE.md` — **untouched**.

## 4. Phases

Test gate at the end of each phase: `./gradlew :sample-compose:assemble && ./gradlew :sample-app:assembleDebug`. (Per spec AC 1–2; CLAUDE.md §3 says no `build`, so we stay module-scoped.)

### Phase 1 — State + validation + redaction (no UI, no RPC, no signing)

1. Create `SendState.kt` with the holder and `SubmissionStatus` sealed hierarchy.
2. Create `RecipientField.kt`'s pure `validate()` + `normalize()` helpers (exported as top-level functions in the same file) separate from the Composable, so they are testable.
3. If `sample-compose` has a `commonTest` source set already (inspect — AC 6 defers if not), add:
   - `SendStateTest` — asserts `toString()` is exactly `"SendState(redacted)"` and contains none of the field values after they are filled with specific fixtures.
   - `RecipientValidationTest` — fixtures per AC 6 (valid lowercase, valid checksum, invalid hex char, length 39, length 41, missing `0x`).
   - If `commonTest` is not set up, document in PR and skip — do not add a test source set from S5.
4. Gate: assemble passes. No RPC, no crypto.

### Phase 2 — UI composition (no signing, no broadcast)

1. Create `RecipientField.kt`, `AmountField.kt`, `GasFields.kt` composables consuming `SendState`.
2. Create `SendScreen.kt` with `PhoneFrame`, chain chip (read-only from `Route.Send(chainId)`), fields, and the "Review" `PrimaryButton`. Button enabled only when: recipient passes regex, `ethDecimalToWei(amount)` does not throw, both gas values parse, priority ≤ max fee (compare as `BigDecimal`, not as strings).
3. Create `ReviewAndSignSheet.kt` with full display + hold-to-sign button. The hold handler calls a `onSign: () -> Unit` callback; in Phase 2 this callback is a stub that sets `SubmissionStatus.Error(userMessage = "Signing not wired — see plan §5")`.
4. Gate: assemble passes. Screen renders (manual check deferred; not part of the gate).

### Phase 3 — Assembler skeleton up to the STOP boundary

1. Create `SendTxAssembler.kt` implementing:
   - Address from `wallet.address(chain)`.
   - `rpc.getNonce(address, "latest")` → hex parse → Long. On malformed or exception, return `Failure(NonceFetchFailed, "Could not fetch account nonce. Try again.")`. Wrap the parse in a private helper `parseNonceHex(s: String): Long` that rejects empty / non-hex and does NOT log the input.
   - `EthFormat.ethDecimalToWei(state.amountEth)` → catch `IllegalArgumentException` → `Failure(InvalidAmount, "Amount is not valid.")`.
   - `EthFormat.gweiToWei(...)` → same pattern.
   - Build `Eip1559Transaction(chainId = chain.id, to = state.recipientNormalized!!, valueWei = …, maxFeePerGasWei = …, maxPriorityFeePerGasWei = …, gasLimit = "21000", nonce = nonceLong, dataHex = null, accessList = emptyList())`.
   - Call `tx.toSigningPayload()` to materialise the bytes — but the return value is **unused** until signing is resolved.
2. **STOP.** Insert a comment block at the signing call site:
   ```
   // STOP — S5 spec open question #1. Wallet.signTransaction expects Transaction
   // (legacy EvmTransactionData). It cannot consume Eip1559Transaction or the
   // EvmSigningPayload bytes without a wallet-core change. Per spec (lines
   // 89–94, 176) and CLAUDE.md §8, do not proceed. Surface to user.
   ```
   The assembler returns `Failure(SigningFailed, "Signing failed.")` at this boundary so the code still compiles and the gate passes.
3. **The implementer halts here and reports to the user.** Do not move to Phase 4.

### Phase 4 — (Conditional) Signing + broadcast

Only runs if the user, after Phase 3's surface, provides an authoritative resolution path (e.g. "ship S5 with a new spec S5.1 that widens wallet-core" or "stub signing for now"). Without that, Phase 4 is skipped and the PR is marked as partial.

If unblocked:
1. Wire the resolved signing call. Bind result to `signedBytes: ByteArray`.
2. Hex-encode with `0x` prefix using `wallet-utils`' hex helpers (do not hand-roll). Local variable name must not be `signedRawHex` in log or exception paths; it is sensitive until broadcast. See §5.
3. `rpc.sendRawTransaction(rawHex)` → on `RpcException` return `Failure(BroadcastFailed(rpcCode = e.code), "Broadcast failed (code ${e.code}).")`. Do **not** interpolate the submitted hex into the message.
4. On success: `navigator.replace(Route.TxStatus(txHash = hash, chainId = chain.id))`.
5. Gate passes + spec AC 4–5 grep checks pass.

### Phase 5 — Edge-case tests (only if commonTest wired)

- Nonce hex `"0x0"` → `0L`.
- Nonce hex `"0x"` (empty body) → `NonceFetchFailed`, no log of input.
- `RpcException(code = -32000, ...)` from `sendRawTransaction` → `BroadcastFailed(-32000)` with scrubbed message.
- Priority > max fee → Review button disabled; assembler never invoked.
- `SendState.toString()` still `"SendState(redacted)"` after `submission = Error(...)`.

## 5. Crypto constraints — rule-by-rule

CLAUDE.md §4 compliance checklist the implementer must tick in the PR description:

- **§4.1 (no logging of secret material).** `SendState.toString()` hard-coded to `"SendState(redacted)"`. `SendTxAssembler` must not `println`, `Log.*`, or throw with messages interpolating: `wallet.mnemonic`, the computed address (low risk but still sensitive per spec), `EvmSigningPayload` bytes, the output of `toSigningPayload()`, or the signed raw hex. All `Failure.userMessage` strings are fixed literals from the spec's table — no `.message` passthrough from underlying exceptions. `BroadcastFailed` carries only the RPC `code`. Grep check in AC 5 enforces this.

- **§4.4 (nonce from RPC, never PRNG).** Nonce is fetched via `rpc.getNonce(address, "latest")` on every submit. No caching, no `kotlin.random.Random`, no `Random()`, no `SecureRandom`. AC 4 grep enforces absence of `Random` in `flows/send/`. The assembler's private `parseNonceHex` helper never falls back to a generated value.

- **§4.5 (golden vectors for signing).** Spec forbids adding a golden here (AC 8, line 165). The wallet-evm module owns the `Eip1559Transaction.toSigningPayload()` golden. The sample must not duplicate it. No assertion of "signed bytes are 65 bytes" or similar — that is a worthless test (§4.5). The sample's tests cover form validation and redaction only.

- **§4.6 (never weaken an existing assertion).** Mapping `Eip1559Transaction` → legacy `EvmTransaction` to make signing compile would silently sign a different transaction than the user reviewed. This plan forbids it explicitly (§2.1 option 2). If the implementer is tempted to "just use signEvmTransaction with a converted object", that is a §4.6 violation and a STOP condition.

- **§4.7 (no case-sensitive address comparison).** `recipientNormalized` is always the lowercased form. All comparisons (e.g. "is this my address?") go through the lowercased form. The display in `ReviewAndSignSheet` may show the user-entered casing — that is presentation, not comparison. Regex is case-insensitive; EIP-55 verification is explicitly out of scope per spec non-goals.

- **§4.8 (serialization of tx is security-relevant).** `EvmSigningPayload.kt` is **not edited**. `Eip1559Transaction` field ordering / defaults are **not modified**. The assembler constructs `Eip1559Transaction` using named arguments in the order the spec prescribes (§5 in spec). Any drift here invalidates wallet-evm's golden vector. Grep during review: changes under `wallet-evm/` in the PR diff must be zero.

## 6. Golden vectors

None in S5. Explicitly forbidden by spec AC 8 / non-goals. wallet-evm owns the `Eip1559Transaction.toSigningPayload()` vector. Do not duplicate, do not add a "sanity check" vector in sample-compose.

## 7. Risks / Open questions

1. **(BLOCKER) Signing API gap — see §2.1.** Phase 3 stops on this. The implementer must surface to the user before Phase 4 begins. Proposed resolution paths for the user to choose from:
   - (a) New spec S5.1 against wallet-core that adds `Wallet.signEip1559Transaction(chain, Eip1559Transaction): ByteArray` with golden vector coverage in wallet-evm. S5 lands as partial, then S5.1 lands, then a follow-up PR wires Phase 4.
   - (b) Extend `EvmTransactionData` with 1559 fields (breaking change; affects `TrustWalletCoreNativeBridge.*`).
   - (c) Introduce a new `Eip1559TransactionData : Transaction` in wallet-core.
   - None of (a)/(b)/(c) are S5's job. Do not choose; surface and wait.
2. **App.kt routing.** Spec AC 3 forbids editing files outside `flows/send/`, but `App.kt` currently routes `Route.Send` to `PlaceholderScreen`. Flagged in §2.4. The Send screen will not be reachable from the UI in this PR. Acceptable per S1-merge sequencing; flag in PR description.
3. **`commonTest` source set in sample-compose.** Unknown whether S1 wired it. AC 6 permits defer. Inspect in Phase 1.1 and decide; do not bootstrap a test source set from S5.
4. **EthFormat name mismatch.** Spec references `EthFormat.ethToWei`; actual symbol is `EthFormat.ethDecimalToWei`. Plan uses the real name. No code change needed in `EthFormat`.
5. **wallet-utils hex helpers.** Phase 4 uses them for encoding `signedBytes` → `0x…`. Verify the public API (`HexEncoding` or similar) before relying on it; if missing, do not hand-roll — surface.
6. **Hold-to-sign on Compose Multiplatform.** `androidx.compose.foundation.gestures.detectTapGestures` with `onPress { … }` + `tryAwaitRelease()` is the standard path. No platform pitfalls expected on Android; sample-compose has no iOS target.

## 8. What this plan does NOT do

- Does **not** edit anything outside `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/send/`.
- Does **not** edit `wallet-core`, `wallet-evm`, `wallet-rpc`, or `wallet-utils`. In particular: does not touch `Wallet.kt`, `WalletEngine.kt`, `Transaction.kt`, `EvmTransactionData.kt`, `Eip1559Transaction.kt`, `EvmSigningPayload.kt`, `WalletEvmExtensions.kt`.
- Does **not** edit `App.kt`, `Route.kt`, `Navigator.kt`, `EthFormat.kt`, `RpcClientFactory.kt`, `WalletSession.kt`, any S1 UI component, or theme files.
- Does **not** edit `gradle/libs.versions.toml`, `settings.gradle.kts`, any `build.gradle.kts`, `gradle.properties`, or `gradle-daemon-jvm.properties`.
- Does **not** add a new dependency.
- Does **not** introduce Koin / Hilt / any DI (CLAUDE.md §8). `SendTxAssembler` is constructor-injected from the call site.
- Does **not** implement QR scanning, fee auto-suggest, EIP-712, token approvals, ERC-20 transfers, legacy tx, receipt polling (→ S6), or chain switching inside Send.
- Does **not** add a golden signing vector in sample tests (AC 8).
- Does **not** implement Phase 4 without user unblock on §7.1.
- Does **not** run `./gradlew build` or any repo-wide task. Only `:sample-compose:assemble` and `:sample-app:assembleDebug`.
- Does **not** normalize the user-entered casing in the Review sheet display. Normalization is for comparison/storage only.

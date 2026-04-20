# Plan — Spec B: Add RPC methods (`getTransactionReceipt`, `ethCall`, `getCode`)

## Summary
Extend `RpcClient` with three read-side JSON-RPC methods: `getTransactionReceipt` (returns a decoded `TransactionReceipt?`, null when not yet mined), `ethCall` (read-only contract call returning hex), and `getCode` (EOA-vs-contract probe returning hex). The work is confined to `wallet-rpc/commonMain` and `wallet-rpc/commonTest`; it introduces a new `TransactionReceipt` serializable model and a small internal refactor that splits `call` into a `callRaw` helper returning `JsonElement?` so the receipt path can discriminate a JSON `null` result from a missing result and decode an object. The spec's approach is reasonable: the refactor keeps `call` byte-identical on the wire, the receipt model is a plain data class with `ignoreUnknownKeys` to absorb forward-compatible node fields (EIP-1559/2718/4844), and `ethCall`/`getCode` reuse the existing string-returning helper as-is.

## Files to create / modify

Modify:
- `wallet-rpc/src/commonMain/kotlin/xyz/wallet/toolkit/rpc/RpcClient.kt` — add `getTransactionReceipt`, `ethCall`, `getCode`; introduce `private suspend fun callRaw(method, vararg params): JsonElement?`; refactor `call` to wrap `callRaw`. Public surface unchanged beyond the three new methods.
- `wallet-rpc/src/commonMain/kotlin/xyz/wallet/toolkit/rpc/JsonRpcModels.kt` — add `@Serializable data class TransactionReceipt(...)` with the fields listed in the spec, `@SerialName` per JSON-RPC field name, and a one-line KDoc explaining why receipts opt into `ignoreUnknownKeys` (EIP forward-compat).

Create (both in `commonTest`):
- `wallet-rpc/src/commonTest/kotlin/xyz/wallet/toolkit/rpc/RpcClientReceiptTest.kt`
- `wallet-rpc/src/commonTest/kotlin/xyz/wallet/toolkit/rpc/RpcClientCallTest.kt`

Do **not** touch:
- `gradle/libs.versions.toml` (hot file per CLAUDE.md §7; and spec B explicitly forbids new deps).
- `wallet-rpc/build.gradle.kts` (no new libs needed — `kotlinx.serialization.json` and Ktor MockEngine are already wired).
- `wallet-rpc/src/commonTest/kotlin/xyz/wallet/toolkit/rpc/RpcClientTest.kt` — must remain byte-identical to `main` (acceptance criterion 5).
- Anything outside `wallet-rpc`.

No hot files are touched.

Source set note: `wallet-rpc` has no `iosMain` (CLAUDE.md §1); all new code lives in `commonMain`/`commonTest`, and `allTests` resolves to `jvmTest` + `testDebugUnitTest` (CLAUDE.md §3).

## Phases

Each phase ends at a state where `./gradlew :wallet-rpc:allTests` is green. Smoke with `./gradlew :wallet-rpc:compileKotlinJvm` between phases when useful.

### Phase 1 — Internal `callRaw` refactor (no behavior change)
Goal: introduce `callRaw` without changing any public behavior or any test.

- In `RpcClient.kt`, add `private suspend fun callRaw(method: String, vararg params: JsonElement): JsonElement?` containing the existing HTTP post + error-unwrap logic. It returns `response.result` (a `JsonElement?`) and still throws `RpcException` on `response.error`. It does **not** throw on a `null` result — the existing missing-result throw moves into `call`.
- Rewrite the existing `private suspend fun call(...)` as a thin wrapper: `callRaw(method, *params) ?: throw RpcException(-1, "Missing RPC result")` then `.jsonPrimitive.content`.
- Public methods (`getBalance`, `getNonce`, `estimateGas`, `sendRawTransaction`) are untouched.
- Gate: run `./gradlew :wallet-rpc:allTests`. `RpcClientTest` must pass unchanged. Diff `RpcClientTest.kt` against `main` — must be byte-identical (acceptance criterion 5).

### Phase 2 — `TransactionReceipt` model + `getTransactionReceipt` + failing tests first
Goal: land the new API with tests that fail before implementation, then pass.

1. Write `RpcClientReceiptTest.kt` first (failing). Test cases:
   - Successful receipt with all required fields present → every field on `TransactionReceipt` has the expected value; `logs` is empty list.
   - Node returns `{"result": null}` → `getTransactionReceipt(...)` returns `null`.
   - Receipt with `status: "0x0"` (reverted on-chain) → does not throw; returned receipt has `status == "0x0"`.
   - Contract-creation receipt: `to: null`, `contractAddress: "0x..."` → `to == null`, `contractAddress == "0x..."`.
   - **Drift guard / unknown-field tolerance:** receipt JSON contains an unexpected top-level `blobGasUsed: "0x0"` (simulating EIP-4844). Parse must succeed and ignore the field. This is the spec B equivalent of a golden-vector drift guard.
2. In `JsonRpcModels.kt`, add:
   ```
   @Serializable
   data class TransactionReceipt(
       val transactionHash: String,
       val transactionIndex: String,
       val blockHash: String,
       val blockNumber: String,
       val from: String,
       val to: String? = null,
       val contractAddress: String? = null,
       val gasUsed: String,
       val cumulativeGasUsed: String,
       val status: String,
       val logsBloom: String,
       val logs: List<JsonElement> = emptyList(),
   )
   ```
   KDoc on the class: one line explaining that decoding is deliberately lenient (`ignoreUnknownKeys = true` on the decode path) because Ethereum clients evolve receipt fields (EIP-1559 `effectiveGasPrice`, EIP-2718 `type`, EIP-4844 `blobGasUsed`).
3. In `RpcClient.kt`, add:
   ```
   suspend fun getTransactionReceipt(txHash: String): TransactionReceipt? {
       val raw = callRaw("eth_getTransactionReceipt", JsonPrimitive(txHash)) ?: return null
       if (raw is JsonNull) return null
       val lenient = if (json.configuration.ignoreUnknownKeys) json
                     else Json(from = json) { ignoreUnknownKeys = true }
       return lenient.decodeFromJsonElement(TransactionReceipt.serializer(), raw)
   }
   ```
   Implementation notes:
   - `callRaw` returns `response.result` as `JsonElement?`. When the server sends `"result": null`, kotlinx.serialization deserializes that to `JsonNull`, not Kotlin `null`. Handle both: Kotlin `null` (field absent) AND `JsonNull` (field present but JSON null) → return Kotlin `null`. The existing `call` path is unaffected because it only runs after a non-null path.
   - The "lenient copy of `json`" dance avoids changing the user-supplied `Json` instance. If the caller already set `ignoreUnknownKeys = true`, reuse it; otherwise derive a lenient copy once. Cache on the class if this ends up on a hot path (not required for the spec).
4. Gate: run `./gradlew :wallet-rpc:allTests`. All five receipt tests must pass; `RpcClientTest` still green and unchanged.

### Phase 3 — `ethCall` + `getCode` + tests
Goal: add the two string-returning methods.

1. Write `RpcClientCallTest.kt` (failing first). Test cases:
   - `ethCall` returns exact hex string. Mock `{"result": "0x000...2a"}` → assert the returned string is that exact value, `0x` prefix preserved.
   - `ethCall` error-path: mock `{"error": {"code": 3, "message": "execution reverted"}}` → assert `RpcException` thrown with `code == 3` and message contains `"execution reverted"`. Uses existing `RpcException` — do not introduce a new type.
   - `getCode` EOA-case: mock `{"result": "0x"}` → returned string is exactly `"0x"`.
   - `getCode` contract-case: mock `{"result": "0x6080604052..."}` → returned string equals the mock value.
2. In `RpcClient.kt`, add:
   ```
   suspend fun ethCall(call: RpcCall, blockTag: String = "latest"): String =
       call("eth_call", json.encodeToJsonElement(call), JsonPrimitive(blockTag))

   suspend fun getCode(address: String, blockTag: String = "latest"): String =
       call("eth_getCode", JsonPrimitive(address), JsonPrimitive(blockTag))
   ```
   Note: the public method name `ethCall` shadows the private `call` helper only inside its own body; qualify the recursive call to the helper with `this.call(...)` if Kotlin resolution is ambiguous. If naming clash is awkward, keep the private helper's name (`call`) and disambiguate at the call site — do not rename the private helper, which would enlarge the diff beyond spec intent.
3. Gate: `./gradlew :wallet-rpc:allTests` green.

### Phase 4 — Final verification
- `./gradlew :wallet-rpc:allTests` — green.
- `./gradlew :wallet-rpc:compileKotlinJvm` — green.
- Diff `RpcClientTest.kt` vs. `main` — byte-identical.
- Diff `libs.versions.toml` vs. `main` — byte-identical.
- Confirm `RpcException` is unchanged (acceptance criterion 6).
- Confirm no file outside `wallet-rpc/` was modified.

No iOS compile check is required for this module (no iOS targets — CLAUDE.md §1).

## Golden vectors

**Not applicable.** Spec B is RPC plumbing, not signing, serialization of a signing input, or key derivation. CLAUDE.md §4.5 "golden vectors" requirement does not apply: there is no cryptographic output to pin.

The drift-guard equivalent for this task is the **unknown-field tolerance test** in Phase 2 (`blobGasUsed` EIP-4844 simulation). That test protects against two failure modes: (a) the decode regressing to strict mode and breaking against real-world nodes; (b) a future refactor accidentally adding unknown fields to the *required* set. The test asserts successful parse plus all known fields still equal their expected values — it does not assert anything about the unknown field itself (it is ignored by contract).

No other cryptographic or signing vectors apply here.

## Risks / Open questions

1. **`JsonNull` vs. Kotlin `null` at the `callRaw` boundary.** When Ethereum returns `"result": null`, the deserialized `JsonRpcResponse<JsonElement>.result` will be `JsonNull` (a non-null `JsonElement`), not Kotlin `null`. The only path that yields Kotlin `null` from `callRaw` is a missing `result` field entirely. Real nodes for `eth_getTransactionReceipt` send `"result": null` (field present), so `getTransactionReceipt` must treat both as "not mined yet". Plan: check both in Phase 2. Flagging here so the implementer does not assume one or the other.
2. **`Json` instance mutation.** The user-supplied `json` on `RpcClient` may or may not have `ignoreUnknownKeys = true`. Re-using it as-is risks strict decode failures against real nodes for receipts. Plan above derives a lenient copy once; confirm this doesn't violate any implicit contract the caller has about the `json` parameter. Alternative: document that `RpcClient` requires `ignoreUnknownKeys = true` for receipts and fail fast. Going with the "derive a lenient copy" option because it is the least-surprising default and matches the spec's intent (receipts are the forward-compat-sensitive surface).
3. **Public method name `ethCall` shadowing private `call`.** Kotlin resolves `call(...)` inside `ethCall(...)` to the private helper by arity/types, but a reader-hostile name collision is worth calling out. If the implementer finds a compile ambiguity, disambiguate at the call site rather than renaming (keeps diff minimal and leaves existing `call` helper name stable for Phase 1's refactor).
4. **Revert data surfacing.** The spec says `ethCall` surfaces RPC revert errors via existing `RpcException`. `JsonRpcError.data` already exists in the model but is not propagated through `RpcException`. The plan does **not** change this — the spec says "do not invent a new exception type" and does not ask to widen `RpcException`. Out of scope.
5. **`logs` opacity.** Keeping `logs: List<JsonElement>` means a caller who wants structured logs must decode again. The spec is explicit that log/ABI decoding is out of scope, so this is accepted.

## What this plan does NOT do

- Does not add polling / `waitForReceipt` helpers.
- Does not decode `logs` into a structured `Log` data class. Opaque `List<JsonElement>` only.
- Does not add WebSocket / `eth_subscribe`.
- Does not add retry, backoff, or timeout policy.
- Does not widen `RpcException` with a `data` field or introduce new exception types.
- Does not modify `RpcCall` or any other existing model.
- Does not modify `RpcClientTest.kt` — it stays byte-identical to `main`.
- Does not modify `libs.versions.toml`, `settings.gradle.kts`, or `wallet-rpc/build.gradle.kts`.
- Does not touch `wallet-core`, `wallet-evm`, `wallet-utils`, `sample-app`, `sample-compose`, or `iosApp`.
- Does not add iOS source sets to `wallet-rpc`.
- Does not add sample/demo usage of the new methods.
- Does not run `./gradlew build` — scoped module commands only (CLAUDE.md §3, §8).

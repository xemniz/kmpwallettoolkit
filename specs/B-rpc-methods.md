# Spec B — Add RPC methods: `eth_getTransactionReceipt`, `eth_call`, `eth_getCode`

## Goal
Extend `RpcClient` with three standard JSON-RPC methods that any usable EVM wallet needs:
- `getTransactionReceipt(txHash: String)` — after `sendRawTransaction` returns, the caller needs to poll for inclusion.
- `ethCall(call: RpcCall, blockTag: String = "latest")` — read-only contract call, returns hex bytes.
- `getCode(address: String, blockTag: String = "latest")` — used to distinguish EOAs from contracts.

## Module(s) touched
- `wallet-rpc` — only.
- No changes to `wallet-core`. No changes to `ChainRegistry`. No changes to `libs.versions.toml`.

## Files expected to change
- `wallet-rpc/src/commonMain/kotlin/xyz/wallet/toolkit/rpc/RpcClient.kt` (add methods)
- `wallet-rpc/src/commonMain/kotlin/xyz/wallet/toolkit/rpc/JsonRpcModels.kt` (add a `TransactionReceipt` data class)
- `wallet-rpc/src/commonTest/kotlin/xyz/wallet/toolkit/rpc/RpcClientReceiptTest.kt` (new)
- `wallet-rpc/src/commonTest/kotlin/xyz/wallet/toolkit/rpc/RpcClientCallTest.kt` (new)

## Design

### `getTransactionReceipt`
- Signature: `suspend fun getTransactionReceipt(txHash: String): TransactionReceipt?`
- Returns `null` when the node returns `result: null` (tx not yet mined). This is a real node behavior, not an error.
- Returns a `TransactionReceipt` otherwise.
- Uses `eth_getTransactionReceipt` with `[txHash]`.

### `TransactionReceipt` data class
`@Serializable data class TransactionReceipt(...)` with at minimum:
- `transactionHash: String`
- `blockHash: String`
- `blockNumber: String` (hex-quantity — keep as String, don't parse)
- `from: String`
- `to: String?` (null for contract-creation receipts)
- `contractAddress: String?` (non-null only for contract creation)
- `gasUsed: String`
- `cumulativeGasUsed: String`
- `status: String` ("0x1" success / "0x0" reverted — keep as String)
- `logsBloom: String`
- `transactionIndex: String`
- `logs: List<JsonElement> = emptyList()` — opaque pass-through; full log parsing is out of scope.

Use `@SerialName` where the JSON-RPC field name differs from the Kotlin property name. Add `ignoreUnknownKeys = true` is **already** on the test client — in production code, do not assume it; use explicit `@SerialName` for every field and let unknown fields fail loudly in the receipt parser so we don't silently eat a field a new EIP adds.

Actually: relax to `Json { ignoreUnknownKeys = true }` for the receipt decode specifically, because Ethereum clients add fields (e.g. `effectiveGasPrice` EIP-1559, `type` EIP-2718, `blobGasUsed` EIP-4844) and receipts are the most forward-compatibility-sensitive surface. Document this decision in a one-line KDoc on the `TransactionReceipt` class.

### `ethCall`
- Signature: `suspend fun ethCall(call: RpcCall, blockTag: String = "latest"): String`
- Returns the hex-encoded result (`"0x..."`), including `"0x"` for empty results. Do **not** strip the `0x` prefix.
- Uses `eth_call` with `[call, blockTag]`.
- Surfaces RPC revert errors via the existing `RpcException` machinery — do not invent a new exception type.

### `getCode`
- Signature: `suspend fun getCode(address: String, blockTag: String = "latest"): String`
- Returns `"0x"` for EOAs (no bytecode), hex-encoded bytecode otherwise.
- Uses `eth_getCode` with `[address, blockTag]`.

### Internal refactor
The existing `private suspend fun call(method, vararg params): String` unwraps the result as a `JsonPrimitive`. `getTransactionReceipt` needs the raw `JsonElement` (to decode into `TransactionReceipt` or detect `null`). Introduce a second private helper `private suspend fun callRaw(method, vararg params): JsonElement?` that returns the raw element (or null) and **does not** call `.jsonPrimitive`. Refactor the existing `call` to wrap `callRaw`. Keep the public surface unchanged.

## Acceptance criteria

1. `./gradlew :wallet-rpc:allTests` — green.
2. `./gradlew :wallet-rpc:compileKotlinJvm` — green.
3. `RpcClientReceiptTest` covers:
   - Successful receipt with all fields present.
   - `result: null` case returns `null` (tx not yet mined).
   - Receipt with `status: "0x0"` (reverted tx) — does not throw, returns the receipt with status preserved.
   - Receipt for a contract-creation tx (`to: null`, `contractAddress: "0x..."`).
   - Receipt with an unknown top-level field (simulate EIP-4844 `blobGasUsed`) — parses successfully, unknown field is ignored (the `ignoreUnknownKeys` contract).
4. `RpcClientCallTest` covers:
   - `ethCall` returns exact hex string.
   - `ethCall` surfaces an RPC error (e.g. `{"error": {"code": 3, "message": "execution reverted"}}`) as `RpcException` with code 3.
   - `getCode` returns `"0x"` for EOA case.
   - `getCode` returns a non-empty hex bytecode string for contract case.
5. Existing `RpcClientTest` passes unchanged. Diff that file — it must be byte-identical to main.
6. `RpcException` was not modified.
7. No new dependency in `libs.versions.toml`.

## Non-goals
- Polling helpers / `waitForReceipt`. This is single-shot RPC plumbing; polling belongs to a future task.
- Log decoding / ABI parsing. `logs: List<JsonElement>` is opaque pass-through.
- WebSocket subscriptions (`eth_subscribe`).
- Retry / backoff policy.
- A new chain registry entry.
- Signing or transaction construction (that's spec A).

## Why this is interesting for the experiment
- Zero platform coupling, zero expect/actual — another control for "parallel agent succeeds on easy-mode task".
- Exercises Ktor MockEngine test discipline — easy to write a test that "passes" by being too lenient. The receipt-with-unknown-field test specifically pushes back on that.
- File-disjoint from spec A — safe to run in parallel.
- Touches `JsonRpcModels.kt` which spec A does not touch. No hot files.

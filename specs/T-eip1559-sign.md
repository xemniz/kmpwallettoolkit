# Spec T — Wallet.signEip1559Transaction entry point

## Goal
Expose a signing entry point that accepts an `Eip1559Transaction` and returns a signed raw-tx byte array suitable for `eth_sendRawTransaction`. Today the toolkit has two halves that don't meet: `Eip1559Transaction.toSigningPayload(): ByteArray` (JSON for TWC) in `wallet-evm`, and `Wallet.signTransaction(chain, transaction: Transaction)` in `wallet-core` that only accepts the legacy `EvmTransactionData` shape. A showcase `Send` flow that actually broadcasts a transaction cannot complete without this bridge.

This is a **crypto-adjacent, multi-platform bridge** task. It touches the expect/actual seam in `wallet-core` and adds an extension in `wallet-evm`. A golden-vector test is mandatory per CLAUDE.md §4.5.

## Module(s) touched
- `wallet-core` — expect object + Android, JVM, iOS actuals.
- `wallet-evm` — new `Wallet.signEip1559Transaction` extension + golden-vector test.

## Files expected to change
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.kt` — extend the `expect object` with `signEip1559(mnemonic, chain, signingPayloadJson: ByteArray): ByteArray`.
- `wallet-core/src/androidMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.android.kt` — Android actual using Trust Wallet Core's JSON-based signing. The primary path is `AnySigner.signJSON(json: String, privateKey: ByteArray, coinType: CoinType)` — verify the exact JNI method name and signature in the planner before locking. The implementation derives the private key via `HDWallet(mnemonic, "").getKeyForCoin(coinType).data()`, calls `signJSON`, and returns the resulting hex string parsed as `ByteArray`. Uses the existing `SupportedChain.toCoinType()` private helper.
- `wallet-core/src/jvmMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.jvm.kt` — stub actual throwing `NotImplementedError` (matches existing jvm stubs).
- `wallet-core/src/iosMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.ios.kt` — delegate to the existing `TrustWalletCoreIosAdapter` hook pattern. Extend the adapter interface with the new method or, if the adapter is a function-type hook, add a companion hook. Implementer must read the existing iOS adapter shape before committing to a signature.
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/WalletEngine.kt` — extend `WalletEngine` interface with `fun signEip1559(mnemonic, chain, signingPayloadJson: ByteArray): ByteArray` (and its default or every-implementor actual).
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreWalletEngine.kt` — implement the new method by delegating to `TrustWalletCoreNativeBridge.signEip1559`.
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/UnsupportedWalletEngine.kt` (if it exists) — throw `NotImplementedError` for the new method. If the class doesn't exist, skip.
- `wallet-evm/src/commonMain/kotlin/xyz/wallet/toolkit/evm/WalletEip1559Extensions.kt` (new) — `fun Wallet.signEip1559Transaction(chain: SupportedChain, tx: Eip1559Transaction): ByteArray`. Composes `tx.toSigningPayload()` + calls the wallet-core engine hook.
- `wallet-evm/src/commonTest/kotlin/xyz/wallet/toolkit/evm/Eip1559SigningGoldenVectorTest.kt` (new) — golden-vector test pinning a known (mnemonic, tx) → signed-hex triple.

**Do not modify:**
- `Chain.kt`, `ChainRegistry.kt` (hot files §7).
- `Eip1559Transaction.kt` or `EvmSigningPayload.kt` — adjacent serialization files; do not drift their output.
- Any existing wallet-evm test file (`Eip1559GoldenVectorTest.kt`, `Eip1559SigningPayloadTest.kt`, etc.).
- Any sample-* module.

## Design

### Bridge contract
```kotlin
// TrustWalletCoreNativeBridge.kt (commonMain, expect)
fun signEip1559(
    mnemonic: String,
    chain: SupportedChain,
    signingPayloadJson: ByteArray,
): ByteArray
```

`signingPayloadJson` is the exact output of `Eip1559Transaction.toSigningPayload()`. The bridge is responsible for converting that JSON into TWC's expected form and invoking the signer. The bridge returns the **raw signed transaction bytes** (EIP-2718 envelope + RLP) ready for `eth_sendRawTransaction` after hex-prefixing.

### Engine
`WalletEngine` gains `signEip1559(...)` so `Wallet.signEip1559Transaction(...)` in wallet-evm never reaches into the bridge directly — it goes through the engine abstraction for parity with the existing `signTransaction` path.

### wallet-evm extension
```kotlin
// WalletEip1559Extensions.kt (commonMain)
fun Wallet.signEip1559Transaction(chain: SupportedChain, tx: Eip1559Transaction): ByteArray {
    require(tx.chainId == chain.id) { "Transaction chainId ${tx.chainId} does not match ${chain.displayName} (${chain.id})" }
    return engine.signEip1559(mnemonic = this.mnemonic, chain = chain, signingPayloadJson = tx.toSigningPayload())
}
```

The `engine` field on `Wallet` is currently `private`. Options: (a) add an internal accessor `internal val Wallet.engine: WalletEngine` via `@PublishedApi` or a `package-private` friend; (b) move `signEip1559Transaction` into the `Wallet` class itself. Planner decides, but **do not** make `engine` public. If option (b), the file moves to `wallet-core` and wallet-evm just uses `wallet.signEip1559Transaction(...)` — cleaner. Prefer (b) unless there's a strong module-boundary reason.

Correction: Prefer (a) via an `internal` property and a `wallet-evm`-visible friend module declaration. If that's not feasible, fall back to (b) and move the extension into wallet-core. The planner owns this decision; flag whichever path is chosen.

### Golden vector
Use a test vector from a trusted source. The recommended source is **viem's EIP-1559 test fixtures** or **Trust Wallet Core's own `EthereumSigner` unit tests**. The planner must identify a specific (mnemonic, transaction fields, expected raw-signed-hex) triple and cite its URL in the plan. Do not invent values.

If no public vector precisely matches the `Eip1559Transaction` schema used here, the test **signs twice and asserts determinism**: sign the same payload with the same key twice; outputs must be byte-identical. Combined with the existing `Eip1559GoldenVectorTest` asserting payload-JSON stability, this is a structural guarantee even without a third-party reference vector. Planner must clearly document which of the two options was chosen.

## Acceptance criteria
A task is **not done** until every one of these passes.

1. `./gradlew :wallet-core:allTests` — green.
2. `./gradlew :wallet-evm:allTests` — green.
3. `./gradlew :wallet-core:compileKotlinJvm :wallet-core:compileKotlinIosX64` — green.
4. `Eip1559SigningGoldenVectorTest` exists, asserts the exact hex output (or determinism — see Design), and is NOT `@Ignore`'d.
5. Existing `Eip1559GoldenVectorTest` (if present) and `Eip1559SigningPayloadTest` pass unmodified. Diff those files — zero changes.
6. `WalletTest.kt` (if present) passes unmodified. Zero changes.
7. JVM actuals throw `NotImplementedError` with a clear message. iOS actuals route through the adapter pattern.
8. `Wallet.signEip1559Transaction(chain, tx)` requires `tx.chainId == chain.id` and throws `IllegalArgumentException` otherwise. Unit test asserts this.
9. No new crypto library added to `libs.versions.toml`. Trust Wallet Core already covers signing.
10. `:sample-app:assembleDebug` still green (end-to-end link check on Android JNI path).

## Non-goals
- Legacy (pre-1559) tx signing refactor — the existing `signTransaction` path stays.
- Fee estimation / `eth_maxPriorityFeePerGas` helpers.
- Address checksum (EIP-55) normalization.
- Any `Wallet.signEip4844Transaction` (type-3, blobs).
- Changes to `Eip1559Transaction`'s fields or serialization.
- iOS adapter implementation in a runtime host — stub/hook only; the sample app is Android-only so iOS remains not-actually-runnable.
- Sample-wallet integration — S5 will consume this method.

## Why this is interesting for the experiment
- **Cross-module expect/actual extension** — the first spec in this set to grow both wallet-core's bridge contract and wallet-evm's API. Good probe of whether the planner reads the seam on every actual target before committing.
- **Golden-vector discipline under uncertainty** — no in-repo EIP-1559 signing vector exists. Either the planner finds a public one (correct, requires judgment) or chooses the determinism-pair fallback (acceptable, must be clearly flagged). Either outcome reveals whether the agent respects §4.5 or invents a vector.
- **Private-field access across modules** — the `Wallet.engine` field is `private`. Forces a design choice (internal + friend, or move the method). Tests whether the planner proposes the right KMP-idiomatic option vs. a leaky one.
- **Signing routing bug from D's plan** — D's planner flagged that `signEvmTransaction` hardcodes `CoinType.ETHEREUM`. T's Android actual must NOT replicate that pattern; it must use `chain.toCoinType()`. This is a correctness tripwire: if T's reviewer catches BSC/Polygon routing correctness up front, we've learned something about what D's reviewer missed.

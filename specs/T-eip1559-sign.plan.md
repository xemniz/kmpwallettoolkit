# Plan — T-eip1559-sign

## 1. Summary

Add a `Wallet.signEip1559Transaction(chain, tx)` entry point that takes an `Eip1559Transaction`, runs it through a new `WalletEngine.signEip1559` method, and returns the raw EIP-2718 signed bytes suitable for `eth_sendRawTransaction`. The plumbing mirrors the existing `signTransaction` path: common-main contract + engine method + expect/actual bridge, with the existing iOS adapter pattern extended for a new call.

The spec's approach is mostly reasonable, but it has one load-bearing ambiguity that must be resolved in the plan: `Eip1559Transaction.toSigningPayload()` produces a **kotlinx-serialization JSON** in our own schema, whereas Trust Wallet Core's `AnySigner.signJSON` expects proto3-JSON matching `Ethereum.SigningInput`. The two schemas are not interchangeable (see Risks §1). The Android actual therefore cannot forward the payload bytes blindly — it must deserialize our JSON and build a protobuf `Ethereum.SigningInput` with EIP-1559 fields (`txMode = Enveloped`, `maxFeePerGas`, `maxInclusionFeePerGas`). This is flagged rather than silently rewritten.

## 2. Files to create / modify

All file paths are absolute.

### wallet-core (all source sets)

- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.kt` — extend `expect object` with `signEip1559(mnemonic, chain, signingPayloadJson: ByteArray): ByteArray`.
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/WalletEngine.kt` — add `fun signEip1559(mnemonic, chain, signingPayloadJson: ByteArray): ByteArray` to the interface; add an override in `UnsupportedWalletEngine` that `error(...)`s.
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreWalletEngine.kt` — no change (it's an `expect class`; each actual adds the override).
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/Wallet.kt` — add `fun signEip1559Transaction(chain, signingPayloadJson: ByteArray): ByteArray` that delegates to `engine.signEip1559(...)`. See §3.1 for rationale (option (b)).
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreIosBridge.kt` — extend `TrustWalletCoreIosAdapter` with `fun signEip1559(mnemonic, chain, signingPayloadJson: ByteArray): String` (hex). Keeping with the existing adapter shape (interface with named methods). NOT a hot file; safe to modify.
- `wallet-core/src/androidMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.android.kt` — actual `signEip1559`: deserializes the kotlinx JSON into a private DTO, derives the private key via `HDWallet(mnemonic, "").getKeyForCoin(chain.toCoinType()).data()`, builds an `Ethereum.SigningInput` with `setTxMode(Ethereum.TransactionMode.Enveloped)`, `setMaxFeePerGas(...)`, `setMaxInclusionFeePerGas(...)`, `setChainId(...)`, `setNonce(...)`, `setGasLimit(...)`, `setToAddress(...)`, `setPrivateKey(...)`, transfer+data, then `AnySigner.sign(input, chain.toCoinType(), Ethereum.SigningOutput.parser())`. Returns `output.encoded.toByteArray()`. Uses `chain.toCoinType()` — NOT hardcoded `CoinType.ETHEREUM` (see Risks §4).
- `wallet-core/src/androidMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreWalletEngine.android.kt` — override `signEip1559` → delegate to native bridge.
- `wallet-core/src/jvmMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.jvm.kt` — stub actual `signEip1559` → `jvmNotSupported("signEip1559")`.
- `wallet-core/src/jvmMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreWalletEngine.jvm.kt` — override `signEip1559` → delegate to native bridge (matches existing pattern).
- `wallet-core/src/iosMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.ios.kt` — actual `signEip1559` → `requireIosAdapter(method = "signEip1559").signEip1559(mnemonic, chain, signingPayloadJson)` → `hexToByteArray()`.
- `wallet-core/src/iosMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreWalletEngine.ios.kt` — override `signEip1559` → delegate to native bridge.

### wallet-evm

- `wallet-evm/src/commonMain/kotlin/xyz/wallet/toolkit/evm/WalletEip1559Extensions.kt` (new) — `fun Wallet.signEip1559Transaction(chain, tx: Eip1559Transaction): ByteArray`. Validates `tx.chainId == chain.id`, calls `Wallet.signEip1559Transaction(chain, tx.toSigningPayload())`.
- `wallet-evm/src/commonTest/kotlin/xyz/wallet/toolkit/evm/Eip1559SigningGoldenVectorTest.kt` (new) — determinism-pair test (see §4) + chainId-mismatch test.

### Hot-file / do-not-touch check (CLAUDE.md §7)

None of the files above are listed in CLAUDE.md §7 (`gradle/libs.versions.toml`, `settings.gradle.kts`, `Chain.kt`, `ChainRegistry.kt`, `CLAUDE.md`, `.claude/agents/*`). No new dep is needed (TWC already vendored). Clear to proceed.

## 3. Design decisions (resolved)

### 3.1 Where does `signEip1559Transaction` live? — Option (b)

Chosen: **place the method on the `Wallet` class itself** (wallet-core, commonMain). The wallet-evm extension then becomes a thin convenience that calls `tx.toSigningPayload()` and forwards.

Why not (a) `internal val engine` + `@PublishedApi`: Kotlin's `internal` is per-module. `wallet-evm` is a separate gradle module, so `internal` would not be visible there, and Kotlin has no friend-module declaration. `@PublishedApi internal` + an inline extension works but leaks `WalletEngine` into wallet-evm's public ABI surface and is fragile under R8. (b) is cleaner and matches the existing `signTransaction` pattern on `Wallet`.

Why not (c): splitting half the logic across modules duplicates the chainId-matches-chain guard.

Shape:
```kotlin
// Wallet.kt (commonMain, wallet-core)
fun signEip1559Transaction(chain: SupportedChain, signingPayloadJson: ByteArray): ByteArray =
    engine.signEip1559(mnemonic = mnemonic, chain = chain, signingPayloadJson = signingPayloadJson)

// WalletEip1559Extensions.kt (commonMain, wallet-evm)
fun Wallet.signEip1559Transaction(chain: SupportedChain, tx: Eip1559Transaction): ByteArray {
    require(chain.id == tx.chainId) {
        "Chain mismatch: wallet chain ${chain.id} differs from transaction chain ${tx.chainId}"
    }
    return signEip1559Transaction(chain = chain, signingPayloadJson = tx.toSigningPayload())
}
```

Contradicts the spec's narrow preference for (a); flagged in Risks §6 per planner instructions.

### 3.2 iOS adapter shape

The existing `TrustWalletCoreIosAdapter` is a plain Kotlin `interface` with named methods (`createMnemonic`, `deriveAddress`, `signEvmTransaction`). Extend it with one more method: `fun signEip1559(mnemonic: String, chain: SupportedChain, signingPayloadJson: ByteArray): String`. Returns hex. Matches the existing shape; no separate companion hook needed. This is a breaking change to anyone who implemented the adapter, but per the spec "iOS remains not-actually-runnable" and no host exists yet.

### 3.3 Android signing entry point — proto path, not signJSON

Verified via TWC source and upstream Android tests: `AnySigner.signJSON(json: String, key: ByteArray, coinType: Int): String` exists. However it expects **proto3-JSON of `Ethereum.SigningInput`**, not our `Eip1559Transaction` kotlinx JSON. Field names differ (`txMode`, `maxInclusionFeePerGas` vs. our `maxPriorityFeePerGasWei`), the transfer body is nested, and amounts are base64-encoded bytes in proto3-JSON. Translating our JSON to TWC's JSON at the wire level would be fragile.

Chosen path: Android deserializes `signingPayloadJson` via kotlinx-serialization into a private `AndroidEip1559Dto` (mirroring `Eip1559Transaction`'s fields — kept private to `androidMain` so wallet-evm's type does not leak into wallet-core), and builds a protobuf `Ethereum.SigningInput` directly. This reuses the existing `decimalToByteString` / `toByteString` helpers.

Key protobuf fields for EIP-1559:
- `txMode = Ethereum.TransactionMode.Enveloped`
- `maxFeePerGas` (bytes)
- `maxInclusionFeePerGas` (bytes) ← maps from our `maxPriorityFeePerGasWei`
- `gasLimit`, `nonce`, `chainId`, `toAddress`, `privateKey`
- `transaction.transfer` with `amount` and optional `data`
- `accessList` entries if non-empty (see Risks §3)

### 3.4 Chain-to-CoinType routing correctness

Every call in `signEip1559` uses `chain.toCoinType()`, not `CoinType.ETHEREUM`. This is the tripwire the spec calls out (D's planner missed it in the legacy path). Additionally, the sign call is `AnySigner.sign(input, chain.toCoinType(), ...)` — both the HDWallet key derivation AND the signer coin type must match, otherwise BSC / Polygon derivations would use the wrong BIP-44 path.

Note: TWC uses a single Ethereum signing proto for all EVM chains; passing `CoinType.SMARTCHAIN` to `AnySigner.sign` works because the signer reads `chainId` from the proto. Verified via existing `signEvmTransaction`. Flagged in Risks §2 in case a future TWC version diverges.

## 4. Golden vectors

**Status: no direct in-schema golden vector found; falling back to determinism-pair per spec Design section.**

Candidates evaluated:
- TWC upstream Android test `TestEthereumTransactionSigner.testEthereumERC20_1559_Signing` provides an authoritative (private-key, proto-input-fields, expected-signed-hex) triple:
  - Private key: `0x608dcb1742bb3fb7aec002074e3420e4fab7d00cced79ccdac53ed5b27138151`
  - ChainId 0x1, nonce 0x0, maxInclusion 0x77359400, maxFee 0xB2D05E00, gasLimit 0x0130B9
  - To: `0x6b175474e89094c44da98b954eedeac495271d0f` (DAI), value 0, data = ERC-20 transfer calldata
  - Expected signed: `0x02f8b00180847735940084b2d05e00830130b9946b175474e89094c44da98b954eedeac495271d0f80b844a9059cbb0000000000000000000000005322b34c88ed0691971bf52a7047448f0f4efc840000000000000000000000000000000000000000000000001bc16d674ec80000c080a0adfcfdf98d4ed35a8967a0c1d78b42adb7c5d831cf5a3272654ec8f8bcd7be2ea011641e065684f6aa476f4fd250aa46cd0b44eccdb0a6e1650d658d1998684cdf`
  - Source: `https://github.com/trustwallet/wallet-core/blob/master/android/app/src/androidTest/java/com/trustwallet/core/app/blockchains/ethereum/TestEthereumTransactionSigner.kt`

Why this vector cannot be asserted in our wallet-evm commonTest as-is:
1. The upstream test provides a raw private key, not a mnemonic. Our `Wallet.signEip1559Transaction` takes a mnemonic; deriving a mnemonic that yields this exact key is not possible.
2. Our commonTest sources compile on JVM — `TrustWalletCoreNativeBridge.jvm.kt` throws `NotImplementedError`, so no real TWC execution happens in `:wallet-evm:allTests` (which runs jvmTest).

Therefore, per CLAUDE.md §4.5 and the spec's explicit fallback clause, **Phase 1/3 tests use the determinism-pair approach**: sign the same payload twice via a fake `WalletEngine` that captures the `signingPayloadJson` and asserts (a) the bytes are byte-identical across invocations, and (b) equal the expected `Eip1559Transaction.toSigningPayload()` output character-for-character. Combined with `Eip1559GoldenVectorTest.goldenJsonDoesNotDrift` (already in the repo) this pins the input side of the signing hash.

**Additional recommendation for a later task (not this one):** add an Android instrumented test (`androidTest` source set on wallet-core) that uses the upstream TWC vector directly by calling `TrustWalletCoreNativeBridge.signEip1559` with a mnemonic whose derived key matches — or by bypassing the HDWallet step via a private raw-key entry point. That is out of scope here; flagged in Risks §5.

## 5. Phases

Each phase ends at a state where `:wallet-core:allTests` and `:wallet-evm:allTests` both pass.

### Phase 1 — Add failing tests (wallet-evm)

1. Create `Eip1559SigningGoldenVectorTest.kt` with:
   - `signEip1559TransactionForwardsExactPayloadToEngine()` — uses a `CapturingEngine : WalletEngine` that records the `signingPayloadJson` argument and returns a fixed `byteArrayOf(0xAA, 0xBB)`. Assert the captured bytes equal `Eip1559Transaction.toSigningPayload()` for a known transaction, and the method returns `byteArrayOf(0xAA, 0xBB)`.
   - `signEip1559TransactionIsDeterministic()` — call twice, assert returned bytes are `contentEquals` and captured JSON bytes across calls are identical.
   - `signEip1559TransactionRejectsChainIdMismatch()` — `tx.chainId = 1`, `chain = SupportedChain.Base (8453)`, expect `IllegalArgumentException` with a message mentioning "Chain mismatch".
2. Run `:wallet-evm:allTests` — expect compile failure (symbol not found) or test failure.

### Phase 2 — Implement wallet-core seam

1. Extend `TrustWalletCoreNativeBridge` expect with `signEip1559`.
2. Extend `WalletEngine` interface; add error-throwing override in `UnsupportedWalletEngine`.
3. Add `Wallet.signEip1559Transaction(chain, signingPayloadJson)` method.
4. Implement actuals:
   - **Android**: full protobuf-based implementation. Kotlinx-serialization DTO for deserialization. Uses `chain.toCoinType()` both for key derivation and for `AnySigner.sign`. Access-list translation: if `accessList.isNotEmpty()`, iterate and call `addAccessList(Ethereum.Access.newBuilder().setAddress(entry.address).addAllStorageKeys(entry.storageKeys.map { ByteString.copyFrom(it.hexToByteArray()) }))`. Verify the exact proto accessor name in Phase 2 (`accessList` vs `access_list`) — the javalite generator uses camelCase setters.
   - **JVM**: `jvmNotSupported("signEip1559")`.
   - **iOS**: delegate to adapter via `requireIosAdapter(method = "signEip1559").signEip1559(...)`; hex-decode.
5. Run `:wallet-core:allTests` — must pass. Existing `WalletTest` is untouched.
6. Run `:wallet-evm:allTests` — must pass (Phase 1 tests now green because `Wallet.signEip1559Transaction(chain, ByteArray)` exists in commonMain and the extension compiles).
7. Run `./gradlew :wallet-core:compileKotlinJvm :wallet-core:compileKotlinIosX64` — must pass (confirms iOS actuals have the new method).

### Phase 3 — Edge-case tests

Add to `Eip1559SigningGoldenVectorTest.kt`:
1. `signEip1559TransactionRejectsNegativeIdentity` — if a test already asserts `SupportedChain.Ethereum.id` vs another chain's id, confirm the message names both.
2. `signEip1559TransactionEmitsPayloadEvenWhenAccessListPopulated` — ensures a transaction with a non-empty access list still flows end-to-end (fake engine captures; we just assert the captured payload contains `"accessList":[`).
3. In `wallet-core/commonTest/WalletTest.kt` — **do not modify** (spec explicitly forbids). Add a new test file instead: `Eip1559WalletEngineTest.kt` with:
   - `walletSignEip1559DelegatesToEngine` — `FakeEngine` with `signEip1559` override that returns known bytes; call via `Wallet.signEip1559Transaction(chain, payloadJson)`.
   - `unsupportedWalletEngineThrowsForSignEip1559` — `UnsupportedWalletEngine().signEip1559(...)` → `IllegalStateException`.
   - `trustWalletCoreSignEip1559FallsBackUntilRuntimeIsAvailable` (jvm only) — `Wallet.fromMnemonicWithTrustWalletCore("test mnemonic").signEip1559Transaction(SupportedChain.Ethereum, byteArrayOf(0))` → `NotImplementedError` with "signEip1559" in message.

### Phase 4 — Cross-platform compile + sample-app check

1. `./gradlew :wallet-core:compileKotlinJvm :wallet-core:compileKotlinIosX64` — green.
2. `./gradlew :wallet-evm:allTests` — green.
3. `./gradlew :wallet-core:allTests` — green.
4. `./gradlew :sample-app:assembleDebug` — green (end-to-end Android JNI link check; verifies the new protobuf call sites compile against the actual TWC AAR).

## 6. Crypto hygiene checklist (CLAUDE.md §4)

Explicitly addressed:

- §4.1 (no log/toString of secrets): `Wallet.mnemonic` is still the only secret on this code path; we do not add `toString()` anywhere, nor `println`, nor include `mnemonic` in exception messages. The Android DTO for deserialization contains only tx fields, no key material. The `IllegalArgumentException` for chain mismatch includes only chain IDs.
- §4.2 (SecureRandom): no randomness generated on this path. HDWallet key derivation is deterministic from mnemonic. No `kotlin.random.Random` used.
- §4.3 (constant-time comparisons): no secret comparisons performed.
- §4.4 (no Random nonces): the EIP-1559 nonce is supplied by the caller (from `eth_getTransactionCount`), not generated here. Verified in `Eip1559Transaction.nonce: Long` — it's a required field.
- §4.5 (golden vectors): see §4 above — determinism-pair fallback is explicitly chosen and documented; the third-party TWC vector's unsuitability is explained.
- §4.6 (never weaken tests): `Eip1559GoldenVectorTest`, `Eip1559PayloadInvarianceTest`, `Eip1559SigningPayloadTest`, `WalletTest` are all untouched.
- §4.7 (address casing): addresses (`tx.to`, access-list addresses) are passed through as-is to TWC. No normalization added (out of scope).
- §4.8 (serialization of signing input is security-relevant): `Eip1559Transaction.toSigningPayload()` and `EvmSigningPayload.kt` are in the do-not-modify list. The Android DTO we add does not go over the wire — it only deserializes locally before re-encoding into protobuf. Changes to its field names would break deserialization and be caught by Phase 1 tests.

## 7. Risks / open questions

1. **Schema mismatch (highest risk).** TWC `AnySigner.signJSON` expects proto3-JSON of `Ethereum.SigningInput`, not our kotlinx JSON. The spec's Design section says "the bridge is responsible for converting that JSON into TWC's expected form". Plan does this via kotlinx deserialization → protobuf `SigningInput`. If the reviewer prefers forwarding JSON literally to `signJSON`, the payload format must be changed — and `Eip1559Transaction.toSigningPayload()` is on the do-not-modify list. Flagging explicitly.
2. **Coin-type vs chain-id duality.** TWC's Ethereum signer derives signing behavior from the proto's `chainId`, not the `CoinType` passed to `AnySigner.sign`. Passing `CoinType.SMARTCHAIN` for BSC works today. If a future TWC upgrade splits this, our routing may break. No action this task; noted.
3. **Access list proto shape uncertainty.** The exact protobuf field name for access list on `Ethereum.Transaction` (or wherever it lives) and whether storage keys take `bytes` vs `string` must be verified against the vendored TWC proto during Phase 2. If absent in the vendored version, we either (a) omit access-list support (add a `require(tx.accessList.isEmpty())` guard) or (b) upgrade TWC. Pause and raise before picking.
4. **Determinism across HDWallet instances.** Phase 3's jvm fall-back test asserts the NotImplementedError path. A true end-to-end determinism check on Android requires an `androidTest` source set on wallet-core, which the repo does not currently use. Scope deferred.
5. **No on-device golden-hex check in this PR.** Per §4, a follow-up instrumented-test task should be filed to consume the TWC upstream vector directly (raw-private-key entrypoint, bypassing HDWallet). Not in scope here.
6. **Spec preference for Option (a) not taken.** Plan uses (b); rationale in §3.1. Please confirm before implementation.
7. **iOS adapter is a breaking-interface change.** Any existing `TrustWalletCoreIosAdapter` implementer (none in-repo) must add `signEip1559`. If downstream hosts exist outside this repo, they will fail to compile. Accepted per spec's "iOS remains not-actually-runnable".

## 8. What this plan does NOT do

- Does not modify `Eip1559Transaction.kt`, `EvmSigningPayload.kt`, `WalletEvmExtensions.kt`, `Chain.kt`, `ChainRegistry.kt`, `gradle/libs.versions.toml`, `settings.gradle.kts`, or any `.claude/*` / `CLAUDE.md` file.
- Does not edit any existing test file. All new tests go in new files.
- Does not refactor or alter the legacy `signTransaction` / `signEvmTransaction` path. The hardcoded `CoinType.ETHEREUM` in the existing `signEvmTransaction` (line 71 of `TrustWalletCoreNativeBridge.android.kt`) is knowingly left as-is — out of scope; it is D's concern.
- Does not add fee estimation, `eth_maxPriorityFeePerGas` helpers, EIP-55 checksum validation, EIP-4844 (blobs), or any sample-wallet integration.
- Does not ship a runnable iOS adapter implementation; only the interface method and Kotlin/Native routing.
- Does not add an `androidTest` source set to any module.
- Does not introduce any new dependency, DI framework, ktlint/detekt config, or build-file restructure.
- Does not run `./gradlew build` at any point.

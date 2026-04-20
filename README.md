# kmp-wallet-toolkit

Kotlin Multiplatform wallet toolkit skeleton focused on EVM first.

## Modules

- `wallet-utils`: shared helpers (hex utilities).
- `wallet-core`: wallet domain model, chain registry, signer adapter boundary.
- `wallet-evm`: EVM transaction model and signing extensions.
- `wallet-rpc`: JSON-RPC client for balance, nonce, gas estimate, raw transaction send.
- `app`: Android host module with a lightweight smoke test against toolkit modules.

## Current status

This is a v0.1 foundation. Transaction signing is wired through `WalletEngine` and still expects a real backend implementation (for example Trust Wallet Core integration).

`TrustWalletCoreWalletEngine` now routes through a platform `TrustWalletCoreNativeBridge` (`expect/actual`) seam in `wallet-core`.

- If Trust runtime classes are available (`wallet.core.jni.HDWallet`, `wallet.core.jni.CoinType`, `wallet.core.jni.AnySigner`), the bridge attempts mnemonic generation, address derivation, and JSON-based signing via reflection.
- If runtime classes/methods are unavailable, operations throw a clear `NotImplementedError`.
- The current JSON signing path expects an EVM-like transaction object (fields like `to`, `gasPriceWei`, `gasLimit`, `valueWei`, `nonce`, `dataHex`).
- iOS source sets are available in `wallet-core` and `wallet-utils`. On iOS, install a `TrustWalletCoreIosAdapter` via `TrustWalletCoreRuntime.installIosAdapter(...)` from your host app before using `TrustWalletCoreWalletEngine`.

Example iOS host setup:

```kotlin
TrustWalletCoreRuntime.installIosAdapter(MyIosTrustWalletCoreAdapter())

val wallet = Wallet.createWithTrustWalletCore()
val address = wallet.address(SupportedChain.Ethereum)
```

To try a Trust KMP beta artifact without hardcoding coordinates in git, set `trustKmpDependency` in `gradle.properties`:

```properties
trustKmpDependency=<group>:<artifact>:<version>
```

## Quick try

Run unit tests for the current modules:

```bash
./gradlew :wallet-core:allTests :wallet-evm:allTests :wallet-rpc:allTests :app:testDebugUnitTest
```

Example API usage:

```kotlin
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.TrustWalletCoreWalletEngine
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.evm.EvmTransaction
import xyz.wallet.toolkit.evm.signEvmTransaction

val wallet = Wallet.fromMnemonic(
    "your mnemonic",
    engine = TrustWalletCoreWalletEngine(),
)

val tx = EvmTransaction(
    chainId = SupportedChain.Ethereum.id,
    to = "0xabc...",
    gasPriceWei = "20000000000",
    gasLimit = "21000",
    nonce = 1,
)

val signedBytes = wallet.signEvmTransaction(SupportedChain.Ethereum, tx)
```


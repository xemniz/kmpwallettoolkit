# kmp-wallet-toolkit

Kotlin Multiplatform wallet toolkit for starting an EVM wallet on Android and iOS.

The project is intentionally split into small library modules plus a Compose Multiplatform sample app. The library gives you wallet creation/import, chain metadata, EVM transaction payloads, signing seams, and JSON-RPC calls. The sample shows how those pieces fit into a starter wallet flow.

## Module Map

| Module | Purpose |
| --- | --- |
| `wallet-utils` | Hex helpers and BIP-39 wordlist support. |
| `wallet-core` | Wallet facade, `WalletKit`, supported chains, Trust Wallet Core engine boundary. |
| `wallet-evm` | Legacy EVM and EIP-1559 transaction models, canonical signing payloads, wallet signing extensions. |
| `wallet-rpc` | Ktor JSON-RPC client for EVM node calls. |
| `sample-compose` | Shared Compose Multiplatform starter wallet UI. |
| `sample-app` | Android host for the sample UI. |
| `iosApp` | iOS host and Trust Wallet Core adapter example. |

## Current Status

This is an EVM-first starter kit. The core modules compile and test on the configured KMP targets, and the sample app demonstrates the main wallet path:

- create wallet
- import wallet
- secure mnemonic persistence in the host app
- home screen with address and portfolio hooks
- send transaction flow
- transaction status flow
- swap quote/assembly work in progress

The signing backend is intentionally behind a `WalletEngine` boundary. Android uses Trust Wallet Core through the Android actual bridge. iOS hosts install a Swift/Objective-C adapter at startup with `TrustWalletCoreRuntime.installIosAdapter(...)`.

## Quick Start

### Local GitHub Packages Credentials

Trust Wallet Core is resolved from GitHub Packages. Keep those credentials outside the repo in your user Gradle properties file:

```properties
# ~/.gradle/gradle.properties
gpr.user=xemniz
gpr.key=<classic-token-with-read:packages>
```

Create or import a wallet with the high-level core facade:

```kotlin
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.WalletKit

val kit = WalletKit.trustWalletCore()
val wallet = kit.createWallet()

val address = kit.address(
    wallet = wallet,
    chain = SupportedChain.Ethereum,
)
```

Import an existing mnemonic:

```kotlin
val wallet = kit.importWallet("abandon abandon abandon ...")
val baseAddress = kit.address(wallet, SupportedChain.Base)
```

Build and sign an EIP-1559 transaction:

```kotlin
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.evm.Eip1559Transaction
import xyz.wallet.toolkit.evm.signEip1559Transaction
import xyz.wallet.toolkit.utils.toHexString

val tx = Eip1559Transaction(
    chainId = SupportedChain.Ethereum.id,
    to = "0x3535353535353535353535353535353535353535",
    valueWei = "1000000000000000",
    maxFeePerGasWei = "50000000000",
    maxPriorityFeePerGasWei = "1000000000",
    gasLimit = "21000",
    nonce = 42,
)

val rawSignedTx = wallet
    .signEip1559Transaction(SupportedChain.Ethereum, tx)
    .toHexString()
```

Send it through JSON-RPC:

```kotlin
import xyz.wallet.toolkit.rpc.RpcClient

val rpc = RpcClient.withDefaults("https://your-rpc-url")
val txHash = rpc.sendRawTransaction(rawSignedTx)
```

Transaction nonces must come from the node:

```kotlin
val nonceHex = rpc.getNonce(address)
```

Do not generate transaction nonces locally.

## iOS Host Setup

Install the iOS adapter before creating a Trust Wallet Core backed wallet:

```kotlin
TrustWalletCoreRuntime.installIosAdapter(MyIosTrustWalletCoreAdapter())

val kit = WalletKit.trustWalletCore()
val wallet = kit.createWallet()
val address = kit.address(wallet, SupportedChain.Ethereum)
```

The adapter must implement mnemonic creation, address derivation, and signing with the native Trust Wallet Core integration supplied by the host app.

## Running Checks

Use scoped Gradle tasks. Do not run the full repo build for normal development.

```bash
./gradlew :wallet-utils:allTests
./gradlew :wallet-core:allTests
./gradlew :wallet-evm:allTests
./gradlew :wallet-rpc:allTests
./gradlew :sample-compose:allTests :sample-app:assembleDebug
```

Use [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) before tagging a milestone.

## Running The Sample

Android:

```bash
./gradlew :sample-app:installDebug
```

iOS:

Open `iosApp/iosApp.xcodeproj` in Xcode, select a simulator, and run the app. The Xcode project builds the shared `SampleCompose` framework through Gradle.

## Security Notes

- Never log mnemonics, private keys, seed bytes, signing payloads containing key material, or raw private keys.
- Transaction nonces come from `eth_getTransactionCount`.
- EIP-1559 signing payload serialization is covered by drift/invariance tests because it is part of the signed input.
- The toolkit delegates signing primitives to Trust Wallet Core; adding another crypto primitive library is a design decision, not a small implementation detail.

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
| `iosApp` | iOS host for the sample UI. |

## Current Status

This is an EVM-first starter kit. The core modules compile and test on the configured KMP targets, and the sample app demonstrates the main wallet path:

- create wallet
- import wallet
- secure mnemonic persistence in the host app
- home screen with address and portfolio hooks
- send transaction flow
- transaction status flow
- swap quote/assembly work in progress

The signing backend is intentionally behind a `WalletEngine` boundary. Android uses Trust Wallet Core through the Android actual bridge. iOS uses Trust Wallet Core through Kotlin/Native cinterop against republished XCFramework artifacts.

## Installation

Released artifacts are available from Maven Central. Consumers do not need Trust Wallet GitHub Packages, SwiftPM, or any Trust Wallet credentials.

```kotlin
plugins {
    // Required only for KMP modules that declare iOS targets.
    id("io.github.xemniz.wallet-toolkit.ios") version "0.1.1"
}

repositories {
    google()
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.xemniz:wallet-core:0.1.1")
            implementation("io.github.xemniz:wallet-evm:0.1.1")
            implementation("io.github.xemniz:wallet-rpc:0.1.1")
        }
    }
}
```

Pin an explicit released version. Do not use `0.1.0`; its Android metadata was published before the Trust Wallet Core republish fix and can leak the upstream `com.trustwallet` dependency.

Use only the modules you need:

- `wallet-core` for wallet creation/import and address derivation.
- `wallet-evm` for EVM transaction models and signing helpers.
- `wallet-rpc` for EVM JSON-RPC calls. `RpcClient.withDefaults(...)` includes OkHttp for JVM/Android and Darwin for iOS; if you construct `RpcClient` with your own `HttpClient`, provide your own Ktor engine.
- `wallet-utils` is pulled in transitively by `wallet-core` and `wallet-evm`.

The Gradle plugin is needed only for iOS targets. It resolves the republished Trust Wallet Core XCFrameworks from Maven Central and wires the native linker. Android consumers only need the library dependencies.

For local development of this repository, the sample uses project dependencies by default. To validate the same sample as a downstream Maven client:

```bash
./gradlew -PwalletToolkitDependencyMode=maven \
  :sample-compose:compileAndroidMain \
  :sample-compose:linkDebugFrameworkIosSimulatorArm64 \
  :sample-app:assembleDebug
```

## Quick Start

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

When the host app needs to persist a newly-created mnemonic, export it explicitly and write it only to platform secure storage:

```kotlin
secureStorage.save(wallet.exportMnemonic())
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

For KMP apps built with Gradle, apply `io.github.xemniz.wallet-toolkit.ios` to the KMP module that declares iOS targets. The plugin links the republished Trust Wallet Core XCFrameworks from Maven Central. No SwiftPM package, host adapter, GitHub Packages repository, or Trust Wallet credentials are required for the default `WalletKit.trustWalletCore()` path.

`WalletKit.trustWalletCore()` is intended for Android and iOS. JVM tests should use `WalletKit.withEngine(fakeWalletEngine)`.

Create wallets the same way as Android:

```kotlin
val kit = WalletKit.trustWalletCore()
val wallet = kit.createWallet()
val address = kit.address(wallet, SupportedChain.Ethereum)
```

Custom backends implement `WalletEngine` and are supplied through `WalletKit.withEngine(...)`.

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

Android using the published Maven Central toolkit artifacts:

```bash
./gradlew -PwalletToolkitDependencyMode=maven :sample-app:installDebug
```

iOS:

Open `iosApp/iosApp.xcodeproj` in Xcode, select a simulator, and run the app. The Xcode project builds the shared `SampleCompose` framework through Gradle.

To verify that the sample links as a downstream Maven client before opening Xcode:

```bash
./gradlew -PwalletToolkitDependencyMode=maven \
  :sample-compose:compileAndroidMain \
  :sample-compose:linkDebugFrameworkIosSimulatorArm64 \
  :sample-app:assembleDebug
```

## Security Notes

- Never log mnemonics, private keys, seed bytes, signing payloads containing key material, or raw private keys.
- `Wallet.toString()` is redacted; use `Wallet.exportMnemonic()` only for explicit secure-storage or backup flows.
- Transaction nonces come from `eth_getTransactionCount`.
- EIP-1559 signing payload serialization is covered by drift/invariance tests because it is part of the signed input.
- The toolkit delegates signing primitives to Trust Wallet Core; adding another crypto primitive library is a design decision, not a small implementation detail.

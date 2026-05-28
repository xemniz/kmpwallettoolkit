# Starter Guide

This guide is the intended first path for an app team using `kmp-wallet-toolkit` to start an EVM wallet with Kotlin Multiplatform.

## 1. Pick The Layer

Add the modules your app needs:

```kotlin
plugins {
    // Required only for KMP modules that declare iOS targets.
    id("io.github.xemniz.wallet-toolkit.ios") version "0.1.0-alpha01"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.xemniz:wallet-core:0.1.0-alpha01")
            implementation("io.github.xemniz:wallet-evm:0.1.0-alpha01")
            implementation("io.github.xemniz:wallet-rpc:0.1.0-alpha01")
        }
    }
}
```

The plugin is required for KMP modules with iOS targets because Trust Wallet Core is distributed as XCFrameworks. It resolves those XCFrameworks from Maven Central and configures the iOS linker. Android-only consumers can omit it. No Trust Wallet GitHub Packages credentials are required.

Use `wallet-core` when you need wallet lifecycle and addresses:

- `WalletKit`
- `Wallet`
- `SupportedChain`
- `WalletEngine`

Use `wallet-evm` when you need transaction data and signing helpers:

- `EvmTransaction`
- `Eip1559Transaction`
- `Wallet.signEvmTransaction(...)`
- `Wallet.signEip1559Transaction(...)`

Use `wallet-rpc` when you need node calls:

- `RpcClient.getBalance(...)`
- `RpcClient.getNonce(...)`
- `RpcClient.estimateGas(...)`
- `RpcClient.sendRawTransaction(...)`
- `RpcClient.getTransactionReceipt(...)`

## 2. Create The Wallet Kit

```kotlin
val kit = WalletKit.trustWalletCore()
```

For tests, provide a fake engine:

```kotlin
val kit = WalletKit.withEngine(fakeWalletEngine)
```

## 3. Create Or Import A Wallet

```kotlin
val newWallet = kit.createWallet()
val importedWallet = kit.importWallet(existingMnemonic)
```

Host apps are responsible for secure storage. The sample uses platform storage wrappers in the Android and iOS hosts. When you need to persist a newly created phrase, export it explicitly:

```kotlin
secureStorage.save(newWallet.exportMnemonic())
```

## 4. Derive Addresses

```kotlin
val address = kit.address(importedWallet, SupportedChain.Base)
```

Supported chains are currently Ethereum, Base, Polygon, Arbitrum, Optimism, and BNB Smart Chain.

## 5. Fetch Chain State

```kotlin
val rpc = RpcClient.withDefaults("https://your-rpc-url")
val balanceHex = rpc.getBalance(address)
val nonceHex = rpc.getNonce(address)
```

Convert the nonce from node-provided hex. Do not invent transaction nonces in application code.

## 6. Build And Sign An EIP-1559 Transaction

```kotlin
val tx = Eip1559Transaction(
    chainId = SupportedChain.Base.id,
    to = recipient,
    valueWei = amountWei,
    maxFeePerGasWei = maxFeePerGasWei,
    maxPriorityFeePerGasWei = maxPriorityFeePerGasWei,
    gasLimit = gasLimit,
    nonce = nonce,
)

val rawTxHex = importedWallet
    .signEip1559Transaction(SupportedChain.Base, tx)
    .toHexString()
```

## 7. Broadcast And Track

```kotlin
val txHash = rpc.sendRawTransaction(rawTxHex)
val receipt = rpc.getTransactionReceipt(txHash)
```

A `null` receipt means the transaction is still pending.

## Production Checklist

- Use `WalletKit.trustWalletCore()` on Android and iOS; the published artifacts provide the Trust Wallet Core backend.
- Store mnemonics only in platform secure storage.
- Keep RPC/API credentials out of source control.
- Use golden vectors for every signing path you add.
- Keep transaction serialization changes covered by drift tests.
- Run scoped module tests before release.

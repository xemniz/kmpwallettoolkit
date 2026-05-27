# Starter Guide

This guide is the intended first path for an app team using `kmp-wallet-toolkit` to start an EVM wallet with Kotlin Multiplatform.

## 1. Pick The Layer

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

Host apps are responsible for secure storage. The sample uses platform storage wrappers in the Android and iOS hosts.

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

- Install a real Trust Wallet Core backend for every target you ship.
- Store mnemonics only in platform secure storage.
- Keep RPC/API credentials out of source control.
- Use golden vectors for every signing path you add.
- Keep transaction serialization changes covered by drift tests.
- Run scoped module tests before release.

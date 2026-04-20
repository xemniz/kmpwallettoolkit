# TRD: KMP Wallet Toolkit

## 1. Overview

**Project name:** `kmp-wallet-toolkit`\
**Type:** Open-source developer SDK\
**Goal:** Provide a clean Kotlin Multiplatform API for building crypto
wallets on top of Trust Wallet Core.

The toolkit simplifies interaction with blockchain wallets by
abstracting:

-   key generation\
-   address derivation\
-   transaction signing\
-   chain configuration

Target platforms:

-   Android\
-   iOS\
-   JVM\
-   Desktop (optional)

------------------------------------------------------------------------

# 2. Objectives

## Primary objectives

1.  Provide a **clean Kotlin API** over Trust Wallet Core.
2.  Support **EVM chains first**.
3.  Enable **wallet creation and transaction signing in \<10 lines of
    code**.
4.  Be **fully multiplatform (KMP)**.

## Non-goals (v1)

-   running blockchain nodes\
-   custom cryptography implementations\
-   advanced DeFi integrations\
-   UI framework

------------------------------------------------------------------------

# 3. System Architecture

## High-level architecture

Application\
│\
kmp-wallet-toolkit\
│\
wallet-core-kotlin (Trust Wallet)\
│\
Trust Wallet Core C++ engine\
│\
Blockchain networks

------------------------------------------------------------------------

# 4. Module Structure

kmp-wallet-toolkit\
│\
├── wallet-core --- common wallet abstractions\
├── wallet-evm --- EVM-specific logic\
├── wallet-rpc --- RPC client\
├── wallet-utils --- encoding / helpers\
└── examples\
  ├── android-demo\
  └── ios-demo

------------------------------------------------------------------------

# 5. Core Components

## 5.1 Wallet

Responsible for managing keys and addresses.

Responsibilities:

-   generate mnemonic\
-   derive private keys\
-   derive public keys\
-   derive addresses

Example API:

``` kotlin
val wallet = Wallet.create()

val mnemonic = wallet.mnemonic

val address = wallet.address(Chain.Ethereum)
```

------------------------------------------------------------------------

## 5.2 Chain Abstraction

Defines behavior for blockchain-specific operations.

``` kotlin
interface Chain {

    val name: String

    fun deriveAddress(privateKey: ByteArray): String

    fun signTransaction(
        privateKey: ByteArray,
        transaction: Transaction
    ): ByteArray
}
```

Initial implementation:

-   `EvmChain`

Future:

-   `SolanaChain`
-   `BitcoinChain`

------------------------------------------------------------------------

## 5.3 Transaction Models

Common transaction interface:

``` kotlin
interface Transaction
```

EVM implementation:

``` kotlin
data class EvmTransaction(
    val to: String,
    val value: BigInteger,
    val gasPrice: BigInteger,
    val gasLimit: BigInteger,
    val nonce: Long,
    val data: ByteArray?
)
```

------------------------------------------------------------------------

## 5.4 Signing Engine

Responsible for signing messages and transactions.

Implementation uses **Trust Wallet Core**.

Signing flow:

Transaction → Encoding → Wallet Core signing → Serialized raw
transaction

Example:

``` kotlin
val signedTx = wallet.signTransaction(
    chain = Chain.Ethereum,
    transaction = tx
)
```

------------------------------------------------------------------------

## 5.5 RPC Client

Simple JSON-RPC wrapper.

Responsibilities:

-   get balance\
-   send raw transaction\
-   estimate gas\
-   get nonce

Implementation stack:

-   Ktor
-   Kotlinx Serialization

Example:

``` kotlin
val rpc = RpcClient("https://mainnet.infura.io")

val balance = rpc.getBalance(address)
```

------------------------------------------------------------------------

# 6. External Dependencies

  Dependency              Purpose
  ----------------------- --------------------------
  wallet-core-kotlin      cryptography and signing
  ktor                    RPC networking
  kotlinx.serialization   JSON parsing

------------------------------------------------------------------------

# 7. Supported Chains (v1)

  Chain      Type
  ---------- ------
  Ethereum   EVM
  Base       EVM
  Polygon    EVM
  Arbitrum   EVM

------------------------------------------------------------------------

# 8. Data Flow

## Wallet Creation

User → Wallet.create() → Trust Wallet Core → Mnemonic generated

## Transaction Signing

Transaction model → Encoding → Wallet Core signing → Signed raw
transaction

## Transaction Broadcast

Signed transaction → RPC client → Blockchain node

------------------------------------------------------------------------

# 9. Public SDK API

Example:

``` kotlin
val wallet = Wallet.create()

val address = wallet.address(Chain.Ethereum)

val tx = EvmTransaction(
    to = "0x...",
    value = 0.01.eth
)

val signed = wallet.sign(tx)

rpc.sendRawTransaction(signed)
```

------------------------------------------------------------------------

# 10. Security Requirements

1.  Do not implement custom cryptography.
2.  All key operations delegated to Trust Wallet Core.
3.  Private keys never leave application memory.
4.  SDK must not store mnemonics.

------------------------------------------------------------------------

# 11. Performance Requirements

Target constraints:

-   signing latency \< 50 ms\
-   minimal memory overhead

Expected binary size impact from Wallet Core:

\~10--20 MB

------------------------------------------------------------------------

# 12. Testing Strategy

## Unit tests

-   mnemonic generation\
-   address derivation\
-   transaction encoding\
-   signing validation

## Integration tests

-   RPC calls\
-   transaction broadcasting

Test networks:

-   Sepolia\
-   Base testnet\
-   Polygon Mumbai

------------------------------------------------------------------------

# 13. Example Applications

Included demos:

### Android demo

-   create wallet\
-   display address\
-   send transaction

### iOS demo

Same functionality.

------------------------------------------------------------------------

# 14. Roadmap

### v0.1

-   wallet creation\
-   EVM signing\
-   RPC client

### v0.2

-   ERC20 helpers\
-   gas estimation\
-   transaction decoding

### v0.3

-   WalletConnect support\
-   Compose UI toolkit

------------------------------------------------------------------------

# 15. Distribution

Publishing targets:

-   Maven Central\
-   GitHub Packages

Artifact example:

    io.kmpwallet:wallet-core

------------------------------------------------------------------------

# 16. Documentation

Documentation includes:

-   quick start guide\
-   wallet creation tutorial\
-   sending transactions\
-   RPC usage

Documentation style: **example-first**.

------------------------------------------------------------------------

# 17. Risks

  Risk                      Mitigation
  ------------------------- -------------------------
  Wallet Core API changes   wrapper abstraction
  Binary size               modular packaging
  Chain-specific quirks     chain abstraction layer

------------------------------------------------------------------------

# 18. Success Criteria

Project considered successful if:

-   developers can build a wallet in **\<30 minutes**
-   project reaches **100+ GitHub stars in the first month**
-   used in **hackathons or demo apps**

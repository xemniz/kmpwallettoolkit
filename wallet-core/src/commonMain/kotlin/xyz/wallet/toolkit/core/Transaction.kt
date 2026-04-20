package xyz.wallet.toolkit.core

interface Transaction

/**
 * Contract for EVM transaction data that the native signing bridge can read
 * without reflection. Implemented by the wallet-evm module's EvmTransaction.
 */
interface EvmTransactionData : Transaction {
    val chainId: Long
    val to: String
    val valueWei: String
    val gasPriceWei: String
    val gasLimit: String
    val nonce: Long
    val dataHex: String?
}


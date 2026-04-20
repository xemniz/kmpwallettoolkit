package xyz.wallet.toolkit.core

/**
 * Adapter boundary for native wallet backends such as Trust Wallet Core.
 */
interface WalletEngine {
    fun createMnemonic(): String
    fun deriveAddress(mnemonic: String, chain: SupportedChain): String
    fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray
}

class UnsupportedWalletEngine : WalletEngine {
    override fun createMnemonic(): String {
        error("No wallet engine configured. Provide a Trust Wallet Core backed WalletEngine.")
    }

    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        error("No wallet engine configured. Provide a Trust Wallet Core backed WalletEngine.")
    }

    override fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray {
        error("No wallet engine configured. Provide a Trust Wallet Core backed WalletEngine.")
    }
}


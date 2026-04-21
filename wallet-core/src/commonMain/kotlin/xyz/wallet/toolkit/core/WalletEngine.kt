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

    /**
     * Sign an EIP-1559 (type-2) transaction using the canonical JSON payload emitted
     * by `wallet-evm`'s `Eip1559Transaction.toSigningPayload()`. Returns the raw
     * EIP-2718 envelope + RLP bytes suitable for `eth_sendRawTransaction`.
     *
     * Default implementation throws; existing engines that predate this method
     * continue to compile. Engines wired to Trust Wallet Core override it.
     */
    fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray {
        throw NotImplementedError(
            "WalletEngine ${this::class.simpleName} does not implement signEip1559. " +
                "Wire a Trust Wallet Core backed engine to sign EIP-1559 transactions.",
        )
    }
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

    override fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray {
        error("No wallet engine configured. Provide a Trust Wallet Core backed WalletEngine.")
    }
}


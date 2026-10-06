package xyz.wallet.toolkit.core

/**
 * Trust Wallet Core calls implemented by Android JNI and iOS cinterop.
 */
expect object TrustWalletCoreNativeBridge {
    /** Keccak-256 of the complete signed EVM envelope, as used by eth_sendRawTransaction. */
    fun evmTransactionHash(rawSignedTransaction: ByteArray): ByteArray

    fun createMnemonic(): String

    fun deriveAddress(mnemonic: String, chain: SupportedChain): String

    fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray

    /**
     * Sign an EIP-1559 (type-2) transaction whose serialized input is the canonical
     * JSON emitted by `Eip1559Transaction.toSigningPayload()` in wallet-evm. Returns
     * the raw EIP-2718 envelope + RLP bytes suitable for `eth_sendRawTransaction`.
     */
    fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray
}

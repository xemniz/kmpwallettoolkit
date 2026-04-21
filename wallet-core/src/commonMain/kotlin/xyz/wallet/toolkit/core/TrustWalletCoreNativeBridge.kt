package xyz.wallet.toolkit.core

/**
 * Native seam for Trust Wallet Core calls. Platform actuals can be wired to JNI/cinterop.
 */
expect object TrustWalletCoreNativeBridge {
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

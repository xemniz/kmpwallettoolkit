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
}

package xyz.wallet.toolkit.core

/**
 * Multiplatform entry point for a Trust Wallet Core backed engine.
 *
 * Platform actuals are stubs for now and should be replaced with real bindings.
 */
expect class TrustWalletCoreWalletEngine() : WalletEngine {
    override fun createMnemonic(): String

    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String

    override fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray

    override fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray
}

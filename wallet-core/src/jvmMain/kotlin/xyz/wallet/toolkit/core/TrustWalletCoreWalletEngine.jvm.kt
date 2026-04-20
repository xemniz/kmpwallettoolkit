package xyz.wallet.toolkit.core

actual class TrustWalletCoreWalletEngine actual constructor() : WalletEngine {
    override fun createMnemonic(): String = TrustWalletCoreNativeBridge.createMnemonic()

    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        return TrustWalletCoreNativeBridge.deriveAddress(mnemonic = mnemonic, chain = chain)
    }

    override fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray {
        return TrustWalletCoreNativeBridge.signTransaction(
            mnemonic = mnemonic,
            chain = chain,
            transaction = transaction,
        )
    }
}


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

    override fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray {
        return TrustWalletCoreNativeBridge.signEip1559(
            mnemonic = mnemonic,
            chain = chain,
            signingPayloadJson = signingPayloadJson,
        )
    }
}


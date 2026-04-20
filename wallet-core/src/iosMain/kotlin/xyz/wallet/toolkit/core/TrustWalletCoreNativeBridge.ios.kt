package xyz.wallet.toolkit.core

import xyz.wallet.toolkit.utils.hexToByteArray

actual object TrustWalletCoreNativeBridge {
    actual fun createMnemonic(): String {
        return TrustWalletCoreRuntime.requireIosAdapter().createMnemonic()
    }

    actual fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        return TrustWalletCoreRuntime.requireIosAdapter().deriveAddress(
            mnemonic = mnemonic,
            chain = chain,
        )
    }

    actual fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray {
        require(transaction is EvmTransactionData) {
            "Only EvmTransactionData transactions are currently supported for signing. " +
                "Received: ${transaction::class.simpleName}"
        }

        val hex = TrustWalletCoreRuntime.requireIosAdapter().signEvmTransaction(
            mnemonic = mnemonic,
            chain = chain,
            transaction = TrustWalletCoreEvmSigningRequest.from(transaction),
        )

        return hex.hexToByteArray()
    }
}

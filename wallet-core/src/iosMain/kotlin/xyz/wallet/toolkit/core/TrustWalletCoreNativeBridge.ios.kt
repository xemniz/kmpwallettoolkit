package xyz.wallet.toolkit.core

import xyz.wallet.toolkit.utils.hexToByteArray

actual object TrustWalletCoreNativeBridge {
    actual fun createMnemonic(): String {
        return TrustWalletCoreRuntime.requireIosAdapter(method = "createMnemonic").createMnemonic()
    }

    actual fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        return TrustWalletCoreRuntime.requireIosAdapter(method = "deriveAddress").deriveAddress(
            mnemonic = mnemonic,
            chain = chain,
        )
    }

    actual fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray {
        val adapter = TrustWalletCoreRuntime.requireIosAdapter(method = "signTransaction")

        require(transaction is EvmTransactionData) {
            "Only EvmTransactionData transactions are currently supported for signing. " +
                "Received: ${transaction::class.simpleName}"
        }

        val hex = adapter.signEvmTransaction(
            mnemonic = mnemonic,
            chain = chain,
            transaction = TrustWalletCoreEvmSigningRequest.from(transaction),
        )

        return hex.hexToByteArray()
    }

    actual fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray {
        val adapter = TrustWalletCoreRuntime.requireIosAdapter(method = "signEip1559")
        val hex = adapter.signEip1559(
            mnemonic = mnemonic,
            chain = chain,
            signingPayloadJson = signingPayloadJson,
        )
        return hex.hexToByteArray()
    }
}

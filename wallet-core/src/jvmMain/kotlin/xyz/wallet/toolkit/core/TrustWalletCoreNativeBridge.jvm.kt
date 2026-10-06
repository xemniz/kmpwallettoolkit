package xyz.wallet.toolkit.core

/**
 * JVM stub; native implementations are available on Android and iOS.
 * Use a mock [WalletEngine] for JVM/desktop unit tests.
 */
actual object TrustWalletCoreNativeBridge {
    actual fun evmTransactionHash(rawSignedTransaction: ByteArray): ByteArray =
        jvmNotSupported("evmTransactionHash")

    actual fun createMnemonic(): String =
        jvmNotSupported("createMnemonic")

    actual fun deriveAddress(mnemonic: String, chain: SupportedChain): String =
        jvmNotSupported("deriveAddress")

    actual fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray =
        jvmNotSupported("signTransaction")

    actual fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray =
        jvmNotSupported("signEip1559")

    private fun jvmNotSupported(method: String): Nothing {
        throw NotImplementedError(
            "Trust Wallet Core native bridge is not available on JVM. " +
                "Use a mock WalletEngine for JVM tests. (method=$method)",
        )
    }
}

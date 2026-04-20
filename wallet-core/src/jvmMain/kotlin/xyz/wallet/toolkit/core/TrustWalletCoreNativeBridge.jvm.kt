package xyz.wallet.toolkit.core

/**
 * JVM stub – Trust Wallet Core native bindings are Android-only.
 * Use a mock [WalletEngine] for JVM/desktop unit tests.
 */
actual object TrustWalletCoreNativeBridge {
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

    private fun jvmNotSupported(method: String): Nothing {
        throw NotImplementedError(
            "Trust Wallet Core native bridge is not available on JVM. " +
                "Use a mock WalletEngine for JVM tests. (method=$method)",
        )
    }
}

package xyz.wallet.toolkit.core

/**
 * Host-provided bridge for iOS consumers of [TrustWalletCoreWalletEngine].
 *
 * The generated Kotlin/Native framework exports this interface so a Swift/Objective-C
 * host can provide the concrete Trust Wallet Core integration at app startup.
 */
interface TrustWalletCoreIosAdapter {
    fun createMnemonic(): String

    fun deriveAddress(mnemonic: String, chain: SupportedChain): String

    /**
     * Sign an EVM transaction and return the raw signed bytes as a hex string.
     * Both prefixed (`0x...`) and non-prefixed hex are accepted.
     */
    fun signEvmTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: TrustWalletCoreEvmSigningRequest,
    ): String
}

data class TrustWalletCoreEvmSigningRequest(
    val chainId: Long,
    val to: String,
    val valueWei: String,
    val gasPriceWei: String,
    val gasLimit: String,
    val nonce: Long,
    val dataHex: String? = null,
) {
    companion object {
        internal fun from(transaction: EvmTransactionData): TrustWalletCoreEvmSigningRequest {
            return TrustWalletCoreEvmSigningRequest(
                chainId = transaction.chainId,
                to = transaction.to,
                valueWei = transaction.valueWei,
                gasPriceWei = transaction.gasPriceWei,
                gasLimit = transaction.gasLimit,
                nonce = transaction.nonce,
                dataHex = transaction.dataHex,
            )
        }
    }
}

/**
 * Runtime registry used by the iOS actual bridge.
 *
 * Call [installIosAdapter] once from the iOS host before creating a wallet with
 * [TrustWalletCoreWalletEngine]. Android and JVM implementations ignore this registry.
 */
object TrustWalletCoreRuntime {
    private var iosAdapter: TrustWalletCoreIosAdapter? = null

    fun installIosAdapter(adapter: TrustWalletCoreIosAdapter) {
        iosAdapter = adapter
    }

    fun clearIosAdapter() {
        iosAdapter = null
    }

    internal fun requireIosAdapter(): TrustWalletCoreIosAdapter {
        return iosAdapter ?: throw NotImplementedError(
            "Trust Wallet Core iOS bridge is not configured. " +
                "Install an adapter via TrustWalletCoreRuntime.installIosAdapter(...) " +
                "from your iOS host before using TrustWalletCoreWalletEngine.",
        )
    }
}

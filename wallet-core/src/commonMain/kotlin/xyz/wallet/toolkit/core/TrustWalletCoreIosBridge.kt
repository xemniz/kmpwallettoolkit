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

    /**
     * Sign an EIP-1559 (type-2) transaction and return the raw signed bytes as a
     * hex string. The payload is the canonical JSON emitted by wallet-evm's
     * `Eip1559Transaction.toSigningPayload()`; the host is responsible for
     * translating it into Trust Wallet Core's `Ethereum.SigningInput`.
     * Both prefixed (`0x...`) and non-prefixed hex are accepted.
     *
     * Default implementation throws; iOS hosts that want EIP-1559 support override it.
     */
    fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): String {
        throw NotImplementedError(
            "TrustWalletCoreIosAdapter does not implement signEip1559. " +
                "Override this method in the iOS host adapter to enable EIP-1559 signing.",
        )
    }
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

    internal fun requireIosAdapter(method: String? = null): TrustWalletCoreIosAdapter {
        return iosAdapter ?: throw NotImplementedError(
            buildString {
                append("Trust Wallet Core native bridge is not available on iOS: ")
                append("install an adapter via TrustWalletCoreRuntime.installIosAdapter(...) ")
                append("from your iOS host before using TrustWalletCoreWalletEngine.")
                if (method != null) append(" (method=$method)")
            },
        )
    }
}

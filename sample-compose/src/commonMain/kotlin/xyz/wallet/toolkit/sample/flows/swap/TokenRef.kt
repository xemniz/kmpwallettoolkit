package xyz.wallet.toolkit.sample.flows.swap

import xyz.wallet.toolkit.core.SupportedChain

/**
 * Cross-provider token identity. [address] = null means the chain-native
 * coin (ETH on Ethereum + Base). 0x's Swap API v2 expects the native-token
 * sentinel address — [addressForZeroEx] does that projection so callers don't
 * need to know.
 */
data class TokenRef(
    val symbol: String,
    val name: String,
    val address: String?,
    val decimals: Int,
    val chain: SupportedChain,
    val iconUrl: String?,
) {
    val isNative: Boolean get() = address == null

    fun addressForZeroEx(): String = address ?: ZERO_EX_NATIVE_TOKEN

    private companion object {
        const val ZERO_EX_NATIVE_TOKEN = "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"
    }
}

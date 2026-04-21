package xyz.wallet.toolkit.sample.rpc

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.rpc.RpcClient

/**
 * Hands out `wallet-rpc` clients pinned to public RPC endpoints for the
 * showcase. S5/S6 consume this; S1 only needs it to compile.
 *
 * No caching: S5 can add a per-chain cache if latency matters. S1 favors
 * simplicity over cleverness.
 */
object RpcClientFactory {
    private const val ETH_URL = "https://ethereum-rpc.publicnode.com"
    private const val BASE_URL = "https://base-rpc.publicnode.com"

    fun forChain(chain: SupportedChain): RpcClient {
        val url = when (chain) {
            SupportedChain.Ethereum -> ETH_URL
            SupportedChain.Base -> BASE_URL
            else -> throw IllegalArgumentException("Unsupported chain: ${chain.id}")
        }
        return RpcClient.withDefaults(url)
    }
}

package xyz.wallet.toolkit.sample.rpc

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.rpc.RpcClient

object RpcClientFactory {
    private const val ETH_URL = "https://ethereum-rpc.publicnode.com"
    private const val BASE_URL = "https://base-rpc.publicnode.com"
    private val clients = mutableMapOf<Long, RpcClient>()

    fun forChain(chain: SupportedChain): RpcClient {
        val url = when (chain) {
            SupportedChain.Ethereum -> ETH_URL
            SupportedChain.Base -> BASE_URL
            else -> throw IllegalArgumentException("Unsupported chain: ${chain.id}")
        }
        return clients[chain.id]?.takeUnless { it.isClosed }
            ?: RpcClient.withDefaults(url).also { clients[chain.id] = it }
    }

    fun closeAll() {
        clients.values.forEach(RpcClient::close)
        clients.clear()
    }
}

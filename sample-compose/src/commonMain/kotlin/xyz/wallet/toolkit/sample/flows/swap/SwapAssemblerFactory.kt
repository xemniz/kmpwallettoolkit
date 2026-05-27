package xyz.wallet.toolkit.sample.flows.swap

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.sample.rpc.RpcClientFactory

interface SwapAssemblerFactory {
    fun create(wallet: Wallet, chain: SupportedChain): SwapAssembler
}

class DefaultSwapAssemblerFactory : SwapAssemblerFactory {
    override fun create(wallet: Wallet, chain: SupportedChain): SwapAssembler =
        SwapAssembler(
            wallet = wallet,
            rpc = RpcClientFactory.forChain(chain),
            chain = chain,
        )
}


package xyz.wallet.toolkit.core

object ChainRegistry {
    private val byId = SupportedChain.entries.associateBy { it.id }

    fun byId(chainId: Long): SupportedChain? = byId[chainId]

    fun all(): List<SupportedChain> = SupportedChain.entries
}


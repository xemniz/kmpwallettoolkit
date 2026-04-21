package xyz.wallet.toolkit.core

/**
 * Identifies a chain supported by the toolkit.
 */
interface Chain {
    val id: Long
    val displayName: String
    val ticker: String
}

enum class SupportedChain(
    override val id: Long,
    override val displayName: String,
    override val ticker: String,
) : Chain {
    Ethereum(id = 1, displayName = "Ethereum", ticker = "ETH"),
    Base(id = 8453, displayName = "Base", ticker = "ETH"),
    Polygon(id = 137, displayName = "Polygon", ticker = "MATIC"),
    Arbitrum(id = 42161, displayName = "Arbitrum", ticker = "ETH"),
    Optimism(id = 10, displayName = "Optimism", ticker = "ETH"),
}


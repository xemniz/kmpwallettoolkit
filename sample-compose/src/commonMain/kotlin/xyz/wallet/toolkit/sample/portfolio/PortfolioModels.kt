package xyz.wallet.toolkit.sample.portfolio

/**
 * Display-ready portfolio snapshot for a single chain, derived from
 * Zerion's positions endpoint.
 *
 * `totalUsd` and `change24h` are summed from per-token fields; tokens
 * without a USD price (Zerion returns `null` for illiquid long-tail
 * assets) are still rendered but do not contribute to the total.
 */
data class PortfolioSnapshot(
    val tokens: List<TokenPosition>,
    val totalUsd: Double,
    val change24h: PortfolioChange24h?,
) {
    val native: TokenPosition? get() = tokens.firstOrNull { it.isNative }

    override fun toString(): String =
        "PortfolioSnapshot(tokens=${tokens.size}, totalUsd=redacted)"
}

data class TokenPosition(
    val symbol: String,
    val name: String,
    val quantityDecimal: String,
    val valueUsd: Double?,
    val iconUrl: String?,
    val isNative: Boolean,
) {
    override fun toString(): String = "TokenPosition($symbol, redacted)"
}

data class PortfolioChange24h(val absoluteUsd: Double, val percent: Double)

sealed interface PortfolioState {
    object Loading : PortfolioState
    data class Value(val snapshot: PortfolioSnapshot) : PortfolioState {
        override fun toString(): String = "Value(redacted)"
    }
    data class Error(val message: String) : PortfolioState
}

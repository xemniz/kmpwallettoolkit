package xyz.wallet.toolkit.sample.nav

/** Destinations for the sample-compose showcase. S2–S6 consume these. */
sealed class Route {
    object Welcome : Route()
    object Create : Route()
    object Import : Route()
    object Home : Route()
    data class Send(val chainId: Long) : Route()
    data class TxStatus(val txHash: String, val chainId: Long) : Route()
}

package xyz.wallet.toolkit.sample.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations for the sample-compose showcase. S2–S6 consume these. */
@Serializable
sealed class Route : NavKey {
    @Serializable
    data object Welcome : Route()

    @Serializable
    data object Create : Route()

    @Serializable
    data object Import : Route()

    @Serializable
    data object Home : Route()

    @Serializable
    data class Send(val chainId: Long) : Route()

    @Serializable
    data class Swap(val chainId: Long) : Route()

    @Serializable
    data class TxStatus(val txHash: String, val chainId: Long) : Route()
}

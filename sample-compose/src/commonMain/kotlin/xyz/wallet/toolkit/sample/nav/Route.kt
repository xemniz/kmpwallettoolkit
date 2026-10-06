package xyz.wallet.toolkit.sample.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Saved destinations in the sample app. */
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
    data class Swap(val chainId: Long, val operationId: Long? = null) : Route()

    @Serializable
    data class Operation(val id: Long) : Route()

    @Serializable
    data class TxStatus(val txHash: String, val chainId: Long) : Route()
}

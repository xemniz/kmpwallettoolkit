package xyz.wallet.toolkit.sample.flows.tx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import xyz.wallet.toolkit.rpc.RpcClient

private const val INITIAL_BACKOFF_MS: Long = 3_000L
private const val MAX_BACKOFF_MS: Long = 15_000L
private const val SOFT_TIMEOUT_SECONDS: Int = 300
private const val BACKOFF_FACTOR_NUM: Long = 3L
private const val BACKOFF_FACTOR_DEN: Long = 2L // multiply by 1.5 without floats

/**
 * Polls `eth_getTransactionReceipt` on an exponential backoff cadence
 * (3s → 4.5s → 6.75s → ... capped at 15s) and pushes [TxStatusState] updates
 * through [onState]. Scoped to a `LaunchedEffect(txHash)` so leaving the
 * screen cancels the loop via structured concurrency — no `GlobalScope`,
 * no detached scope.
 *
 * Elapsed time is computed by accumulating the scheduled [delay] intervals;
 * this avoids a `kotlinx.datetime` dependency and is precise enough for the
 * `mm:ss` display and the 5-minute soft-timeout check. Per CLAUDE.md §4.2 no
 * `kotlin.random.Random` is used; per §4.1 neither the tx hash nor the
 * receipt is logged.
 */
@Composable
fun TxPollingEffect(
    txHash: String,
    rpc: RpcClient,
    onState: (TxStatusState) -> Unit,
) {
    LaunchedEffect(txHash) {
        var backoffMs: Long = INITIAL_BACKOFF_MS
        var elapsedMs: Long = 0L
        while (true) {
            val receipt = rpc.getTransactionReceipt(txHash)
            if (receipt != null) {
                when (receipt.status) {
                    "0x1" -> {
                        onState(TxStatusState.Confirmed(receipt))
                        return@LaunchedEffect
                    }
                    "0x0" -> {
                        onState(TxStatusState.Failed("Transaction reverted"))
                        return@LaunchedEffect
                    }
                    else -> {
                        // Unknown status — stay pending defensively rather than crash.
                    }
                }
            }
            val elapsedSeconds = (elapsedMs / 1000L).toInt()
            onState(
                TxStatusState.Pending(
                    elapsedSeconds = elapsedSeconds,
                    stalled = elapsedSeconds > SOFT_TIMEOUT_SECONDS,
                ),
            )
            delay(backoffMs)
            elapsedMs += backoffMs
            backoffMs = (backoffMs * BACKOFF_FACTOR_NUM / BACKOFF_FACTOR_DEN)
                .coerceAtMost(MAX_BACKOFF_MS)
        }
    }
}

package xyz.wallet.toolkit.rpc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

sealed interface ReceiptPollingState {
    data class Pending(
        val elapsedSeconds: Int,
        val retrying: Boolean,
        val stalled: Boolean,
    ) : ReceiptPollingState

    data class Received(val receipt: TransactionReceipt) : ReceiptPollingState
}

/** A timeout describes delayed confirmation; it never proves that a transaction failed. */
suspend fun pollTransactionReceipt(
    transactionHash: String,
    fetchReceipt: suspend (String) -> TransactionReceipt?,
    onState: (ReceiptPollingState) -> Unit,
    initialBackoffMillis: Long = 3_000,
    maxBackoffMillis: Long = 15_000,
    softTimeoutSeconds: Int = 300,
): TransactionReceipt {
    require(initialBackoffMillis > 0 && maxBackoffMillis >= initialBackoffMillis)
    require(softTimeoutSeconds >= 0)
    var backoffMillis = initialBackoffMillis
    var elapsedMillis = 0L
    while (true) {
        var retrying = false
        val receipt = try {
            fetchReceipt(transactionHash)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            retrying = true
            null
        }
        if (receipt?.status == "0x1" || receipt?.status == "0x0") {
            onState(ReceiptPollingState.Received(receipt))
            return receipt
        }
        val elapsedSeconds = (elapsedMillis / 1_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        onState(
            ReceiptPollingState.Pending(
                elapsedSeconds = elapsedSeconds,
                retrying = retrying,
                stalled = elapsedSeconds >= softTimeoutSeconds,
            ),
        )
        delay(backoffMillis)
        elapsedMillis = if (Long.MAX_VALUE - elapsedMillis < backoffMillis) Long.MAX_VALUE
        else elapsedMillis + backoffMillis
        backoffMillis = if (backoffMillis >= maxBackoffMillis - backoffMillis / 2) maxBackoffMillis
        else backoffMillis + backoffMillis / 2
    }
}

suspend fun RpcClient.pollTransactionReceipt(
    transactionHash: String,
    onState: (ReceiptPollingState) -> Unit,
    initialBackoffMillis: Long = 3_000,
    maxBackoffMillis: Long = 15_000,
    softTimeoutSeconds: Int = 300,
): TransactionReceipt = pollTransactionReceipt(
    transactionHash = transactionHash,
    fetchReceipt = ::getTransactionReceipt,
    onState = onState,
    initialBackoffMillis = initialBackoffMillis,
    maxBackoffMillis = maxBackoffMillis,
    softTimeoutSeconds = softTimeoutSeconds,
)

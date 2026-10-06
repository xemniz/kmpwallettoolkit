package xyz.wallet.toolkit.rpc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptPollingTest {
    @Test
    fun transientErrorReportsRetryAndLaterReturnsReceipt() = runTest {
        var attempts = 0
        val states = mutableListOf<ReceiptPollingState>()
        val receipt = successfulReceipt()

        val result = pollTransactionReceipt(
            transactionHash = "0xhash",
            fetchReceipt = {
                attempts++
                when (attempts) {
                    1 -> throw IllegalStateException("Private upstream error details")
                    2 -> null
                    else -> receipt
                }
            },
            onState = states::add,
            initialBackoffMillis = 10,
            maxBackoffMillis = 20,
        )

        assertEquals(receipt, result)
        assertTrue((states[0] as ReceiptPollingState.Pending).retrying)
        assertEquals(false, (states[1] as ReceiptPollingState.Pending).retrying)
        assertEquals(ReceiptPollingState.Received(receipt), states.last())
        assertEquals(25, testScheduler.currentTime)
    }

    @Test
    fun cancellationStopsPollingWithoutShowingRetry() = runTest {
        val states = mutableListOf<ReceiptPollingState>()

        assertFailsWith<CancellationException> {
            pollTransactionReceipt(
                transactionHash = "0xhash",
                fetchReceipt = { throw CancellationException("Stopped") },
                onState = states::add,
            )
        }

        assertTrue(states.isEmpty())
    }

    @Test
    fun softTimeoutAndUnknownStatusRemainPendingWithCappedBackoff() = runTest {
        val attempts = mutableListOf<Long>()
        val states = mutableListOf<ReceiptPollingState>()
        val polling = async {
            pollTransactionReceipt(
                transactionHash = "0xhash",
                fetchReceipt = {
                    attempts.add(testScheduler.currentTime)
                    if (attempts.size == 6) successfulReceipt().copy(status = "0x0")
                    else successfulReceipt().copy(status = "0x2")
                },
                onState = states::add,
                initialBackoffMillis = 1_000,
                maxBackoffMillis = 2_000,
                softTimeoutSeconds = 2,
            )
        }
        runCurrent()
        advanceTimeBy(8_500)
        runCurrent()

        assertEquals("0x0", polling.await().status)
        assertEquals(listOf(0L, 1_000L, 2_500L, 4_500L, 6_500L, 8_500L), attempts)
        assertTrue((states[2] as ReceiptPollingState.Pending).stalled)
        assertEquals(ReceiptPollingState.Received(successfulReceipt().copy(status = "0x0")), states.last())
    }
}

private fun successfulReceipt() = TransactionReceipt(
    transactionHash = "0xhash",
    transactionIndex = "0x0",
    blockHash = "0xblock",
    blockNumber = "0x1",
    from = "0xowner",
    gasUsed = "0x5208",
    cumulativeGasUsed = "0x5208",
    status = "0x1",
    logsBloom = "0x0",
)

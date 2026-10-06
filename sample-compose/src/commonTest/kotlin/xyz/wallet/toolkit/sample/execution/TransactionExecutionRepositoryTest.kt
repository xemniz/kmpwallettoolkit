package xyz.wallet.toolkit.sample.execution

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.flows.send.SendState
import xyz.wallet.toolkit.sample.flows.swap.QuoteTransaction
import xyz.wallet.toolkit.sample.flows.swap.SwapQuote
import xyz.wallet.toolkit.sample.flows.swap.TokenRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionExecutionRepositoryTest {
    @Test
    fun recreatingTheRootUiPreservesTheActiveSessionExecution() = runTest {
        val refreshed = CompletableDeferred<SwapQuote>()
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = TransactionExecutionRepository(MemoryJournal(), { rpc }, ::hashSigned, { _, _, _ -> refreshed.await() }, backgroundScope)
        repository.attachSession(wallet, epoch = 1)
        repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        rpc.allowance = "100"
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        advanceTimeBy(3_000)
        runCurrent()

        repository.attachSession(wallet, epoch = 1)
        refreshed.complete(tokenQuote())
        runCurrent()

        assertEquals(2, rpc.broadcasts.size)
        assertEquals(listOf(7L, 7L), wallet.signedNonces)
    }

    @Test
    fun invalidPersistedAssetMetadataBlocksRecoveryAndSubmissions() = runTest {
        val journal = MemoryJournal()
        val original = repository(journal, FakeExecutionRpc(), FakeExecutionWallet())
        original.start(original.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        original.revokeSession()
        journal.serialized = journal.serialized!!.replace("\"sellDecimals\":6", "\"sellDecimals\":-1")
        val restored = repository(journal, FakeExecutionRpc(), FakeExecutionWallet())
        assertTrue(restored.storageError.value)
        assertTrue(restored.operations.value.isEmpty())
        assertFailsWith<ExecutionRejected> { restored.start(restored.sendReview()) }
    }

    @Test
    fun nativeSalesNeverReadErc20AllowanceEvenWhenTheApiSuppliesATarget() = runTest {
        val rpc = FakeExecutionRpc().apply { allowanceThrows = true }
        val repository = repository(MemoryJournal(), rpc, FakeExecutionWallet())
        val quote = tokenQuote().copy(sell = tokenQuote().buy)
        repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, quote))
        runCurrent()
        assertEquals(1, rpc.broadcasts.size)
    }

    @Test
    fun mutatingPublishedProgressCannotChangeTheMonitoredIdentity() = runTest {
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = repository(MemoryJournal(), rpc, wallet)
        repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        val exposed = repository.operations.value.single().steps as MutableList<StepProgress>
        exposed[0] = exposed[0].copy(hash = SECOND_HASH)
        rpc.allowance = "100"
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(2, wallet.signedNonces.size)
        assertEquals(HASH, repository.operations.value.single().steps.first().hash)
    }

    @Test
    fun anApprovalStillHoldsItsNetworkLockWhileRefreshingTheQuote() = runTest {
        val refreshed = CompletableDeferred<SwapQuote>()
        val rpc = FakeExecutionRpc()
        val repository = TransactionExecutionRepository(MemoryJournal(), { rpc }, ::hashSigned, { _, _, _ -> refreshed.await() }, backgroundScope)
        repository.attachSession(FakeExecutionWallet(), epoch = 1)
        repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        rpc.allowance = "100"
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        advanceTimeBy(3_000)
        runCurrent()
        assertFailsWith<ExecutionRejected> { repository.start(repository.sendReview()) }
        refreshed.complete(tokenQuote())
        runCurrent()
        assertEquals(2, rpc.broadcasts.size)
    }

    @Test
    fun mutatingAnExposedReviewCannotChangeWhatWasAuthorized() = runTest {
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = repository(MemoryJournal(), rpc, wallet)
        val review = repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote())
        repository.start(review)
        runCurrent()
        val exposed = review.steps as MutableList<ReviewedStep>
        val original = exposed.last().transaction as ReviewedTransaction.Legacy
        exposed[exposed.lastIndex] = exposed.last().copy(transaction = ReviewedTransaction.Legacy(original.value.copy(to = OWNER)))
        rpc.allowance = "100"
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(RECIPIENT, wallet.signedTransactions.last().to)
    }

    @Test
    fun checksumCasingDoesNotChangeTheReviewedTransaction() = runTest {
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val quote = tokenQuote().copy(
            sell = tokenQuote().sell.copy(address = "0xabababababababababababababababababababab"),
            allowanceTarget = "0xcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd",
            transaction = tokenQuote().transaction.copy(to = "0xefefefefefefefefefefefefefefefefefefefef"),
        )
        val repository = TransactionExecutionRepository(MemoryJournal(), { rpc }, ::hashSigned, { _, _, previous -> previous.copy(
            sell = previous.sell.copy(address = "0xABABABABABABABABABABABABABABABABABABABAB"),
            allowanceTarget = "0xCDCDCDCDCDCDCDCDCDCDCDCDCDCDCDCDCDCDCDCD",
            transaction = previous.transaction.copy(to = "0xEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEF"),
        ) }, backgroundScope)
        repository.attachSession(wallet, epoch = 1)
        repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, quote))
        runCurrent()
        rpc.allowance = "100"
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(2, rpc.broadcasts.size)
        assertEquals(quote.transaction.to, wallet.signedTransactions.last().to)
    }

    @Test
    fun aLaterReviewInvalidatesTheEarlierReviewOnThatNetwork() = runTest {
        val repository = repository(MemoryJournal(), FakeExecutionRpc(), FakeExecutionWallet())
        val previous = repository.sendReview()
        repository.sendReview()
        assertFailsWith<ExecutionRejected> { repository.start(previous) }
    }

    @Test
    fun malformedAllowanceCannotBeTreatedAsZero() = runTest {
        val rpc = FakeExecutionRpc().apply { allowance = "garbled" }
        val repository = repository(MemoryJournal(), rpc, FakeExecutionWallet())
        assertFailsWith<ExecutionRejected> { repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()) }
        assertTrue(rpc.broadcasts.isEmpty())
    }

    @Test
    fun signOutDuringBroadcastRetainsThePreparedIdentity() = runTest {
        val journal = MemoryJournal()
        val rpc = FakeExecutionRpc()
        val repository = repository(journal, rpc, FakeExecutionWallet())
        rpc.onBroadcast = { repository.revokeSession() }
        repository.start(repository.sendReview())
        runCurrent()
        assertTrue(repository.operations.value.isEmpty())
        val restored = repository(journal, FakeExecutionRpc(), FakeExecutionWallet())
        assertEquals(StepStatus.Prepared, restored.operations.value.single().steps.single().status)
        assertEquals(HASH, restored.operations.value.single().steps.single().hash)
    }

    @Test
    fun aNodeReturningAnotherHashCannotReplaceTheLocalIdentity() = runTest {
        val rpc = FakeExecutionRpc().apply { returnedHash = SECOND_HASH }
        val repository = repository(MemoryJournal(), rpc, FakeExecutionWallet())
        repository.start(repository.sendReview())
        runCurrent()
        assertEquals(HASH, repository.operations.value.single().steps.single().hash)
        assertEquals(OperationStatus.Monitoring, repository.operations.value.single().status)
        assertFailsWith<ExecutionRejected> { repository.start(repository.sendReview()) }
    }

    @Test
    fun anUnknownApprovalOutcomeNeverSignsTheSwap() = runTest {
        val rpc = FakeExecutionRpc().apply { latestNonce = "0x8" }
        val wallet = FakeExecutionWallet()
        val repository = repository(MemoryJournal(), rpc, wallet)
        repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        assertEquals(OperationStatus.UnknownOutcome, repository.operations.value.single().status)
        assertEquals(listOf(7L), wallet.signedNonces)
        assertEquals(1, rpc.broadcasts.size)
    }

    @Test
    fun changesToAnyReviewedTransactionOrAmountRequireFreshReview() = runTest {
        val changes: List<(SwapQuote) -> SwapQuote> = listOf(
            { it.copy(sellAmountRaw = "101") },
            { it.copy(buyAmountRaw = "201") },
            { it.copy(minBuyAmountRaw = "191") },
            { it.copy(allowanceTarget = RECIPIENT) },
            { it.copy(sell = it.sell.copy(address = RECIPIENT)) },
            { it.copy(buy = it.buy.copy(decimals = 17)) },
            { it.copy(transaction = it.transaction.copy(to = TOKEN)) },
            { it.copy(transaction = it.transaction.copy(dataHex = "0xabcd")) },
            { it.copy(transaction = it.transaction.copy(valueWei = "1")) },
            { it.copy(transaction = it.transaction.copy(gasLimit = "90001")) },
            { it.copy(transaction = it.transaction.copy(gasPriceWei = "20000000001")) },
        )
        for ((index, change) in changes.withIndex()) {
            val rpc = FakeExecutionRpc()
            val wallet = FakeExecutionWallet()
            val repository = TransactionExecutionRepository(MemoryJournal(), { rpc }, ::hashSigned, { _, _, quote -> change(quote) }, backgroundScope)
            repository.attachSession(wallet, epoch = 1)
            repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
            runCurrent()
            rpc.allowance = "100"
            rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(OperationStatus.NeedsReview, repository.operations.value.single().status, "Change $index")
            assertEquals(1, rpc.broadcasts.size, "Change $index")
        }
    }

    @Test
    fun aFailedJournalWritePreventsSigningAndBroadcasting() = runTest {
        val journal = MemoryJournal()
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = repository(journal, rpc, wallet)
        journal.writable = false

        assertFailsWith<ExecutionRejected> { repository.start(repository.sendReview()) }
        runCurrent()
        assertTrue(wallet.signedNonces.isEmpty())
        assertTrue(rpc.broadcasts.isEmpty())
        assertTrue(repository.storageError.value)
    }

    @Test
    fun aSignedTransactionIsRecoverableBeforeTheNetworkSeesIt() = runTest {
        val journal = MemoryJournal()
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = repository(journal, rpc, wallet)
        rpc.onBroadcast = {
            val recovered = repository(journal, FakeExecutionRpc(), FakeExecutionWallet())
            assertEquals(StepStatus.Prepared, recovered.operations.value.single().steps.single().status)
            assertEquals(7L, recovered.operations.value.single().steps.single().nonce)
            assertEquals(HASH, recovered.operations.value.single().steps.single().hash)
            recovered.revokeSession()
        }
        repository.start(repository.sendReview())
        runCurrent()
        assertEquals(1, rpc.broadcasts.size)
    }

    @Test
    fun failureToSaveTheSignedIdentityPreventsBroadcast() = runTest {
        val journal = MemoryJournal()
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = repository(journal, rpc, wallet)
        wallet.onSign = { journal.writable = false }
        repository.start(repository.sendReview())
        runCurrent()

        assertTrue(rpc.broadcasts.isEmpty())
        assertEquals(OperationStatus.NeedsReview, repository.operations.value.single().status)
        assertTrue(repository.storageError.value)
    }

    @Test
    fun signingUsesPendingNonceAndReviewsAreConsumedOnce() = runTest {
        val rpc = FakeExecutionRpc().apply { latestNonce = "0x1" }
        val wallet = FakeExecutionWallet()
        val repository = repository(MemoryJournal(), rpc, wallet)
        val review = repository.sendReview()
        repository.start(review)
        assertFailsWith<ExecutionRejected> { repository.start(review) }
        runCurrent()
        assertEquals(listOf(7L), wallet.signedNonces)
    }

    @Test
    fun anotherNetworkCanSubmitWhileTheFirstIsPending() = runTest {
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = repository(MemoryJournal(), rpc, wallet)
        repository.start(repository.sendReview())
        repository.start(repository.sendReview(SupportedChain.Base))
        runCurrent()
        assertEquals(2, rpc.broadcasts.size)
        assertEquals(2, repository.operations.value.size)
    }

    @Test
    fun previousSessionReviewsCannotAuthorizeANewSession() = runTest {
        val repository = repository(MemoryJournal(), FakeExecutionRpc(), FakeExecutionWallet())
        val review = repository.sendReview()
        repository.attachSession(FakeExecutionWallet(), epoch = 2)
        assertFailsWith<ExecutionRejected> { repository.start(review) }
    }

    @Test
    fun signOutWhileFetchingNonceStopsSigning() = runTest {
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = repository(MemoryJournal(), rpc, wallet)
        rpc.onPendingNonce = { repository.revokeSession() }
        repository.start(repository.sendReview())
        runCurrent()
        assertTrue(wallet.signedNonces.isEmpty())
        assertTrue(rpc.broadcasts.isEmpty())
    }

    @Test
    fun anUncertainBroadcastIsMonitoredWithoutResending() = runTest {
        val rpc = FakeExecutionRpc().apply { broadcastThrows = true }
        val repository = repository(MemoryJournal(), rpc, FakeExecutionWallet())
        repository.start(repository.sendReview())
        runCurrent()
        assertFailsWith<ExecutionRejected> { repository.start(repository.sendReview()) }
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, rpc.broadcasts.size)
        assertEquals(OperationStatus.Monitoring, repository.operations.value.single().status)
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(OperationStatus.Confirmed, repository.operations.value.single().status)
    }

    @Test
    fun aConsumedNonceReleasesTheLockWithoutClaimingSuccessOrFailure() = runTest {
        val rpc = FakeExecutionRpc().apply { latestNonce = "0x8" }
        val repository = repository(MemoryJournal(), rpc, FakeExecutionWallet())
        repository.start(repository.sendReview())
        runCurrent()
        assertEquals(OperationStatus.UnknownOutcome, repository.operations.value.single().status)
        assertEquals(StepStatus.NonceConsumedUnknownOutcome, repository.operations.value.single().steps.single().status)
        rpc.nextNonce = "0x8"
        repository.start(repository.sendReview())
        runCurrent()
        assertEquals(2, rpc.broadcasts.size)
    }

    @Test
    fun receiptErrorsRemainRecoverableAndSignOutCancelsPolling() = runTest {
        val rpc = FakeExecutionRpc().apply { receiptThrows = true }
        val repository = repository(MemoryJournal(), rpc, FakeExecutionWallet())
        repository.start(repository.sendReview())
        runCurrent()
        assertEquals(OperationStatus.Monitoring, repository.operations.value.single().status)
        advanceTimeBy(30_000)
        runCurrent()
        val callsAtSignOut = rpc.receiptCalls
        repository.revokeSession()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(callsAtSignOut, rpc.receiptCalls)
        assertEquals(1, rpc.broadcasts.size)
    }

    @Test
    fun restoringOnlyMonitorsTheMatchingWalletAndNeverSigns() = runTest {
        val journal = MemoryJournal()
        val original = repository(journal, FakeExecutionRpc(), FakeExecutionWallet())
        original.start(original.sendReview())
        runCurrent()
        original.revokeSession()
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet(RECIPIENT)
        val restored = TransactionExecutionRepository(journal, { rpc }, ::hashSigned, { _, _, quote -> quote }, backgroundScope)
        restored.attachSession(wallet, epoch = 1)
        runCurrent()
        assertTrue(restored.operations.value.isEmpty())
        assertEquals(0, rpc.receiptCalls)
        val sameWallet = FakeExecutionWallet()
        restored.attachSession(sameWallet, epoch = 2)
        runCurrent()
        assertEquals(HASH, restored.operations.value.single().steps.single().hash)
        assertTrue(rpc.receiptCalls > 0)
        assertTrue(sameWallet.signedNonces.isEmpty())
        assertTrue(rpc.broadcasts.isEmpty())
    }

    @Test
    fun unreadableJournalBlocksSubmissionsAndRetryDoesNotSign() = runTest {
        val journal = MemoryJournal().apply { readable = false }
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = repository(journal, rpc, wallet)
        assertTrue(repository.storageError.value)
        assertFailsWith<ExecutionRejected> { repository.start(repository.sendReview()) }
        journal.readable = true
        assertTrue(repository.retryStorage())
        runCurrent()
        assertTrue(wallet.signedNonces.isEmpty())
        assertTrue(rpc.broadcasts.isEmpty())
    }

    @Test
    fun aMalformedJournalCannotBeSilentlyOverwritten() = runTest {
        val journal = MemoryJournal().apply { serialized = "{\"version\":999,\"nextId\":1}" }
        val repository = repository(journal, FakeExecutionRpc(), FakeExecutionWallet())
        assertTrue(repository.storageError.value)
        assertFalse(repository.retryStorage())
        assertEquals("{\"version\":999,\"nextId\":1}", journal.serialized)
    }

    @Test
    fun zeroResetAndExactApprovalWaitForReceiptsAndFreshPendingNonces() = runTest {
        val rpc = FakeExecutionRpc().apply { allowance = "50" }
        val wallet = FakeExecutionWallet()
        val repository = repository(MemoryJournal(), rpc, wallet)
        val review = repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote())
        assertEquals(listOf(StepKind.ResetAllowance, StepKind.Approve, StepKind.Swap), review.steps.map { it.kind })
        assertEquals(listOf("0", "100", null), review.steps.map { it.approvalAmountRaw })
        repository.start(review)
        runCurrent()
        assertEquals(listOf(7L), wallet.signedNonces)
        assertEquals(1, rpc.broadcasts.size)
        rpc.allowance = "0"
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        rpc.nextNonce = "0x9"
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(7L, 9L), wallet.signedNonces)
        assertEquals(2, rpc.broadcasts.size)
        rpc.allowance = "100"
        rpc.receipt = ExecutionReceipt(SECOND_HASH, succeeded = true)
        rpc.nextNonce = "0xb"
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(7L, 9L, 11L), wallet.signedNonces)
        assertEquals(3, rpc.broadcasts.size)
        assertEquals(StepKind.Swap, repository.operations.value.single().steps.last().kind)
    }

    @Test
    fun sufficientExistingAllowanceNeedsNoApproval() = runTest {
        val rpc = FakeExecutionRpc().apply { allowance = "10000000000000000000000000000000000000000000000000000000000000000" }
        val repository = repository(MemoryJournal(), rpc, FakeExecutionWallet())
        val review = repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote())
        assertEquals(listOf(StepKind.Swap), review.steps.map { it.kind })
        repository.start(review)
        runCurrent()
        assertEquals(1, rpc.broadcasts.size)
    }

    @Test
    fun anExplicitNullTargetDoesNotInventASpender() = runTest {
        val rpc = FakeExecutionRpc().apply { allowanceThrows = true }
        val repository = repository(MemoryJournal(), rpc, FakeExecutionWallet())
        val review = repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote().copy(allowanceTarget = null))
        repository.start(review)
        runCurrent()
        assertEquals(1, rpc.broadcasts.size)
    }

    @Test
    fun aRevertedApprovalStopsTheSwap() = runTest {
        val rpc = FakeExecutionRpc().apply { receipt = ExecutionReceipt(HASH, succeeded = false) }
        val wallet = FakeExecutionWallet()
        val repository = repository(MemoryJournal(), rpc, wallet)
        repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        assertEquals(OperationStatus.Reverted, repository.operations.value.single().status)
        assertEquals(1, wallet.signedNonces.size)
        assertEquals(1, rpc.broadcasts.size)
    }

    @Test
    fun changedQuoteTermsAfterApprovalRequireAnotherReview() = runTest {
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = TransactionExecutionRepository(MemoryJournal(), { rpc }, ::hashSigned, { _, _, quote -> quote.copy(buyAmountRaw = "201") }, backgroundScope)
        repository.attachSession(wallet, epoch = 1)
        repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        rpc.allowance = "100"
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(OperationStatus.NeedsReview, repository.operations.value.single().status)
        assertEquals(1, rpc.broadcasts.size)
    }

    @Test
    fun aNewReviewCanReplacePausedWorkWithoutUnresolvedBroadcasts() = runTest {
        val rpc = FakeExecutionRpc()
        val repository = TransactionExecutionRepository(MemoryJournal(), { rpc }, ::hashSigned, { _, _, quote -> quote.copy(transaction = quote.transaction.copy(dataHex = "0xabcd")) }, backgroundScope)
        repository.attachSession(FakeExecutionWallet(), epoch = 1)
        val originalId = repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        rpc.allowance = "100"
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        advanceTimeBy(3_000)
        runCurrent()
        val newId = repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        assertTrue(newId > originalId)
        assertEquals(newId, repository.operations.value.single().id)
        assertEquals(2, rpc.broadcasts.size)
    }

    @Test
    fun restoringAnApprovalOnlyMonitorsThenWaitsForReview() = runTest {
        val journal = MemoryJournal()
        val rpc = FakeExecutionRpc()
        val original = repository(journal, rpc, FakeExecutionWallet())
        original.start(original.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        original.revokeSession()
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        val wallet = FakeExecutionWallet()
        val restored = repository(journal, rpc, wallet)
        runCurrent()
        assertEquals(OperationStatus.NeedsReview, restored.operations.value.single().status)
        assertTrue(wallet.signedNonces.isEmpty())
        assertEquals(1, rpc.broadcasts.size)
        assertEquals("100", restored.operations.value.single().swapIntent!!.sellAmountRaw)
    }

    @Test
    fun postBroadcastStorageFailureStopsFurtherSigningEvenIfReceiptConfirms() = runTest {
        val journal = MemoryJournal()
        val rpc = FakeExecutionRpc()
        val wallet = FakeExecutionWallet()
        val repository = repository(journal, rpc, wallet)
        rpc.onBroadcast = { journal.writable = false }
        repository.start(repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote()))
        runCurrent()
        rpc.allowance = "100"
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(OperationStatus.NeedsReview, repository.operations.value.single().status)
        assertEquals(1, wallet.signedNonces.size)
        assertEquals(1, rpc.broadcasts.size)
    }

    @Test
    fun anAllowanceChangeCannotInsertAnUnreviewedApproval() = runTest {
        val rpc = FakeExecutionRpc().apply { allowance = "100" }
        val wallet = FakeExecutionWallet()
        val repository = TransactionExecutionRepository(MemoryJournal(), { rpc }, { HASH }, { _, _, quote -> quote }, backgroundScope)
        repository.attachSession(wallet, epoch = 1)
        val review = repository.prepareSwap(SupportedChain.Ethereum, OWNER, tokenQuote())
        rpc.onPendingNonce = { rpc.allowance = "0" }
        repository.start(review)
        runCurrent()

        assertTrue(wallet.signedNonces.isEmpty())
        assertEquals(OperationStatus.NeedsReview, repository.operations.value.single().status)
    }
    @Test
    fun signOutDuringReceiptRequestRetainsTheUnfinishedRecord() = runTest {
        val rpc = FakeExecutionRpc()
        val journal = MemoryJournal()
        val repository = TransactionExecutionRepository(journal, { rpc }, { HASH }, { _, _, quote -> quote }, backgroundScope)
        repository.attachSession(FakeExecutionWallet(), epoch = 1)
        rpc.onReceipt = { repository.revokeSession() }
        rpc.receipt = ExecutionReceipt(HASH, succeeded = true)
        repository.start(repository.sendReview())
        runCurrent()

        assertTrue(repository.operations.value.isEmpty())
        assertTrue(journal.serialized!!.contains(HASH), "A cancelled monitor must not erase the recoverable broadcast.")
    }

    @Test
    fun aPendingSendBlocksAnotherOperationOnItsWalletAndNetwork() = runTest {
        val rpc = FakeExecutionRpc()
        val repository = TransactionExecutionRepository(
            journal = MemoryJournal(),
            rpcForChain = { rpc },
            hashSignedTransaction = { HASH },
            refreshQuote = { _, _, quote -> quote },
            scope = backgroundScope,
        )
        repository.attachSession(FakeExecutionWallet(), epoch = 1)
        val id = repository.start(repository.sendReview())
        runCurrent()

        assertEquals(OperationStatus.Monitoring, repository.operations.value.single { it.id == id }.status)
        assertFailsWith<ExecutionRejected> { repository.start(repository.sendReview()) }
    }
}

private fun TransactionExecutionRepository.sendReview(chain: SupportedChain = SupportedChain.Ethereum): ExecutionReview =
    prepareSend(chain, OWNER, SendState(chain.id).apply {
        recipientNormalized = RECIPIENT
        amountEth = "1"
        maxFeeGwei = "20"
        maxPriorityGwei = "1"
    })

private class MemoryJournal : ExecutionJournalStore {
    var serialized: String? = null
    var writable = true
    var readable = true
    override fun load(): String? {
        check(readable) { "Storage is unavailable" }
        return serialized
    }
    override fun save(serialized: String): Boolean {
        if (!writable) return false
        this.serialized = serialized
        return true
    }
}

private class FakeExecutionWallet(private val owner: String = OWNER) : ExecutionWallet {
    val signedNonces = mutableListOf<Long>()
    val signedTransactions = mutableListOf<ReviewedTransaction>()
    var onSign: (() -> Unit)? = null
    override fun address(chain: SupportedChain): String = owner
    override fun sign(chain: SupportedChain, transaction: ReviewedTransaction, nonce: Long): ByteArray {
        signedNonces += nonce
        signedTransactions += transaction
        onSign?.invoke()
        return byteArrayOf(signedNonces.size.toByte())
    }
}

private class FakeExecutionRpc : ExecutionRpc {
    var nextNonce = "0x7"
    var latestNonce = "0x7"
    var receipt: ExecutionReceipt? = null
    var allowance = "0"
    var onReceipt: (() -> Unit)? = null
    var onPendingNonce: (() -> Unit)? = null
    var onBroadcast: (() -> Unit)? = null
    var broadcastThrows = false
    var receiptThrows = false
    var allowanceThrows = false
    var returnedHash: String? = null
    var receiptCalls = 0
    val broadcasts = mutableListOf<String>()
    override suspend fun nonce(owner: String, blockTag: String): String {
        if (blockTag == "pending") onPendingNonce?.invoke()
        return if (blockTag == "pending") nextNonce else latestNonce
    }
    override suspend fun broadcast(rawSignedTransaction: String): String {
        broadcasts += rawSignedTransaction
        onBroadcast?.invoke()
        if (broadcastThrows) error("Connection interrupted")
        return returnedHash ?: when (rawSignedTransaction) {
            "0x01" -> HASH
            "0x02" -> SECOND_HASH
            else -> THIRD_HASH
        }
    }
    override suspend fun receipt(hash: String): ExecutionReceipt? {
        onReceipt?.also { onReceipt = null }?.invoke()
        receiptCalls += 1
        if (receiptThrows) error("Connection interrupted")
        return receipt
    }
    override suspend fun allowance(token: String, owner: String, spender: String): String {
        if (allowanceThrows) error("No allowance endpoint expected")
        return allowance
    }
}

private fun TestScope.repository(journal: MemoryJournal, rpc: FakeExecutionRpc, wallet: FakeExecutionWallet): TransactionExecutionRepository =
    TransactionExecutionRepository(journal, { rpc }, ::hashSigned, { _, _, quote -> quote }, backgroundScope).also {
        it.attachSession(wallet, epoch = 1)
    }

private fun hashSigned(bytes: ByteArray): String = when (bytes.single().toInt()) {
    1 -> HASH
    2 -> SECOND_HASH
    else -> THIRD_HASH
}

private const val OWNER = "0x1111111111111111111111111111111111111111"
private const val RECIPIENT = "0x2222222222222222222222222222222222222222"
private const val HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
private const val SECOND_HASH = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
private const val THIRD_HASH = "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
private const val TOKEN = "0x3333333333333333333333333333333333333333"
private const val SPENDER = "0x4444444444444444444444444444444444444444"

private fun tokenQuote(): SwapQuote = SwapQuote(
    sell = TokenRef("TOK", "Token", TOKEN, 6, SupportedChain.Ethereum, null),
    buy = TokenRef("ETH", "Ether", null, 18, SupportedChain.Ethereum, null),
    sellAmountRaw = "100",
    buyAmountRaw = "200",
    minBuyAmountRaw = "190",
    transaction = QuoteTransaction(RECIPIENT, "0x1234", "0", "90000", "20000000000"),
    allowanceIssue = null,
    allowanceTarget = SPENDER,
)

package xyz.wallet.toolkit.sample.execution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.flows.swap.SwapAssembler
import xyz.wallet.toolkit.sample.flows.swap.SwapQuote
import xyz.wallet.toolkit.sample.flows.swap.compareDecimal
import xyz.wallet.toolkit.sample.flows.send.SendState
import xyz.wallet.toolkit.sample.flows.send.SendTxAssembler
import xyz.wallet.toolkit.utils.toHexString

/**
 * App-owned execution. All entry points run on the main dispatcher. Only public intent,
 * nonce and hash are durable; a review and permission to sign never survive a session.
 */
class TransactionExecutionRepository(
    private val journal: ExecutionJournalStore,
    private val rpcForChain: (SupportedChain) -> ExecutionRpc,
    private val hashSignedTransaction: (ByteArray) -> String,
    private val refreshQuote: suspend (SupportedChain, String, SwapQuote) -> SwapQuote,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val json = Json { encodeDefaults = true }
    private val records = linkedMapOf<Long, OperationProgress>()
    private val jobs = mutableMapOf<Long, Job>()
    private val reviewSessions = mutableMapOf<ExecutionReview, Pair<Long, Long>>()
    private val mutableOperations = MutableStateFlow<List<OperationProgress>>(emptyList())
    private val mutableStorageError = MutableStateFlow(false)
    private var nextId = 1L
    private var sessionWallet: ExecutionWallet? = null
    private var sessionEpoch = 0L
    private var generation = 0L
    private var loadFailed = false

    val operations: StateFlow<List<OperationProgress>> = mutableOperations.asStateFlow()
    val storageError: StateFlow<Boolean> = mutableStorageError.asStateFlow()

    init {
        readJournal()
    }

    fun attachSession(wallet: ExecutionWallet, epoch: Long) {
        // Root UI recreation reattaches the same app-owned session; its epoch owns authorization.
        if (sessionWallet != null && sessionEpoch == epoch) return
        revokeSession()
        sessionWallet = wallet
        sessionEpoch = epoch
        publish()
        monitorRestored()
    }

    fun revokeSession() {
        generation += 1
        sessionWallet = null
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        reviewSessions.clear()
        records.entries.removeAll { it.value.isResolved() }
        for ((id, record) in records.toMap()) {
            if (!record.isResolved()) records[id] = record.copy(status = OperationStatus.NeedsReview)
        }
        if (records.isNotEmpty() && !loadFailed) persist()
        publish()
    }

    suspend fun prepareSwap(chain: SupportedChain, owner: String, quote: SwapQuote): ExecutionReview {
        val expectedGeneration = generation
        requireSession(owner, chain)
        val review = SwapAssembler(rpcForChain(chain), chain).prepare(owner.lowercase(), quote)
        requireGeneration(expectedGeneration)
        issueReview(review)
        return review
    }

    fun prepareSend(chain: SupportedChain, owner: String, state: SendState): ExecutionReview {
        requireSession(owner, chain)
        return SendTxAssembler().review(state, chain, owner).also(::issueReview)
    }

    /** Persist the operation identity before returning it to the screen. */
    fun start(review: ExecutionReview): Long {
        if (mutableStorageError.value) throw ExecutionRejected("Transaction storage is unavailable. Try again.")
        requireSession(review.owner, review.chain)
        if (reviewSessions[review] != (generation to sessionEpoch)) {
            throw ExecutionRejected("The active session changed. Review the transaction again.")
        }
        if (records.values.any { it.owner == review.owner && it.chainId == review.chain.id &&
                (it.isLocked() || jobs[it.id]?.isActive == true)
            }
        ) {
            throw ExecutionRejected("A transaction is already in progress for this wallet and network.")
        }
        if (nextId == Long.MAX_VALUE) throw ExecutionRejected("Transaction storage is unavailable.")
        val previous = records.toMap()
        // A new confirmation replaces only paused work without an unresolved broadcast.
        records.entries.removeAll { it.value.owner == review.owner && it.value.chainId == review.chain.id }
        val id = nextId++
        records[id] = OperationProgress(
            id = id,
            owner = review.owner,
            chainId = review.chain.id,
            kind = review.kind,
            steps = review.steps.map { StepProgress(it.kind, it.transaction.to, it.transaction.valueWei, it.approvalAmountRaw) },
            status = OperationStatus.Executing,
            swapIntent = review.swapQuote?.let { quote -> SwapIntent(
                quote.sell.address?.lowercase(), quote.sell.symbol, quote.sell.decimals,
                quote.buy.address?.lowercase(), quote.buy.symbol, quote.buy.decimals,
                quote.sellAmountRaw, quote.buyAmountRaw, quote.minBuyAmountRaw,
            ) },
        )
        if (!persist()) {
            records.clear()
            records.putAll(previous)
            publish()
            throw ExecutionRejected("Could not save transaction progress. Nothing was sent.")
        }
        reviewSessions.remove(review)
        publish()
        val expectedGeneration = generation
        val expectedEpoch = sessionEpoch
        jobs[id] = scope.launch(start = CoroutineStart.LAZY) { execute(id, review, expectedGeneration, expectedEpoch) }
        jobs.getValue(id).start()
        return id
    }

    fun retryStorage(): Boolean {
        if (loadFailed) {
            readJournal()
        } else {
            persist()
        }
        if (!mutableStorageError.value) monitorRestored()
        publish()
        return !mutableStorageError.value
    }

    fun close() {
        revokeSession()
        scope.cancel()
    }

    private suspend fun execute(id: Long, review: ExecutionReview, expectedGeneration: Long, expectedEpoch: Long) {
        val rpc = rpcForChain(review.chain)
        try {
            review.swapQuote?.let { quote ->
                val current = SwapAssembler(rpc, review.chain).prepare(review.owner, quote)
                requireGeneration(expectedGeneration)
                if (current.steps != review.steps) {
                    pause(id, "Allowance changed. Review the swap again.")
                    return
                }
            }
            for ((index, step) in review.steps.withIndex()) {
                requireAuthorization(expectedGeneration, expectedEpoch)
                if (mutableStorageError.value) {
                    pause(id, "Transaction storage is unavailable. Review again after retrying.")
                    return
                }
                val nonce = parseNonceHex(rpc.nonce(review.owner, "pending"))
                    ?: throw ExecutionRejected("Could not fetch account nonce.")
                requireAuthorization(expectedGeneration, expectedEpoch)
                if (step.kind == StepKind.Swap && !verifySwapAllowance(id, review, rpc, expectedGeneration)) return
                val signed = sessionWallet!!.sign(review.chain, step.transaction, nonce).copyOf()
                requireAuthorization(expectedGeneration, expectedEpoch)
                val rawSignedTransaction = signed.toHexString()
                val hash = hashSignedTransaction(signed.copyOf()).lowercase()
                if (!hash.isTransactionHash()) throw ExecutionRejected("Could not identify the signed transaction.")
                updateStep(id, index, StepStatus.Prepared, nonce, hash)
                if (!persist()) {
                    updateStep(id, index, StepStatus.Planned, null, null)
                    pause(id, "Could not save transaction progress. Nothing was sent.")
                    return
                }
                requireAuthorization(expectedGeneration, expectedEpoch)
                var uncertain = false
                try {
                    val returnedHash = rpc.broadcast(rawSignedTransaction)
                    // The locally computed identity remains authoritative even if the node disagrees.
                    uncertain = !returnedHash.equals(hash, ignoreCase = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    uncertain = true
                }
                requireAuthorization(expectedGeneration, expectedEpoch)
                updateStep(id, index, StepStatus.Pending, nonce, hash)
                updateStatus(id, OperationStatus.Monitoring, if (uncertain) "Submission outcome is uncertain. Checking the blockchain." else null)
                val durable = persist()
                publish()
                val outcome = awaitOutcome(id, index, rpc, expectedGeneration)
                if (outcome != StepStatus.Confirmed) {
                    finish(id, if (outcome == StepStatus.Reverted) OperationStatus.Reverted else OperationStatus.UnknownOutcome)
                    return
                }
                if (index == review.steps.lastIndex) {
                    finish(id, OperationStatus.Confirmed)
                    return
                }
                requireAuthorization(expectedGeneration, expectedEpoch)
                if (!durable || mutableStorageError.value) {
                    pause(id, "Transaction storage is unavailable. Review again after retrying.")
                    return
                }
                if (!verifyApproval(id, step, review, rpc, expectedGeneration)) return
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            requireGeneration(expectedGeneration)
            val record = records[id] ?: return
            if (record.steps.any { it.status.isUnresolved() }) {
                updateStatus(id, OperationStatus.Monitoring, "Checking the blockchain for this transaction.")
                persist()
            } else if (record.steps.any { it.status == StepStatus.Confirmed }) {
                pause(id, "The next step could not complete. Review again to continue.")
            } else {
                finish(id, OperationStatus.Failed, "The transaction could not be prepared. Nothing was sent.")
            }
        } finally {
            if (generation == expectedGeneration) jobs.remove(id)
            publish()
        }
    }

    private suspend fun verifySwapAllowance(
        id: Long,
        review: ExecutionReview,
        rpc: ExecutionRpc,
        expectedGeneration: Long,
    ): Boolean {
        val quote = review.swapQuote ?: return true
        if (quote.sell.isNative) return true
        val spender = quote.allowanceTarget ?: return true
        val token = quote.sell.address ?: throw ExecutionRejected("Invalid token.")
        val allowance = rpc.allowance(token, review.owner, spender)
        requireGeneration(expectedGeneration)
        if (!allowance.isUnsignedDecimal()) throw ExecutionRejected("Could not read allowance.")
        if (compareDecimal(allowance, quote.sellAmountRaw) < 0) {
            pause(id, "Allowance changed. Review the swap again.")
            return false
        }
        return true
    }

    private suspend fun verifyApproval(
        id: Long,
        step: ReviewedStep,
        review: ExecutionReview,
        rpc: ExecutionRpc,
        expectedGeneration: Long,
    ): Boolean {
        val quote = review.swapQuote ?: return true
        val spender = quote.allowanceTarget ?: return true
        val allowance = rpc.allowance(quote.sell.address ?: throw ExecutionRejected("Invalid token."), review.owner, spender)
        if (!allowance.isUnsignedDecimal()) throw ExecutionRejected("Could not read allowance.")
        requireGeneration(expectedGeneration)
        val acceptable = if (step.kind == StepKind.ResetAllowance) compareDecimal(allowance, "0") == 0
            else compareDecimal(allowance, quote.sellAmountRaw) >= 0
        if (!acceptable) {
            pause(id, "Allowance did not match the reviewed step. Review the swap again.")
            return false
        }
        val refreshed = refreshQuote(review.chain, review.owner, quote)
        requireGeneration(expectedGeneration)
        if (!quote.hasSameReviewedTerms(refreshed)) {
            pause(id, "The quote changed. Review the swap again.")
            return false
        }
        updateStatus(id, OperationStatus.Executing)
        publish()
        return true
    }

    private suspend fun awaitOutcome(id: Long, index: Int, rpc: ExecutionRpc, expectedGeneration: Long): StepStatus {
        var backoff = 3_000L
        while (true) {
            requireGeneration(expectedGeneration)
            val step = records.getValue(id).steps[index]
            val receipt = try {
                rpc.receipt(step.hash!!)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                requireGeneration(expectedGeneration)
                updateStatus(id, OperationStatus.Monitoring, "Connection interrupted. Retrying transaction status.")
                null
            }
            requireGeneration(expectedGeneration)
            if (receipt != null && receipt.hash.equals(step.hash, ignoreCase = true)) {
                val status = if (receipt.succeeded) StepStatus.Confirmed else StepStatus.Reverted
                updateStep(id, index, status, step.nonce, step.hash)
                persist()
                publish()
                return status
            }
            val canonicalNonce = try {
                parseNonceHex(rpc.nonce(records.getValue(id).owner, "latest"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            requireGeneration(expectedGeneration)
            if (canonicalNonce != null && canonicalNonce > step.nonce!!) {
                updateStep(id, index, StepStatus.NonceConsumedUnknownOutcome, step.nonce, step.hash)
                persist()
                publish()
                return StepStatus.NonceConsumedUnknownOutcome
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(15_000L)
        }
    }

    private fun monitorRestored() {
        val wallet = sessionWallet ?: return
        for ((id, record) in records.toMap()) {
            if (jobs.containsKey(id)) continue
            val chain = SupportedChain.entries.firstOrNull { it.id == record.chainId } ?: continue
            if (!wallet.address(chain).equals(record.owner, ignoreCase = true)) continue
            val index = record.steps.indexOfFirst { it.status.isUnresolved() }
            if (index < 0) continue
            updateStatus(id, OperationStatus.Monitoring)
            val expectedGeneration = generation
            jobs[id] = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val outcome = awaitOutcome(id, index, rpcForChain(chain), expectedGeneration)
                    if (outcome == StepStatus.Confirmed && index < record.steps.lastIndex) {
                        pause(id, "Review the remaining steps to continue.")
                    } else {
                        finish(id, when (outcome) {
                            StepStatus.Confirmed -> OperationStatus.Confirmed
                            StepStatus.Reverted -> OperationStatus.Reverted
                            else -> OperationStatus.UnknownOutcome
                        })
                    }
                } finally {
                    if (generation == expectedGeneration) jobs.remove(id)
                    publish()
                }
            }
            jobs.getValue(id).start()
        }
        publish()
    }

    private fun pause(id: Long, message: String) {
        updateStatus(id, OperationStatus.NeedsReview, message)
        persist()
        publish()
    }

    private fun finish(id: Long, status: OperationStatus, message: String? = null) {
        updateStatus(id, status, message)
        persist()
        publish()
    }

    private fun updateStep(id: Long, index: Int, status: StepStatus, nonce: Long?, hash: String?) {
        val record = records.getValue(id)
        records[id] = record.copy(steps = record.steps.mapIndexed { i, step ->
            if (i == index) step.copy(status = status, nonce = nonce, hash = hash) else step
        })
    }

    private fun updateStatus(id: Long, status: OperationStatus, message: String? = null) {
        records[id] = records.getValue(id).copy(status = status, message = message)
    }

    private fun requireSession(owner: String, chain: SupportedChain) {
        val wallet = sessionWallet ?: throw ExecutionRejected("Sign in before reviewing a transaction.")
        if (!wallet.address(chain).equals(owner, ignoreCase = true)) throw ExecutionRejected("The active wallet changed. Review again.")
    }

    private fun issueReview(review: ExecutionReview) {
        reviewSessions.keys.removeAll { it.owner == review.owner && it.chain == review.chain }
        reviewSessions[review] = generation to sessionEpoch
    }

    private fun requireGeneration(expected: Long) {
        if (generation != expected || sessionWallet == null) throw CancellationException("Session ended")
    }

    private fun requireAuthorization(expectedGeneration: Long, expectedEpoch: Long) {
        requireGeneration(expectedGeneration)
        if (sessionEpoch != expectedEpoch) throw CancellationException("Session ended")
    }

    private fun persist(): Boolean {
        if (loadFailed) return false
        val snapshot = ExecutionSnapshot(nextId = nextId, operations = records.values.filterNot { it.isResolved() })
        val saved = try { journal.save(json.encodeToString(snapshot)) } catch (_: Exception) { false }
        mutableStorageError.value = !saved
        return saved
    }

    private fun readJournal() {
        try {
            val serialized = journal.load()
            val snapshot = if (serialized == null) ExecutionSnapshot() else json.decodeFromString<ExecutionSnapshot>(serialized)
            require(snapshot.version == 1 && snapshot.nextId > 0)
            require(snapshot.operations.map { it.id }.distinct().size == snapshot.operations.size)
            require(snapshot.operations.all { it.validJournalEntry() && it.id < snapshot.nextId })
            require(snapshot.operations.groupBy { it.owner to it.chainId }.all { (_, entries) -> entries.size == 1 })
            records.clear()
            records.putAll(snapshot.operations.associateBy { it.id }.mapValues { (_, record) -> record.copy(status = OperationStatus.NeedsReview, message = null) })
            nextId = snapshot.nextId
            loadFailed = false
            mutableStorageError.value = false
        } catch (_: Exception) {
            loadFailed = true
            mutableStorageError.value = true
        }
        publish()
    }

    private fun publish() {
        val wallet = sessionWallet
        mutableOperations.value = if (wallet == null) emptyList() else records.values.filter { record ->
            val chain = SupportedChain.entries.firstOrNull { it.id == record.chainId }
            chain != null && wallet.address(chain).equals(record.owner, ignoreCase = true)
        }.map { it.copy(steps = it.steps.toList()) }
    }
}

@Serializable
private data class ExecutionSnapshot(
    val version: Int = 1,
    val nextId: Long = 1,
    val operations: List<OperationProgress> = emptyList(),
)

private fun StepStatus.isUnresolved(): Boolean = this == StepStatus.Prepared || this == StepStatus.Pending
private fun OperationProgress.isResolved(): Boolean = status == OperationStatus.Confirmed || status == OperationStatus.Reverted || status == OperationStatus.UnknownOutcome || status == OperationStatus.Failed
private fun OperationProgress.isLocked(): Boolean = steps.any { it.status.isUnresolved() } || status == OperationStatus.Executing
internal fun String.isUnsignedDecimal(): Boolean = isNotEmpty() && all { it in '0'..'9' }
private fun String.isTransactionHash(): Boolean = length == 66 && startsWith("0x") && substring(2).all { it in '0'..'9' || it in 'a'..'f' }

private fun OperationProgress.validJournalEntry(): Boolean =
    id > 0 && owner == owner.lowercase() && owner.isEvmAddress() &&
        SupportedChain.entries.any { it.id == chainId } && steps.isNotEmpty() &&
        (if (kind == OperationKind.Send) swapIntent == null else swapIntent?.validJournalIntent() == true) &&
        steps.count { it.status.isUnresolved() } <= 1 && steps.all { step ->
            step.to.isEvmAddress() && step.valueWei.isUnsignedDecimal() &&
                (step.approvalAmountRaw == null || step.approvalAmountRaw.isUnsignedDecimal()) &&
                if (step.status == StepStatus.Planned) step.nonce == null && step.hash == null
                else step.nonce != null && step.nonce >= 0 && step.hash?.isTransactionHash() == true
        }

private fun SwapIntent.validJournalIntent(): Boolean =
    sellDecimals in 0..255 && buyDecimals in 0..255 &&
        sellAddress.validJournalAssetAddress() && buyAddress.validJournalAssetAddress() &&
        sellAmountRaw.isUnsignedDecimal() && compareDecimal(sellAmountRaw, "0") > 0 &&
        buyAmountRaw.isUnsignedDecimal() && minBuyAmountRaw.isUnsignedDecimal()

private fun String?.validJournalAssetAddress(): Boolean = this == null ||
    (isEvmAddress() && this == lowercase() && substring(2).any { it != '0' })

internal fun String.isEvmAddress(): Boolean = length == 42 && startsWith("0x") && substring(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun SwapQuote.hasSameReviewedTerms(other: SwapQuote): Boolean =
    sell.copy(address = sell.address?.lowercase()) == other.sell.copy(address = other.sell.address?.lowercase()) &&
        buy.copy(address = buy.address?.lowercase()) == other.buy.copy(address = other.buy.address?.lowercase()) &&
        sellAmountRaw == other.sellAmountRaw &&
        buyAmountRaw == other.buyAmountRaw && minBuyAmountRaw == other.minBuyAmountRaw &&
        allowanceTarget?.lowercase() == other.allowanceTarget?.lowercase() &&
        transaction.copy(to = transaction.to.lowercase()) == other.transaction.copy(to = other.transaction.to.lowercase())

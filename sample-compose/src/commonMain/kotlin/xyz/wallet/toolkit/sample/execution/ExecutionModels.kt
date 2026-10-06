package xyz.wallet.toolkit.sample.execution

import kotlinx.serialization.Serializable
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.evm.Eip1559Transaction
import xyz.wallet.toolkit.evm.EvmTransaction
import xyz.wallet.toolkit.evm.signEip1559Transaction
import xyz.wallet.toolkit.evm.signEvmTransaction
import xyz.wallet.toolkit.rpc.RpcCall
import xyz.wallet.toolkit.rpc.RpcClient
import xyz.wallet.toolkit.sample.flows.swap.Erc20
import xyz.wallet.toolkit.sample.flows.swap.SwapQuote
import xyz.wallet.toolkit.sample.flows.swap.compareDecimal

interface ExecutionJournalStore {
    fun load(): String?
    fun save(serialized: String): Boolean
}

interface ExecutionWallet {
    fun address(chain: SupportedChain): String
    fun sign(chain: SupportedChain, transaction: ReviewedTransaction, nonce: Long): ByteArray
}

class WalletExecutionSigner(private val wallet: Wallet) : ExecutionWallet {
    override fun address(chain: SupportedChain): String = wallet.address(chain)

    override fun sign(chain: SupportedChain, transaction: ReviewedTransaction, nonce: Long): ByteArray =
        when (transaction) {
            is ReviewedTransaction.Legacy -> wallet.signEvmTransaction(chain, transaction.value.copy(nonce = nonce))
            is ReviewedTransaction.Type2 -> wallet.signEip1559Transaction(chain, transaction.value.copy(nonce = nonce))
        }

    override fun toString(): String = "WalletExecutionSigner(redacted)"
}

interface ExecutionRpc {
    suspend fun nonce(owner: String, blockTag: String): String
    suspend fun broadcast(rawSignedTransaction: String): String
    suspend fun receipt(hash: String): ExecutionReceipt?
    suspend fun allowance(token: String, owner: String, spender: String): String
}

data class ExecutionReceipt(val hash: String, val succeeded: Boolean)

class RpcExecutionClient(private val client: RpcClient) : ExecutionRpc {
    override suspend fun nonce(owner: String, blockTag: String): String = client.getNonce(owner, blockTag)
    override suspend fun broadcast(rawSignedTransaction: String): String = client.sendRawTransaction(rawSignedTransaction)
    override suspend fun receipt(hash: String): ExecutionReceipt? {
        val receipt = client.getTransactionReceipt(hash) ?: return null
        val status = parseNonceHex(receipt.status)
        if (status != 0L && status != 1L) return null
        return ExecutionReceipt(receipt.transactionHash, succeeded = status == 1L)
    }

    override suspend fun allowance(token: String, owner: String, spender: String): String =
        Erc20.decodeUint256Decimal(client.ethCall(RpcCall(to = token, data = Erc20.allowanceCallData(owner, spender))))
            ?: throw ExecutionRejected("Could not read allowance.")
}

sealed interface ReviewedTransaction {
    val to: String
    val valueWei: String
    val gasLimit: String
    val feePerGasWei: String

    data class Legacy(val value: EvmTransaction) : ReviewedTransaction {
        override val to: String get() = value.to
        override val valueWei: String get() = value.valueWei
        override val gasLimit: String get() = value.gasLimit
        override val feePerGasWei: String get() = value.gasPriceWei
    }

    data class Type2(val value: Eip1559Transaction) : ReviewedTransaction {
        override val to: String get() = value.to
        override val valueWei: String get() = value.valueWei
        override val gasLimit: String get() = value.gasLimit
        override val feePerGasWei: String get() = value.maxFeePerGasWei
    }
}

data class ReviewedStep(
    val kind: StepKind,
    val transaction: ReviewedTransaction,
    val approvalAmountRaw: String? = null,
)

/** A review is consumed once. Its authorization exists only in the signed-in app session. */
class ExecutionReview internal constructor(
    val owner: String,
    val chain: SupportedChain,
    val kind: OperationKind,
    steps: List<ReviewedStep>,
    val swapQuote: SwapQuote? = null,
) {
    private val reviewedSteps: List<ReviewedStep> = steps.toList()
    val steps: List<ReviewedStep> get() = reviewedSteps.toList()

    companion object {
        fun send(
            owner: String,
            chain: SupportedChain,
            to: String,
            valueWei: String,
            maxFeePerGasWei: String,
            maxPriorityFeePerGasWei: String,
        ): ExecutionReview {
            if (!owner.isEvmAddress() || !to.isEvmAddress() || !valueWei.isUnsignedDecimal() ||
                compareDecimal(valueWei, "0") <= 0 || !maxFeePerGasWei.isUnsignedDecimal() ||
                !maxPriorityFeePerGasWei.isUnsignedDecimal() || compareDecimal(maxPriorityFeePerGasWei, maxFeePerGasWei) > 0
            ) throw ExecutionRejected("Amount, recipient or gas values are not valid.")
            return ExecutionReview(
                owner = owner.lowercase(),
                chain = chain,
                kind = OperationKind.Send,
                steps = listOf(ReviewedStep(StepKind.Send, ReviewedTransaction.Type2(Eip1559Transaction(
                    chainId = chain.id,
                    to = to.lowercase(),
                    valueWei = valueWei,
                    maxFeePerGasWei = maxFeePerGasWei,
                    maxPriorityFeePerGasWei = maxPriorityFeePerGasWei,
                    gasLimit = "21000",
                    nonce = 0,
                )))),
            )
        }
    }
}

@Serializable
enum class OperationKind { Send, Swap }

@Serializable
enum class StepKind { Send, ResetAllowance, Approve, Swap }

@Serializable
enum class StepStatus { Planned, Prepared, Pending, Confirmed, Reverted, NonceConsumedUnknownOutcome }

@Serializable
enum class OperationStatus { Executing, Monitoring, NeedsReview, Confirmed, Reverted, UnknownOutcome, Failed }

@Serializable
data class StepProgress(
    val kind: StepKind,
    val to: String,
    val valueWei: String,
    val approvalAmountRaw: String? = null,
    val status: StepStatus = StepStatus.Planned,
    val nonce: Long? = null,
    val hash: String? = null,
)

@Serializable
data class OperationProgress(
    val id: Long,
    val owner: String,
    val chainId: Long,
    val kind: OperationKind,
    val steps: List<StepProgress>,
    val status: OperationStatus,
    val message: String? = null,
    val swapIntent: SwapIntent? = null,
)

@Serializable
data class SwapIntent(
    val sellAddress: String?,
    val sellSymbol: String,
    val sellDecimals: Int,
    val buyAddress: String?,
    val buySymbol: String,
    val buyDecimals: Int,
    val sellAmountRaw: String,
    val buyAmountRaw: String,
    val minBuyAmountRaw: String,
)

class ExecutionRejected(message: String) : IllegalStateException(message)

fun parseNonceHex(raw: String): Long? {
    if (!raw.startsWith("0x", ignoreCase = true)) return null
    val digits = raw.substring(2)
    if (digits.isEmpty() || !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return digits.toLongOrNull(16)?.takeIf { it >= 0 }
}

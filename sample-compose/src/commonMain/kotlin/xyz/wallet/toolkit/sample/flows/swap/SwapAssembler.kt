package xyz.wallet.toolkit.sample.flows.swap

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.evm.EvmTransaction
import xyz.wallet.toolkit.sample.execution.ExecutionRejected
import xyz.wallet.toolkit.sample.execution.ExecutionReview
import xyz.wallet.toolkit.sample.execution.ExecutionRpc
import xyz.wallet.toolkit.sample.execution.OperationKind
import xyz.wallet.toolkit.sample.execution.ReviewedStep
import xyz.wallet.toolkit.sample.execution.ReviewedTransaction
import xyz.wallet.toolkit.sample.execution.StepKind
import xyz.wallet.toolkit.sample.execution.isEvmAddress
import xyz.wallet.toolkit.sample.execution.isUnsignedDecimal

/** Builds the exact approval steps shown before the user authorizes a swap. */
class SwapAssembler(
    private val rpc: ExecutionRpc,
    private val chain: SupportedChain,
) {
    suspend fun prepare(owner: String, quote: SwapQuote): ExecutionReview {
        if (!owner.isEvmAddress() || quote.sell.chain != chain || quote.buy.chain != chain ||
            !quote.sellAmountRaw.isUnsignedDecimal() || compareDecimal(quote.sellAmountRaw, "0") <= 0 ||
            !quote.buyAmountRaw.isUnsignedDecimal() || !quote.minBuyAmountRaw.isUnsignedDecimal() ||
            !quote.transaction.to.isEvmAddress() || !quote.transaction.valueWei.isUnsignedDecimal() ||
            !quote.transaction.gasLimit.isUnsignedDecimal() || !quote.transaction.gasPriceWei.isUnsignedDecimal()
        ) throw ExecutionRejected("The quote is invalid. Request a new quote.")

        val steps = mutableListOf<ReviewedStep>()
        val spender = quote.allowanceTarget
        if (!quote.sell.isNative && spender != null) {
            val token = quote.sell.address
                ?: throw ExecutionRejected("The quote has an invalid approval target.")
            if (!token.isEvmAddress() || !spender.isEvmAddress()) throw ExecutionRejected("The quote has an invalid approval target.")
            val allowance = rpc.allowance(token, owner, spender)
            if (!allowance.isUnsignedDecimal()) throw ExecutionRejected("Could not read allowance.")
            if (compareDecimal(allowance, quote.sellAmountRaw) < 0) {
                if (compareDecimal(allowance, "0") != 0) steps += approvalStep(quote, token, spender, "0", StepKind.ResetAllowance)
                steps += approvalStep(quote, token, spender, quote.sellAmountRaw, StepKind.Approve)
            }
        }
        steps += ReviewedStep(StepKind.Swap, ReviewedTransaction.Legacy(quote.transaction.toSwapTransaction(chain, nonce = 0)))
        return ExecutionReview(owner.lowercase(), chain, OperationKind.Swap, steps, quote)
    }

    private fun approvalStep(quote: SwapQuote, token: String, spender: String, amount: String, kind: StepKind) =
        ReviewedStep(kind, ReviewedTransaction.Legacy(approvalTransactionFor(chain, token, spender, 0, quote.transaction.gasPriceWei, amount)), amount)
}

internal fun QuoteTransaction.toSwapTransaction(chain: SupportedChain, nonce: Long): EvmTransaction = EvmTransaction(
    chainId = chain.id,
    to = to.lowercase(),
    valueWei = valueWei,
    gasPriceWei = gasPriceWei,
    gasLimit = gasLimit,
    nonce = nonce,
    dataHex = dataHex,
)

internal fun approvalTransactionFor(
    chain: SupportedChain,
    sellAddress: String,
    spender: String,
    nonce: Long,
    gasPriceWei: String,
    amountRaw: String,
): EvmTransaction = EvmTransaction(
    chainId = chain.id,
    to = sellAddress.lowercase(),
    valueWei = "0",
    gasPriceWei = gasPriceWei,
    gasLimit = "80000",
    nonce = nonce,
    dataHex = Erc20.approveCallData(spender, amountRaw),
)

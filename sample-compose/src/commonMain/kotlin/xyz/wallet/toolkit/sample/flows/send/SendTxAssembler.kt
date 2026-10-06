package xyz.wallet.toolkit.sample.flows.send

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.execution.ExecutionRejected
import xyz.wallet.toolkit.sample.execution.ExecutionReview
import xyz.wallet.toolkit.sample.format.EthFormat

/** Converts a validated form into an immutable review; signing belongs to app execution. */
class SendTxAssembler {
    fun review(state: SendState, chain: SupportedChain, owner: String): ExecutionReview {
        val recipient = state.recipientNormalized
            ?: throw ExecutionRejected("Recipient address is not valid.")
        if (state.chainId != chain.id || validateRecipient(recipient) != ValidationResult.Valid) {
            throw ExecutionRejected("Recipient address is not valid.")
        }
        val amount = EthFormat.ethDecimalToWei(state.amountEth)
        val maxFee = EthFormat.gweiToWei(state.maxFeeGwei)
        val priority = EthFormat.gweiToWei(state.maxPriorityGwei)
        return ExecutionReview.send(owner, chain, recipient, amount, maxFee, priority)
    }
}

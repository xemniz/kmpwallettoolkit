package xyz.wallet.toolkit.sample.flows.swap

data class SwapQuote(
    val sell: TokenRef,
    val buy: TokenRef,
    val sellAmountRaw: String,
    val buyAmountRaw: String,
    val minBuyAmountRaw: String,
    val transaction: QuoteTransaction,
    val allowanceIssue: AllowanceIssue?,
)

data class QuoteTransaction(
    val to: String,
    val dataHex: String,
    val valueWei: String,
    val gasLimit: String,
)

data class AllowanceIssue(val spender: String)

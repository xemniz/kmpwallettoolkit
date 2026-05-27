package xyz.wallet.toolkit.sample.flows.swap

import xyz.wallet.toolkit.core.SupportedChain

data class SwapUiState(
    val chain: SupportedChain,
    val sell: TokenRef,
    val buy: TokenRef,
    val amountInput: String = "",
    val quote: QuoteStatus = QuoteStatus.Idle,
    val submission: SubmissionStatus = SubmissionStatus.Idle,
    val searchQuery: String = "",
    val searchResults: SearchStatus = SearchStatus.Idle,
)

sealed class QuoteStatus {
    object Idle : QuoteStatus()
    object Loading : QuoteStatus()
    data class Value(val quote: SwapQuote) : QuoteStatus()
    data class Error(val userMessage: String) : QuoteStatus()
}

sealed class SubmissionStatus {
    object Idle : SubmissionStatus()
    object Submitting : SubmissionStatus()
    data class Error(val userMessage: String) : SubmissionStatus()
}

sealed class SearchStatus {
    object Idle : SearchStatus()
    object Loading : SearchStatus()
    data class Value(val tokens: List<TokenRef>) : SearchStatus()
    data class Error(val userMessage: String) : SearchStatus()
}

enum class PickerSide { Sell, Buy }

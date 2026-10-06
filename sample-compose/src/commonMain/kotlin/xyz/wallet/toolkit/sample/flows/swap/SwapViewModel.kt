package xyz.wallet.toolkit.sample.flows.swap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.wallet.toolkit.core.SupportedChain

class AcceptedSwapQuote internal constructor(
    val quote: SwapQuote,
    internal val generation: Long,
)

/** Draft requests belong to the screen; confirmed execution belongs to the app. */
class SwapViewModel(
    val chain: SupportedChain,
    private val zeroEx: SwapQuoteSource,
    private val search: TokenSearchSource,
    private val scope: CoroutineScope,
    private val taker: String,
) {
    private val mutableState = MutableStateFlow(SwapUiState(
        chain = chain,
        sell = DefaultTokens.nativeFor(chain),
        buy = DefaultTokens.defaultBuyFor(chain),
    ))
    val state: StateFlow<SwapUiState> = mutableState.asStateFlow()
    private var generation = 0L
    private var searchGeneration = 0L
    private var quoteJob: Job? = null
    private var searchJob: Job? = null
    private var accepted: AcceptedSwapQuote? = null
    private var disposed = false

    fun onAmountChange(raw: String) {
        changeDraft(state.value.copy(amountInput = raw.filter { it == '.' || it in '0'..'9' }))
    }

    fun restoreDraft(sell: TokenRef, buy: TokenRef, amount: String) {
        require(sell.chain == chain && buy.chain == chain) { "Unsupported token network" }
        changeDraft(state.value.copy(sell = sell, buy = buy, amountInput = amount))
    }

    fun onFlip() {
        val current = state.value
        changeDraft(current.copy(sell = current.buy, buy = current.sell))
    }

    fun onPick(side: PickerSide, token: TokenRef) {
        if (token.chain != chain) return
        val current = state.value
        val next = when (side) {
            PickerSide.Sell -> if (sameToken(token, current.buy)) current.copy(sell = token, buy = current.sell) else current.copy(sell = token)
            PickerSide.Buy -> if (sameToken(token, current.sell)) current.copy(buy = token, sell = current.buy) else current.copy(buy = token)
        }
        onSearchQueryChange("")
        changeDraft(next.copy(searchQuery = "", searchResults = SearchStatus.Idle))
    }

    fun captureQuote(): AcceptedSwapQuote? = accepted

    fun isCurrent(selection: AcceptedSwapQuote): Boolean =
        !disposed && accepted === selection && generation == selection.generation

    fun consumeQuote(selection: AcceptedSwapQuote): Boolean {
        if (!isCurrent(selection)) return false
        accepted = null
        mutableState.value = state.value.copy(quote = QuoteStatus.Idle)
        return true
    }

    private fun changeDraft(next: SwapUiState) {
        generation += 1
        accepted = null
        quoteJob?.cancel()
        mutableState.value = next.copy(quote = QuoteStatus.Idle)
        if (disposed || next.amountInput.isBlank() || sameToken(next.sell, next.buy)) return
        val requestGeneration = generation
        quoteJob = scope.launch {
            delay(400)
            val amount = amountToRaw(next.amountInput, next.sell.decimals)
            if (amount == null) {
                if (generation == requestGeneration) mutableState.value = state.value.copy(quote = QuoteStatus.Error("Invalid amount."))
                return@launch
            }
            mutableState.value = state.value.copy(quote = QuoteStatus.Loading)
            try {
                val quote = zeroEx.fetchQuote(chain, next.sell, next.buy, amount, taker.lowercase())
                require(quote.sellAmountRaw == amount && sameToken(quote.sell, next.sell) && sameToken(quote.buy, next.buy)) { "Quote does not match the request" }
                if (!disposed && generation == requestGeneration) {
                    accepted = AcceptedSwapQuote(quote, requestGeneration)
                    mutableState.value = state.value.copy(quote = QuoteStatus.Value(quote))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!disposed && generation == requestGeneration) mutableState.value = state.value.copy(quote = QuoteStatus.Error("Quote unavailable."))
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        searchGeneration += 1
        searchJob?.cancel()
        mutableState.value = state.value.copy(searchQuery = query, searchResults = SearchStatus.Idle)
        if (disposed || query.isBlank()) return
        val requestGeneration = searchGeneration
        searchJob = scope.launch {
            delay(250)
            mutableState.value = state.value.copy(searchResults = SearchStatus.Loading)
            try {
                val results = search.searchTokens(query.trim(), chain)
                if (!disposed && searchGeneration == requestGeneration) mutableState.value = state.value.copy(searchResults = SearchStatus.Value(results))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!disposed && searchGeneration == requestGeneration) mutableState.value = state.value.copy(searchResults = SearchStatus.Error("Search failed."))
            }
        }
    }

    fun dispose() {
        disposed = true
        generation += 1
        searchGeneration += 1
        accepted = null
        quoteJob?.cancel()
        searchJob?.cancel()
    }
}

internal fun sameToken(a: TokenRef, b: TokenRef): Boolean =
    a.chain == b.chain && a.address?.lowercase() == b.address?.lowercase() && a.decimals == b.decimals

class SwapViewModelFactory(
    private val zeroEx: SwapQuoteSource,
    private val search: TokenSearchSource,
) {
    fun create(chain: SupportedChain, taker: String, scope: CoroutineScope): SwapViewModel =
        SwapViewModel(chain, zeroEx, search, scope, taker)
}

/**
 * Convert a human decimal like `"1.25"` into raw base-units using the
 * sell token's decimals. Returns null on invalid input.
 */
internal fun amountToRaw(input: String, decimals: Int): String? {
    if (input.isBlank() || decimals !in 0..255) return null
    val parts = input.split('.')
    if (parts.size > 2) return null
    val intPart = parts[0].ifEmpty { "0" }
    val fracPart = parts.getOrNull(1) ?: ""
    if (!intPart.all { it in '0'..'9' }) return null
    if (!fracPart.all { it in '0'..'9' }) return null
    if (fracPart.length > decimals) return null
    val padded = fracPart + "0".repeat(decimals - fracPart.length)
    val combined = (intPart + padded).trimStart('0').ifEmpty { "0" }
    if (combined == "0") return null
    return combined
}

/**
 * Convert raw base-units into a short human decimal. Not round-trip
 * safe — trims trailing zeros. Used for display of quote outputs.
 */
internal fun rawToAmount(raw: String, decimals: Int, maxFrac: Int = 6): String {
    require(decimals in 0..255 && maxFrac >= 0) { "Invalid decimals" }
    if (raw.isEmpty() || raw == "0") return "0"
    if (decimals == 0) return raw.trimStart('0').ifEmpty { "0" }
    val padded = if (raw.length <= decimals) "0".repeat(decimals - raw.length + 1) + raw else raw
    val intPart = padded.dropLast(decimals).ifEmpty { "0" }
    val fracPart = padded.takeLast(decimals).take(maxFrac).trimEnd('0')
    return if (fracPart.isEmpty()) intPart else "$intPart.$fracPart"
}

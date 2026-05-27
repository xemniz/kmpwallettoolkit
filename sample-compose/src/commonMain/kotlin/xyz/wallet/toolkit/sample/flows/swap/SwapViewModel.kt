package xyz.wallet.toolkit.sample.flows.swap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet

/**
 * Holds swap state and drives the async work: debounced quote fetch,
 * debounced token search, and swap submission.
 *
 * The chain is pinned at construction and does not change within the
 * screen — parity with Send.
 */
@OptIn(FlowPreview::class)
class SwapViewModel(
    val chain: SupportedChain,
    private val zeroEx: ZeroExClient,
    private val search: TokenSearchClient,
    private val assemblerFactory: SwapAssemblerFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SwapUiState(
            chain = chain,
            sell = DefaultTokens.nativeFor(chain),
            buy = DefaultTokens.defaultBuyFor(chain),
        )
    )
    val state: StateFlow<SwapUiState> = _state.asStateFlow()

    private var quoteJob: Job? = null
    private var searchJob: Job? = null

    init {
        // Debounced quote: refetch 400ms after the user stops typing or
        // swapping tokens.
        _state
            .map { QuoteKey(it.sell, it.buy, it.amountInput) }
            .distinctUntilChanged()
            .debounce(400)
            .filter { it.amountInput.isNotBlank() && it.sell != it.buy }
            .onEach { fetchQuote() }
            .launchIn(viewModelScope)

        // Debounced search.
        _state
            .map { it.searchQuery }
            .distinctUntilChanged()
            .debounce(250)
            .onEach { q -> runSearch(q) }
            .launchIn(viewModelScope)
    }

    fun onAmountChange(raw: String) {
        val sanitized = raw.filter { it == '.' || it in '0'..'9' }
        _state.value = _state.value.copy(
            amountInput = sanitized,
            quote = if (sanitized.isBlank()) QuoteStatus.Idle else _state.value.quote,
        )
    }

    fun onFlip() {
        val s = _state.value
        _state.value = s.copy(sell = s.buy, buy = s.sell, quote = QuoteStatus.Idle)
    }

    fun onPick(side: PickerSide, token: TokenRef) {
        val s = _state.value
        val next = when (side) {
            PickerSide.Sell -> if (token == s.buy) s.copy(sell = token, buy = s.sell) else s.copy(sell = token)
            PickerSide.Buy -> if (token == s.sell) s.copy(buy = token, sell = s.buy) else s.copy(buy = token)
        }
        _state.value = next.copy(
            quote = QuoteStatus.Idle,
            searchQuery = "",
            searchResults = SearchStatus.Idle,
        )
    }

    fun onSearchQueryChange(q: String) {
        _state.value = _state.value.copy(searchQuery = q)
    }

    fun submit(onSuccess: (txHash: String) -> Unit) {
        val s = _state.value
        val quote = (s.quote as? QuoteStatus.Value)?.quote ?: return
        val attachedWallet = wallet ?: run {
            _state.value = s.copy(submission = SubmissionStatus.Error("No wallet."))
            return
        }
        val assembler = assemblerFactory.create(attachedWallet, chain)
        _state.value = s.copy(submission = SubmissionStatus.Submitting)
        viewModelScope.launch {
            when (val result = assembler.execute(quote)) {
                is SwapResult.Success -> {
                    _state.value = _state.value.copy(submission = SubmissionStatus.Idle)
                    onSuccess(result.txHash)
                }
                is SwapResult.Failure -> {
                    _state.value = _state.value.copy(submission = SubmissionStatus.Error(result.userMessage))
                }
            }
        }
    }

    private fun fetchQuote() {
        quoteJob?.cancel()
        val s = _state.value
        val raw = amountToRaw(s.amountInput, s.sell.decimals) ?: run {
            _state.value = s.copy(quote = QuoteStatus.Error("Invalid amount."))
            return
        }
        val taker = wallet?.address(chain)?.lowercase() ?: run {
            _state.value = s.copy(quote = QuoteStatus.Error("No wallet."))
            return
        }
        _state.value = s.copy(quote = QuoteStatus.Loading)
        quoteJob = viewModelScope.launch {
            val result = runCatching {
                zeroEx.fetchQuote(
                    chain = chain,
                    sell = s.sell,
                    buy = s.buy,
                    sellAmountRaw = raw,
                    taker = taker,
                    slippageBps = 100,
                )
            }
            _state.value = _state.value.copy(
                quote = result.fold(
                    onSuccess = { QuoteStatus.Value(it) },
                    onFailure = { QuoteStatus.Error("Quote unavailable.") },
                )
            )
        }
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _state.value = _state.value.copy(searchResults = SearchStatus.Idle)
            return
        }
        _state.value = _state.value.copy(searchResults = SearchStatus.Loading)
        searchJob = viewModelScope.launch {
            val result = runCatching { search.searchTokens(trimmed, chain) }
            _state.value = _state.value.copy(
                searchResults = result.fold(
                    onSuccess = { SearchStatus.Value(it) },
                    onFailure = { SearchStatus.Error("Search failed.") },
                )
            )
        }
    }

    // The wallet is provided by the Screen via attach() so the VM can be
    // constructed by Koin without a Wallet dependency (wallets are
    // session-scoped, not app-scoped).
    private var wallet: Wallet? = null
    fun attach(wallet: Wallet) {
        this.wallet = wallet
    }

    private data class QuoteKey(val sell: TokenRef, val buy: TokenRef, val amountInput: String)
}

class SwapViewModelFactory(
    private val zeroEx: ZeroExClient,
    private val search: TokenSearchClient,
    private val assemblerFactory: SwapAssemblerFactory,
) {
    fun create(chain: SupportedChain): SwapViewModel =
        SwapViewModel(
            chain = chain,
            zeroEx = zeroEx,
            search = search,
            assemblerFactory = assemblerFactory,
        )
}

/**
 * Convert a human decimal like `"1.25"` into raw base-units using the
 * sell token's decimals. Returns null on invalid input.
 */
internal fun amountToRaw(input: String, decimals: Int): String? {
    if (input.isBlank()) return null
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
    if (raw.isEmpty() || raw == "0") return "0"
    val padded = if (raw.length <= decimals) "0".repeat(decimals - raw.length + 1) + raw else raw
    val intPart = padded.dropLast(decimals).ifEmpty { "0" }
    val fracPart = padded.takeLast(decimals).take(maxFrac).trimEnd('0')
    return if (fracPart.isEmpty()) intPart else "$intPart.$fracPart"
}

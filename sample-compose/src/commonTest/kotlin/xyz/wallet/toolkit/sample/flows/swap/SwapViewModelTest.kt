package xyz.wallet.toolkit.sample.flows.swap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import xyz.wallet.toolkit.core.SupportedChain

@OptIn(ExperimentalCoroutinesApi::class)
class SwapViewModelTest {
    @Test
    fun amountEditImmediatelyRevokesAnAcceptedQuote() = runTest {
        val source = DeferredQuotes()
        val vm = SwapViewModel(chain, source, noSearch, backgroundScope, owner)
        vm.onAmountChange("1")
        advanceTimeBy(400)
        runCurrent()
        source.requests.single().complete(quote("1000000000000000000"))
        runCurrent()
        assertIs<QuoteStatus.Value>(vm.state.value.quote)
        vm.onAmountChange("0.1")
        assertNull(vm.captureQuote())
        assertIs<QuoteStatus.Idle>(vm.state.value.quote)
        vm.dispose()
    }

    @Test
    fun clearingInputCannotBeRepopulatedByAnOldResponse() = runTest {
        val source = DeferredQuotes()
        val vm = SwapViewModel(chain, source, noSearch, backgroundScope, owner)
        vm.onAmountChange("1")
        advanceTimeBy(400)
        runCurrent()
        vm.onAmountChange("")
        source.requests.single().complete(quote("1000000000000000000"))
        runCurrent()
        assertNull(vm.captureQuote())
        assertIs<QuoteStatus.Idle>(vm.state.value.quote)
        vm.dispose()
    }

    @Test
    fun returningToTheSameAmountDoesNotReauthorizeItsFirstRequest() = runTest {
        val source = DeferredQuotes()
        val vm = SwapViewModel(chain, source, noSearch, backgroundScope, owner)
        vm.onAmountChange("1")
        advanceTimeBy(400)
        runCurrent()
        vm.onAmountChange("2")
        vm.onAmountChange("1")
        advanceTimeBy(400)
        runCurrent()
        source.requests[0].complete(quote("1000000000000000000"))
        runCurrent()
        assertNull(vm.captureQuote())
        source.requests[1].complete(quote("1000000000000000000"))
        runCurrent()
        assertIs<QuoteStatus.Value>(vm.state.value.quote)
        vm.dispose()
    }

    @Test
    fun confirmationConsumesTheAcceptedQuoteExactlyOnce() = runTest {
        val source = DeferredQuotes()
        val vm = SwapViewModel(chain, source, noSearch, backgroundScope, owner)
        vm.onAmountChange("1")
        advanceTimeBy(400)
        runCurrent()
        source.requests.single().complete(quote("1000000000000000000"))
        runCurrent()
        val selection = vm.captureQuote()!!
        assertTrue(vm.consumeQuote(selection))
        assertFalse(vm.consumeQuote(selection))
        assertNull(vm.captureQuote())
        vm.dispose()
    }

    private class DeferredQuotes : SwapQuoteSource {
        val requests = mutableListOf<CompletableDeferred<SwapQuote>>()
        override suspend fun fetchQuote(chain: SupportedChain, sell: TokenRef, buy: TokenRef, sellAmountRaw: String, taker: String, slippageBps: Int): SwapQuote {
            val response = CompletableDeferred<SwapQuote>().also(requests::add)
            return withContext(NonCancellable) { response.await() }
        }
    }

    companion object {
        private val chain = SupportedChain.Ethereum
        private const val owner = "0x1111111111111111111111111111111111111111"
        private val noSearch = object : TokenSearchSource {
            override suspend fun searchTokens(query: String, chain: SupportedChain): List<TokenRef> = emptyList()
        }
        private fun quote(amount: String) = SwapQuote(
            DefaultTokens.nativeFor(chain), DefaultTokens.defaultBuyFor(chain), amount, "200", "190",
            QuoteTransaction("0x3333333333333333333333333333333333333333", "0x", amount, "21000", "1000000000"),
            null, null,
        )
    }
}

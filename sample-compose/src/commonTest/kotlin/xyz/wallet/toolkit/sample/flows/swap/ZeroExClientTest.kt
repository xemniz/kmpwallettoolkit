package xyz.wallet.toolkit.sample.flows.swap

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import xyz.wallet.toolkit.core.SupportedChain

class ZeroExClientTest {
    @Test
    fun missingApprovalTargetDoesNotProduceAnExecutableQuote() = runTest {
        assertFails { fetch(target = null) }
    }

    @Test
    fun explicitNullRequiresNoApprovalEvenForWrappedTokenSell() = runTest {
        val quote = fetch(target = "null", wrappedSell = true)
        assertNull(quote.allowanceTarget)
        assertNull(quote.allowanceIssue)
    }

    @Test
    fun sufficientAllowanceKeepsItsAuthoritativeTargetWhenNoIssueIsReported() = runTest {
        val quote = fetch(target = "\"$spender\"")
        assertEquals(spender, quote.allowanceTarget)
        assertEquals(router, quote.transaction.to)
        assertNull(quote.allowanceIssue)
    }

    @Test
    fun spenderChecksumCasingDoesNotChangeItsIdentity() = runTest {
        val quote = fetch(target = "\"$spender\"", issue = spender.uppercase().replace("0X", "0x"))
        assertEquals(spender, quote.allowanceIssue?.spender)
    }

    @Test
    fun conflictingOrAbsentTargetsCannotAuthorizeAnAllowanceIssue() = runTest {
        assertFails { fetch(target = "null", issue = spender) }
        assertFails { fetch(target = "\"$spender\"", issue = router) }
    }

    @Test
    fun malformedAndZeroApprovalTargetsAreRejected() = runTest {
        for (target in listOf("0x", "0x-1", "0x" + "0".repeat(40), "0x" + "g".repeat(40))) {
            assertFails { fetch(target = "\"$target\"") }
        }
    }

    private suspend fun fetch(target: String?, issue: String? = null, wrappedSell: Boolean = false): SwapQuote {
        val targetField = target?.let { "\"allowanceTarget\":$it," } ?: ""
        val issueField = if (issue == null) "null" else "{\"spender\":\"$issue\",\"actual\":\"0\"}"
        val http = HttpClient(MockEngine {
            respond(
                """{$targetField"issues":{"allowance":$issueField},"sellAmount":"100","buyAmount":"200","minBuyAmount":"190","transaction":{"to":"$router","data":"0x","value":"100","gas":"21000","gasPrice":"1000000000"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val chain = SupportedChain.Ethereum
            val sell = if (wrappedSell) DefaultTokens.forChain(chain).first { it.symbol == "WETH" } else DefaultTokens.nativeFor(chain)
            return ZeroExClient(http, "test-key").fetchQuote(chain, sell, DefaultTokens.defaultBuyFor(chain), "100", "0x1111111111111111111111111111111111111111")
        } finally {
            http.close()
        }
    }

    companion object {
        private const val spender = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
        private const val router = "0x3333333333333333333333333333333333333333"
    }
}

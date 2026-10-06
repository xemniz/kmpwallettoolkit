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
import kotlin.test.assertFails
import kotlin.test.assertNull
import xyz.wallet.toolkit.core.SupportedChain

class ZeroExClientTest {
    @Test
    fun missingApprovalTargetDoesNotProduceAnExecutableQuote() = runTest {
        val http = HttpClient(MockEngine {
            respond(
                """{"sellAmount":"100","buyAmount":"200","minBuyAmount":"190","transaction":{"to":"0x3333333333333333333333333333333333333333","data":"0x","value":"100","gas":"21000","gasPrice":"1000000000"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val chain = SupportedChain.Ethereum
            val client = ZeroExClient(http, "test-key")
            assertFails {
                client.fetchQuote(chain, DefaultTokens.nativeFor(chain), DefaultTokens.defaultBuyFor(chain), "100", "0x1111111111111111111111111111111111111111")
            }
        } finally {
            http.close()
        }
    }
}

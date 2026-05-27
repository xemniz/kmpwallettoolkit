package xyz.wallet.toolkit.sample.flows.swap

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.secrets.Secrets

/**
 * Minimal 0x Swap client — the AllowanceHolder variant. Returns a signed-
 * able EVM transaction the caller can hand to `wallet.signEip1559Transaction`.
 *
 * Secrecy:
 *  - The API key lives on the `0x-api-key` header. It is never logged and
 *    `toString()` returns a redacted form.
 *  - Responses contain the user's wallet in `taker`-shaped fields —
 *    callers must not interpolate exception messages with raw responses.
 */
class ZeroExClient(
    private val http: HttpClient = defaultHttp(),
    private val apiKey: String = Secrets.ZEROX_API_KEY,
) {

    init {
        check(apiKey.isNotBlank() && apiKey != "REPLACE_ME") {
            "0x API key is not configured. Paste it into Secrets.kt."
        }
    }

    suspend fun fetchQuote(
        chain: SupportedChain,
        sell: TokenRef,
        buy: TokenRef,
        sellAmountRaw: String,
        taker: String,
        slippageBps: Int = 100,
    ): SwapQuote {
        val response: QuoteResponse = http
            .get("https://api.0x.org/swap/allowance-holder/quote") {
                header("0x-api-key", apiKey)
                header("0x-version", "v2")
                parameter("chainId", chain.id.toString())
                parameter("sellToken", sell.addressForZeroEx())
                parameter("buyToken", buy.addressForZeroEx())
                parameter("sellAmount", sellAmountRaw)
                parameter("taker", taker.lowercase())
                parameter("slippageBps", slippageBps.toString())
            }
            .body()
        return response.toDomain(sell, buy)
    }

    override fun toString(): String = "ZeroExClient"

    companion object {
        private fun defaultHttp(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
}

@Serializable
private data class QuoteResponse(
    val buyAmount: String? = null,
    val sellAmount: String? = null,
    val minBuyAmount: String? = null,
    val transaction: TxResponse? = null,
    val issues: IssuesResponse? = null,
)

@Serializable
private data class TxResponse(
    val to: String? = null,
    val data: String? = null,
    val value: String? = null,
    val gas: String? = null,
)

@Serializable
private data class IssuesResponse(val allowance: AllowanceResponse? = null)

@Serializable
private data class AllowanceResponse(val actual: String? = null, val spender: String? = null)

private fun QuoteResponse.toDomain(sell: TokenRef, buy: TokenRef): SwapQuote {
    val tx = transaction ?: error("0x response missing transaction")
    return SwapQuote(
        sell = sell,
        buy = buy,
        sellAmountRaw = sellAmount ?: error("0x response missing sellAmount"),
        buyAmountRaw = buyAmount ?: error("0x response missing buyAmount"),
        minBuyAmountRaw = minBuyAmount ?: buyAmount ?: "0",
        transaction = QuoteTransaction(
            to = tx.to ?: error("0x response missing tx.to"),
            dataHex = tx.data ?: error("0x response missing tx.data"),
            valueWei = tx.value ?: "0",
            gasLimit = tx.gas ?: "250000",
        ),
        allowanceIssue = issues?.allowance?.spender?.let { AllowanceIssue(spender = it) },
    )
}

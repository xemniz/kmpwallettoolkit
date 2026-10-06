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

interface SwapQuoteSource {
    suspend fun fetchQuote(
        chain: SupportedChain,
        sell: TokenRef,
        buy: TokenRef,
        sellAmountRaw: String,
        taker: String,
        slippageBps: Int = 100,
    ): SwapQuote
}

class ZeroExClient(
    private val http: HttpClient = defaultHttp(),
    private val apiKey: String = Secrets.ZEROX_API_KEY,
) : SwapQuoteSource {

    init {
        check(apiKey.isNotBlank() && apiKey != "REPLACE_ME") {
            "0x API key is not configured. Paste it into Secrets.kt."
        }
    }

    override suspend fun fetchQuote(
        chain: SupportedChain,
        sell: TokenRef,
        buy: TokenRef,
        sellAmountRaw: String,
        taker: String,
        slippageBps: Int,
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
                })
            }
        }
    }
}

@Serializable
private data class QuoteResponse(
    val allowanceTarget: String?,
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
    val gasPrice: String? = null,
)

@Serializable
private data class IssuesResponse(val allowance: AllowanceResponse? = null)

@Serializable
private data class AllowanceResponse(val actual: String? = null, val spender: String? = null)

private fun QuoteResponse.toDomain(sell: TokenRef, buy: TokenRef): SwapQuote {
    val tx = transaction ?: error("0x response missing transaction")
    val target = allowanceTarget?.let(::validatedAddress)
    val issue = issues?.allowance?.let {
        val spender = validatedAddress(it.spender ?: error("Missing approval spender"))
        require(target == spender) { "Inconsistent approval target" }
        AllowanceIssue(spender)
    }
    return SwapQuote(
        sell = sell,
        buy = buy,
        sellAmountRaw = sellAmount ?: error("0x response missing sellAmount"),
        buyAmountRaw = buyAmount ?: error("0x response missing buyAmount"),
        minBuyAmountRaw = minBuyAmount ?: error("0x response missing minBuyAmount"),
        transaction = QuoteTransaction(
            to = validatedAddress(tx.to ?: error("0x response missing tx.to")),
            dataHex = tx.data ?: error("0x response missing tx.data"),
            valueWei = tx.value ?: error("0x response missing tx.value"),
            gasLimit = tx.gas ?: error("0x response missing tx.gas"),
            gasPriceWei = tx.gasPrice ?: error("0x response missing tx.gasPrice"),
        ),
        allowanceIssue = issue,
        allowanceTarget = target,
    )
}

internal fun validatedAddress(raw: String): String {
    val normalized = raw.lowercase()
    require(normalized.length == 42 && normalized.startsWith("0x") &&
        normalized.substring(2).all { it in '0'..'9' || it in 'a'..'f' } &&
        normalized.substring(2).any { it != '0' }) { "Invalid address" }
    return normalized
}

package xyz.wallet.toolkit.sample.flows.swap

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.secrets.Secrets

/**
 * Token autocomplete via Zerion's `/v1/fungibles` search. Scoped to the
 * chain the user is currently on — we only project implementations that
 * match the active `chain_id`.
 *
 * No auth header is ever logged. Results are untrusted: we do not echo
 * their content into exception messages or logs.
 */
interface TokenSearchSource {
    suspend fun searchTokens(query: String, chain: SupportedChain): List<TokenRef>
}

class TokenSearchClient(
    private val http: HttpClient = defaultHttp(),
    private val apiKey: String = Secrets.ZERION_API_KEY,
) : TokenSearchSource {

    init {
        check(apiKey.isNotBlank() && apiKey != "REPLACE_ME") {
            "Zerion API key is not configured."
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private val authHeader: String =
        "Basic " + Base64.encode("$apiKey:".encodeToByteArray())

    override suspend fun searchTokens(query: String, chain: SupportedChain): List<TokenRef> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val chainId = chain.toZerionChainId() ?: return emptyList()
        val response: FungiblesResponse = http
            .get("https://api.zerion.io/v1/fungibles/") {
                header(HttpHeaders.Authorization, authHeader)
                header(HttpHeaders.Accept, "application/json")
                parameter("filter[search_query]", trimmed)
                parameter("filter[implementation_chain_id]", chainId)
                parameter("page[size]", "20")
            }
            .body()
        return response.toTokenRefs(chain, chainId)
    }

    override fun toString(): String = "TokenSearchClient"

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

private fun SupportedChain.toZerionChainId(): String? = when (this) {
    SupportedChain.Ethereum -> "ethereum"
    SupportedChain.Base -> "base"
    else -> null
}

@Serializable
private data class FungiblesResponse(val data: List<FungibleEntry> = emptyList())

@Serializable
private data class FungibleEntry(val attributes: FungibleAttrs? = null)

@Serializable
private data class FungibleAttrs(
    val name: String? = null,
    val symbol: String? = null,
    val icon: Icon? = null,
    val implementations: List<Impl> = emptyList(),
)

@Serializable
private data class Icon(val url: String? = null)

@Serializable
private data class Impl(
    val chain_id: String? = null,
    val address: String? = null,
    val decimals: Int? = null,
)

private fun FungiblesResponse.toTokenRefs(chain: SupportedChain, chainId: String): List<TokenRef> =
    data.mapNotNull { entry ->
        val a = entry.attributes ?: return@mapNotNull null
        val symbol = a.symbol ?: return@mapNotNull null
        val impl = a.implementations.firstOrNull { it.chain_id == chainId } ?: return@mapNotNull null
        val decimals = impl.decimals ?: return@mapNotNull null
        TokenRef(
            symbol = symbol,
            name = a.name ?: symbol,
            address = impl.address?.lowercase(),
            decimals = decimals,
            chain = chain,
            iconUrl = a.icon?.url,
        )
    }

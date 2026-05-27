package xyz.wallet.toolkit.sample.portfolio

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.secrets.Secrets

/**
 * Minimal Zerion REST client. Only `listPositions` is wired — the showcase
 * does not need NFTs, activity, or portfolio endpoints.
 *
 * Auth: HTTP Basic with the API key as the username and an empty password.
 * The `Authorization` header is never logged.
 */
class ZerionClient(
    private val http: HttpClient = defaultHttp(),
    private val apiKey: String = Secrets.ZERION_API_KEY,
) {

    init {
        check(apiKey.isNotBlank() && apiKey != "REPLACE_ME") {
            "Zerion API key is not configured. Copy Secrets.kt.template to Secrets.kt and paste your key."
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private val authHeader: String =
        "Basic " + Base64.encode("$apiKey:".encodeToByteArray())

    // Zerion's free tier throttles parallel requests from the same key; serialize
    // them here so concurrent chain fetches don't race each other into a 429.
    private val requestMutex = Mutex()

    suspend fun fetchPortfolio(address: String, chain: SupportedChain): PortfolioSnapshot {
        val chainId = chain.toZerionChainId()
        val response: PositionsResponse = requestMutex.withLock {
            http.get("https://api.zerion.io/v1/wallets/${address.lowercase()}/positions/") {
                header(HttpHeaders.Authorization, authHeader)
                header(HttpHeaders.Accept, "application/json")
                parameter("filter[chain_ids]", chainId)
                parameter("filter[positions]", "only_simple")
                parameter("currency", "usd")
                parameter("sort", "-value")
                parameter("sync", "true")
            }.body()
        }
        return response.toSnapshot(chainId)
    }

    override fun toString(): String = "ZerionClient"

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

private fun SupportedChain.toZerionChainId(): String = when (this) {
    SupportedChain.Ethereum -> "ethereum"
    SupportedChain.Base -> "base"
    else -> error("Zerion chain id not mapped for $this")
}

// --- JSON:API envelope (subset the showcase uses) ---

@Serializable
private data class PositionsResponse(val data: List<PositionEntry> = emptyList())

@Serializable
private data class PositionEntry(
    val id: String? = null,
    val attributes: PositionAttributes? = null,
)

@Serializable
private data class PositionAttributes(
    val name: String? = null,
    val quantity: PositionQuantity? = null,
    val value: Double? = null,
    val changes: PositionChanges? = null,
    val fungible_info: FungibleInfo? = null,
    val flags: PositionFlags? = null,
)

@Serializable
private data class PositionQuantity(
    val numeric: String? = null,
    val decimals: Int? = null,
    val int: String? = null,
    val float: Double? = null,
)

@Serializable
private data class PositionChanges(
    val absolute_1d: Double? = null,
    val percent_1d: Double? = null,
)

@Serializable
private data class FungibleInfo(
    val name: String? = null,
    val symbol: String? = null,
    val icon: FungibleIcon? = null,
    val implementations: List<FungibleImplementation> = emptyList(),
)

@Serializable
private data class FungibleIcon(val url: String? = null)

@Serializable
private data class FungibleImplementation(
    val chain_id: String? = null,
    val address: String? = null,
    val decimals: Int? = null,
)

@Serializable
private data class PositionFlags(val displayable: Boolean? = null)

private fun PositionsResponse.toSnapshot(chainId: String): PortfolioSnapshot {
    val tokens = data
        .filter { it.attributes?.flags?.displayable != false }
        .mapNotNull { entry ->
            val a = entry.attributes ?: return@mapNotNull null
            val info = a.fungible_info ?: return@mapNotNull null
            val impl = info.implementations.firstOrNull { it.chain_id == chainId }
            val isNative = impl != null && impl.address == null
            val symbol = info.symbol ?: return@mapNotNull null
            TokenPosition(
                symbol = symbol,
                name = info.name ?: symbol,
                quantityDecimal = a.quantity?.numeric ?: "0",
                valueUsd = a.value,
                iconUrl = info.icon?.url,
                isNative = isNative,
            )
        }
        .sortedWith(compareByDescending<TokenPosition> { it.isNative }.thenByDescending { it.valueUsd ?: 0.0 })

    val totalUsd = tokens.sumOf { it.valueUsd ?: 0.0 }
    val change24h = data
        .mapNotNull { it.attributes?.changes?.absolute_1d }
        .takeIf { it.isNotEmpty() }
        ?.sum()
    val previousTotal = totalUsd - (change24h ?: 0.0)
    val percent = if (change24h != null && previousTotal > 0.0) change24h / previousTotal * 100.0 else null

    return PortfolioSnapshot(
        tokens = tokens,
        totalUsd = totalUsd,
        change24h = if (change24h != null && percent != null) PortfolioChange24h(change24h, percent) else null,
    )
}

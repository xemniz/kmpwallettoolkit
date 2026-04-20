package xyz.wallet.toolkit.rpc

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

class RpcClient(
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val json: Json = Json,
) {
    // Lenient decoder used for receipts. Receipts are the most forward-compat-
    // sensitive surface (EIP-1559 effectiveGasPrice, EIP-2718 type, EIP-4844
    // blobGasUsed, etc.), so unknown fields must not break decoding.
    private val lenientJson: Json =
        if (json.configuration.ignoreUnknownKeys) json
        else Json(from = json) { ignoreUnknownKeys = true }

    companion object {
        fun withDefaults(baseUrl: String, json: Json = Json): RpcClient {
            val httpClient = HttpClient {
                install(ContentNegotiation) {
                    json(json)
                }
            }
            return RpcClient(baseUrl = baseUrl, httpClient = httpClient, json = json)
        }
    }

    suspend fun getBalance(address: String, blockTag: String = "latest"): String {
        return call("eth_getBalance", JsonPrimitive(address), JsonPrimitive(blockTag))
    }

    suspend fun getNonce(address: String, blockTag: String = "latest"): String {
        return call("eth_getTransactionCount", JsonPrimitive(address), JsonPrimitive(blockTag))
    }

    suspend fun estimateGas(call: RpcCall): String {
        return call("eth_estimateGas", json.encodeToJsonElement(call))
    }

    suspend fun sendRawTransaction(rawTransaction: String): String {
        return call("eth_sendRawTransaction", JsonPrimitive(rawTransaction))
    }

    suspend fun getTransactionReceipt(txHash: String): TransactionReceipt? {
        val raw = callRaw("eth_getTransactionReceipt", JsonPrimitive(txHash))
            ?: return null
        if (raw is JsonNull) return null
        return lenientJson.decodeFromJsonElement(TransactionReceipt.serializer(), raw)
    }

    private suspend fun call(method: String, vararg params: JsonElement): String {
        val result = callRaw(method, *params)
            ?: throw RpcException(code = -1, message = "Missing RPC result")
        return result.jsonPrimitive.content
    }

    private suspend fun callRaw(method: String, vararg params: JsonElement): JsonElement? {
        val request = JsonRpcRequest(method = method, params = params.toList())
        val response: JsonRpcResponse<JsonElement> = httpClient.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        response.error?.let { err ->
            throw RpcException(code = err.code, message = err.message)
        }

        return response.result
    }
}

class RpcException(
    val code: Int,
    override val message: String,
) : IllegalStateException("RPC error($code): $message")



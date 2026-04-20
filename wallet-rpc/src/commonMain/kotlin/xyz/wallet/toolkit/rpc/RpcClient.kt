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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

class RpcClient(
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val json: Json = Json,
) {
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

    private suspend fun call(method: String, vararg params: JsonElement): String {
        val request = JsonRpcRequest(method = method, params = params.toList())
        val response: JsonRpcResponse<JsonElement> = httpClient.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        response.error?.let { err ->
            throw RpcException(code = err.code, message = err.message)
        }

        val result = response.result ?: throw RpcException(code = -1, message = "Missing RPC result")
        return result.jsonPrimitive.content
    }
}

class RpcException(
    val code: Int,
    override val message: String,
) : IllegalStateException("RPC error($code): $message")



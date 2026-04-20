package xyz.wallet.toolkit.rpc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: List<JsonElement>,
    val id: Long = 1,
)

@Serializable
internal data class JsonRpcResponse<T>(
    val jsonrpc: String,
    val id: Long,
    val result: T? = null,
    val error: JsonRpcError? = null,
)

@Serializable
internal data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable
data class RpcCall(
    val from: String? = null,
    val to: String,
    val gas: String? = null,
    @SerialName("gasPrice") val gasPrice: String? = null,
    val value: String? = null,
    val data: String? = null,
)


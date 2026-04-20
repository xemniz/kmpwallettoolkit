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

/**
 * Decoded JSON-RPC `eth_getTransactionReceipt` result. Decoding is deliberately
 * lenient (`ignoreUnknownKeys = true` on the receipt decode path) because
 * receipts are the most forward-compatibility-sensitive surface — real nodes
 * add fields over time (EIP-1559 `effectiveGasPrice`, EIP-2718 `type`,
 * EIP-4844 `blobGasUsed`) and we must not break decoding when they show up.
 */
@Serializable
data class TransactionReceipt(
    val transactionHash: String,
    val transactionIndex: String,
    val blockHash: String,
    val blockNumber: String,
    val from: String,
    val to: String? = null,
    val contractAddress: String? = null,
    val gasUsed: String,
    val cumulativeGasUsed: String,
    val status: String,
    val logsBloom: String,
    val logs: List<JsonElement> = emptyList(),
)


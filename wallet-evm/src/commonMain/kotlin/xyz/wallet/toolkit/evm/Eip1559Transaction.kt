package xyz.wallet.toolkit.evm

import kotlinx.serialization.Serializable

@Serializable
data class AccessListEntry(
    val address: String,
    val storageKeys: List<String>,
)

@Serializable
data class Eip1559Transaction(
    val chainId: Long,
    val to: String,
    val valueWei: String = "0",
    val maxFeePerGasWei: String,
    val maxPriorityFeePerGasWei: String,
    val gasLimit: String,
    val nonce: Long,
    val dataHex: String? = null,
    val accessList: List<AccessListEntry> = emptyList(),
)

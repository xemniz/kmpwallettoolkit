package xyz.wallet.toolkit.evm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.wallet.toolkit.utils.hexToByteArray
import xyz.wallet.toolkit.utils.requireUnsignedDecimal

/** Field order and defaults are part of the native signing contract. */
fun EvmTransaction.toSigningPayload(json: Json = Json): ByteArray {
    requireValidQuantities()
    dataHex?.hexToByteArray()
    return json.encodeToString(this).encodeToByteArray()
}

internal fun EvmTransaction.requireValidQuantities() {
    require(chainId >= 0 && nonce >= 0) { "Chain ID and nonce must not be negative" }
    valueWei.requireUnsignedDecimal()
    gasPriceWei.requireUnsignedDecimal()
    gasLimit.requireUnsignedDecimal()
}

private val Eip1559Json = Json { encodeDefaults = true }

fun Eip1559Transaction.toSigningPayload(): ByteArray {
    require(chainId >= 0 && nonce >= 0) { "Chain ID and nonce must not be negative" }
    valueWei.requireUnsignedDecimal()
    maxFeePerGasWei.requireUnsignedDecimal()
    maxPriorityFeePerGasWei.requireUnsignedDecimal()
    gasLimit.requireUnsignedDecimal()
    dataHex?.hexToByteArray()
    accessList.forEach { entry -> entry.storageKeys.forEach { it.hexToByteArray() } }
    return Eip1559Json.encodeToString(this).encodeToByteArray()
}

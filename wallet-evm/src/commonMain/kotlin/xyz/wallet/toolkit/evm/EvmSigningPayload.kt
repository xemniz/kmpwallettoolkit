package xyz.wallet.toolkit.evm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Canonical JSON payload passed to native signer adapters.
 */
fun EvmTransaction.toSigningPayload(json: Json = Json): ByteArray {
    return json.encodeToString(this).encodeToByteArray()
}

private val Eip1559Json = Json { encodeDefaults = true }

fun Eip1559Transaction.toSigningPayload(): ByteArray {
    return Eip1559Json.encodeToString(this).encodeToByteArray()
}


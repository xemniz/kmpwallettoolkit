package xyz.wallet.toolkit.core

import com.google.protobuf.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wallet.core.java.AnySigner
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet
import wallet.core.jni.proto.Ethereum
import xyz.wallet.toolkit.utils.hexToByteArray
import java.math.BigInteger

actual object TrustWalletCoreNativeBridge {

    init {
        System.loadLibrary("TrustWalletCore")
    }

    actual fun createMnemonic(): String {
        val wallet = HDWallet(128, "")
        return wallet.mnemonic()
    }

    actual fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        val wallet = HDWallet(mnemonic, "")
        return wallet.getAddressForCoin(chain.toCoinType())
    }

    actual fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray {
        require(transaction is EvmTransactionData) {
            "Only EvmTransactionData transactions are currently supported for signing. " +
                "Received: ${transaction::class.simpleName}"
        }
        return signEvmTransaction(mnemonic, chain, transaction)
    }

    actual fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray {
        val json = Json.parseToJsonElement(signingPayloadJson.decodeToString()).jsonObject

        val chainId = json.requireLong("chainId")
        val to = json.requireString("to")
        val valueWei = json["valueWei"]?.jsonPrimitive?.content ?: "0"
        val maxFeePerGasWei = json.requireString("maxFeePerGasWei")
        val maxPriorityFeePerGasWei = json.requireString("maxPriorityFeePerGasWei")
        val gasLimit = json.requireString("gasLimit")
        val nonce = json.requireLong("nonce")
        val dataHex = json["dataHex"]?.jsonPrimitive?.let { if (it.content == "null") null else it.content }
            ?.takeIf { it.isNotEmpty() }
        val accessList = json["accessList"]?.jsonArray ?: JsonArray(emptyList())

        // Payload-declared chainId must match the routing chain. The outer
        // Wallet.signEip1559Transaction extension also guards this, but the
        // bridge stays honest about its invariants.
        require(chainId == chain.id) {
            "Payload chainId $chainId does not match routing chain ${chain.displayName} (${chain.id})"
        }

        val coinType = chain.toCoinType()
        val wallet = HDWallet(mnemonic, "")
        val privateKey = wallet.getKeyForCoin(coinType)

        val transferBuilder = Ethereum.Transaction.Transfer.newBuilder()
            .setAmount(valueWei.decimalToByteString())
        if (dataHex != null) {
            transferBuilder.setData(ByteString.copyFrom(dataHex.hexToByteArray()))
        }

        val ethTransaction = Ethereum.Transaction.newBuilder()
            .setTransfer(transferBuilder)
            .build()

        val input = Ethereum.SigningInput.newBuilder()
            .setTxMode(Ethereum.TransactionMode.Enveloped)
            .setChainId(chainId.toByteString())
            .setNonce(nonce.toByteString())
            .setMaxFeePerGas(maxFeePerGasWei.decimalToByteString())
            .setMaxInclusionFeePerGas(maxPriorityFeePerGasWei.decimalToByteString())
            .setGasLimit(gasLimit.decimalToByteString())
            .setToAddress(to)
            .setPrivateKey(ByteString.copyFrom(privateKey.data()))
            .setTransaction(ethTransaction)

        accessList.forEach { entry ->
            val obj = entry.jsonObject
            val address = obj.requireString("address")
            val storageKeys = obj["storageKeys"]?.jsonArray ?: JsonArray(emptyList())
            val accessBuilder = Ethereum.Access.newBuilder()
                .setAddress(address)
            storageKeys.forEach { key ->
                accessBuilder.addStoredKeys(ByteString.copyFrom(key.jsonPrimitive.content.hexToByteArray()))
            }
            input.addAccessList(accessBuilder)
        }

        val output: Ethereum.SigningOutput =
            AnySigner.sign(input.build(), coinType, Ethereum.SigningOutput.parser())

        check(output.error.number == 0) {
            "Signing failed: ${output.error} – ${output.errorMessage}"
        }

        return output.encoded.toByteArray()
    }

    private fun JsonObject.requireString(key: String): String =
        this[key]?.jsonPrimitive?.content
            ?: error("Missing or non-primitive field '$key' in EIP-1559 signing payload")

    private fun JsonObject.requireLong(key: String): Long =
        this[key]?.jsonPrimitive?.content?.toLong()
            ?: error("Missing or non-primitive field '$key' in EIP-1559 signing payload")

    // ── EVM signing via protobuf ────────────────────────────────────────

    private fun signEvmTransaction(
        mnemonic: String,
        chain: SupportedChain,
        tx: EvmTransactionData,
    ): ByteArray {
        val wallet = HDWallet(mnemonic, "")
        val coinType = chain.toCoinType()
        val privateKey = wallet.getKeyForCoin(coinType)

        val transferBuilder = Ethereum.Transaction.Transfer.newBuilder()
            .setAmount(tx.valueWei.decimalToByteString())
        val hex = tx.dataHex
        if (!hex.isNullOrEmpty()) {
            transferBuilder.setData(ByteString.copyFrom(hex.hexToByteArray()))
        }

        val ethTransaction = Ethereum.Transaction.newBuilder()
            .setTransfer(transferBuilder)
            .build()

        val input = Ethereum.SigningInput.newBuilder()
            .setChainId(tx.chainId.toByteString())
            .setNonce(tx.nonce.toByteString())
            .setGasPrice(tx.gasPriceWei.decimalToByteString())
            .setGasLimit(tx.gasLimit.decimalToByteString())
            .setToAddress(tx.to)
            .setPrivateKey(ByteString.copyFrom(privateKey.data()))
            .setTransaction(ethTransaction)
            .build()

        val output: Ethereum.SigningOutput = AnySigner.sign(input, coinType, Ethereum.SigningOutput.parser())

        check(output.error.number == 0) {
            "Signing failed: ${output.error} – ${output.errorMessage}"
        }

        return output.encoded.toByteArray()
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun SupportedChain.toCoinType(): CoinType = when (this) {
        SupportedChain.Ethereum -> CoinType.ETHEREUM
        SupportedChain.Base -> CoinType.ETHEREUM
        SupportedChain.Arbitrum -> CoinType.ETHEREUM
        SupportedChain.Polygon -> CoinType.POLYGON
        SupportedChain.Optimism -> CoinType.ETHEREUM
        SupportedChain.BnbSmartChain -> CoinType.SMARTCHAIN
    }

    /** Convert a [Long] to a big-endian unsigned [ByteString]. */
    private fun Long.toByteString(): ByteString {
        if (this == 0L) return ByteString.copyFrom(byteArrayOf(0))
        return BigInteger.valueOf(this).toUnsignedByteString()
    }

    /** Convert a decimal number string (e.g. "1000000000") to a big-endian unsigned [ByteString]. */
    private fun String.decimalToByteString(): ByteString {
        val bi = BigInteger(this)
        if (bi == BigInteger.ZERO) return ByteString.copyFrom(byteArrayOf(0))
        return bi.toUnsignedByteString()
    }

    private fun BigInteger.toUnsignedByteString(): ByteString {
        val bytes = toByteArray()
        // BigInteger.toByteArray() prepends 0x00 for positive values to indicate sign; strip it.
        val trimmed = if (bytes.size > 1 && bytes[0] == 0.toByte()) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }
        return ByteString.copyFrom(trimmed)
    }
}

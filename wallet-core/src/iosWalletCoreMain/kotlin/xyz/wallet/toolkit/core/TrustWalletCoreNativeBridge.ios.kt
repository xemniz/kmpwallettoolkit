@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package xyz.wallet.toolkit.core

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wallet.core.ios.TWAnySignerSign
import wallet.core.ios.TWCoinType
import wallet.core.ios.TWCoinTypeEthereum
import wallet.core.ios.TWCoinTypePolygon
import wallet.core.ios.TWCoinTypeSmartChain
import wallet.core.ios.TWDataBytes
import wallet.core.ios.TWDataCreateWithBytes
import wallet.core.ios.TWDataDelete
import wallet.core.ios.TWDataSize
import wallet.core.ios.TWHDWalletCreate
import wallet.core.ios.TWHDWalletCreateWithMnemonic
import wallet.core.ios.TWHDWalletDelete
import wallet.core.ios.TWHDWalletGetAddressForCoin
import wallet.core.ios.TWHDWalletGetKeyForCoin
import wallet.core.ios.TWHDWalletMnemonic
import wallet.core.ios.TWPrivateKeyData
import wallet.core.ios.TWPrivateKeyDelete
import wallet.core.ios.TWStringCreateWithUTF8Bytes
import wallet.core.ios.TWStringDelete
import wallet.core.ios.TWStringUTF8Bytes
import xyz.wallet.toolkit.utils.hexToByteArray

actual object TrustWalletCoreNativeBridge {
    actual fun createMnemonic(): String {
        return withTwString("") { passphrase ->
            val wallet = TWHDWalletCreate(128, passphrase)
                ?: error("Trust Wallet Core failed to create an HD wallet")
            try {
                TWHDWalletMnemonic(wallet).toKotlinStringAndDelete()
            } finally {
                TWHDWalletDelete(wallet)
            }
        }
    }

    actual fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        return withTwString(mnemonic) { mnemonicString ->
            withTwString("") { passphrase ->
                val wallet = TWHDWalletCreateWithMnemonic(mnemonicString, passphrase)
                    ?: error("Trust Wallet Core rejected the mnemonic")
                try {
                    TWHDWalletGetAddressForCoin(wallet, chain.toCoinType()).toKotlinStringAndDelete()
                } finally {
                    TWHDWalletDelete(wallet)
                }
            }
        }
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

        require(chainId == chain.id) {
            "Payload chainId $chainId does not match routing chain ${chain.displayName} (${chain.id})"
        }

        return withTwString(mnemonic) { mnemonicString ->
            withTwString("") { passphrase ->
                val wallet = TWHDWalletCreateWithMnemonic(mnemonicString, passphrase)
                    ?: error("Trust Wallet Core rejected the mnemonic")
                try {
            val nativePrivateKey = TWHDWalletGetKeyForCoin(wallet, chain.toCoinType())
                ?: error("Trust Wallet Core failed to derive the private key")
            val privateKey = try {
                val data = TWPrivateKeyData(nativePrivateKey)
                    ?: error("Trust Wallet Core returned no private key bytes")
                try {
                    data.toByteArray()
                } finally {
                    TWDataDelete(data)
                }
            } finally {
                TWPrivateKeyDelete(nativePrivateKey)
            }
            val transfer = ProtoWriter().apply {
                bytes(1, valueWei.decimalToUnsignedBytes())
                if (dataHex != null) {
                    bytes(2, dataHex.hexToByteArray())
                }
            }.toByteArray()
            val ethTransaction = ProtoWriter().apply {
                message(1, transfer)
            }.toByteArray()
            val input = ProtoWriter().apply {
                bytes(1, chainId.toUnsignedBytes())
                bytes(2, nonce.toUnsignedBytes())
                int32(3, 1) // Ethereum.TransactionMode.Enveloped
                bytes(5, gasLimit.decimalToUnsignedBytes())
                bytes(6, maxPriorityFeePerGasWei.decimalToUnsignedBytes())
                bytes(7, maxFeePerGasWei.decimalToUnsignedBytes())
                string(8, to)
                bytes(9, privateKey)
                message(10, ethTransaction)
                accessList.forEach { entry ->
                    message(12, entry.jsonObject.toAccessProto())
                }
            }.toByteArray()

            signEthereumInput(input, chain.toCoinType())
                } finally {
                    TWHDWalletDelete(wallet)
                }
            }
        }
    }

    private fun signEvmTransaction(
        mnemonic: String,
        chain: SupportedChain,
        tx: EvmTransactionData,
    ): ByteArray {
        return withTwString(mnemonic) { mnemonicString ->
            withTwString("") { passphrase ->
                val wallet = TWHDWalletCreateWithMnemonic(mnemonicString, passphrase)
                    ?: error("Trust Wallet Core rejected the mnemonic")
                try {
            val nativePrivateKey = TWHDWalletGetKeyForCoin(wallet, chain.toCoinType())
                ?: error("Trust Wallet Core failed to derive the private key")
            val privateKey = try {
                val data = TWPrivateKeyData(nativePrivateKey)
                    ?: error("Trust Wallet Core returned no private key bytes")
                try {
                    data.toByteArray()
                } finally {
                    TWDataDelete(data)
                }
            } finally {
                TWPrivateKeyDelete(nativePrivateKey)
            }
            val transfer = ProtoWriter().apply {
                bytes(1, tx.valueWei.decimalToUnsignedBytes())
                val hex = tx.dataHex
                if (!hex.isNullOrEmpty()) {
                    bytes(2, hex.hexToByteArray())
                }
            }.toByteArray()
            val ethTransaction = ProtoWriter().apply {
                message(1, transfer)
            }.toByteArray()
            val input = ProtoWriter().apply {
                bytes(1, tx.chainId.toUnsignedBytes())
                bytes(2, tx.nonce.toUnsignedBytes())
                bytes(4, tx.gasPriceWei.decimalToUnsignedBytes())
                bytes(5, tx.gasLimit.decimalToUnsignedBytes())
                string(8, tx.to)
                bytes(9, privateKey)
                message(10, ethTransaction)
            }.toByteArray()

            signEthereumInput(input, chain.toCoinType())
                } finally {
                    TWHDWalletDelete(wallet)
                }
            }
        }
    }

    private fun signEthereumInput(input: ByteArray, coinType: TWCoinType): ByteArray {
        val inputData = input.toTwData()
        try {
            val outputData = TWAnySignerSign(inputData, coinType)
                ?: error("Trust Wallet Core signing returned no output")
            try {
                val output = EthereumSigningOutput.parse(outputData.toByteArray())
                check(output.error == 0) {
                    "Signing failed: ${output.errorMessage}"
                }
                return output.encoded
            } finally {
                TWDataDelete(outputData)
            }
        } finally {
            TWDataDelete(inputData)
        }
    }

    private fun JsonObject.toAccessProto(): ByteArray {
        val address = requireString("address")
        val storageKeys = this["storageKeys"]?.jsonArray ?: JsonArray(emptyList())
        return ProtoWriter().apply {
            string(1, address)
            storageKeys.forEach { key ->
                bytes(2, key.jsonPrimitive.content.hexToByteArray())
            }
        }.toByteArray()
    }

    private fun JsonObject.requireString(key: String): String =
        this[key]?.jsonPrimitive?.content
            ?: error("Missing or non-primitive field '$key' in EIP-1559 signing payload")

    private fun JsonObject.requireLong(key: String): Long =
        this[key]?.jsonPrimitive?.content?.toLong()
            ?: error("Missing or non-primitive field '$key' in EIP-1559 signing payload")

    private fun SupportedChain.toCoinType(): TWCoinType = when (this) {
        SupportedChain.Ethereum -> TWCoinTypeEthereum
        SupportedChain.Base -> TWCoinTypeEthereum
        SupportedChain.Arbitrum -> TWCoinTypeEthereum
        SupportedChain.Polygon -> TWCoinTypePolygon
        SupportedChain.Optimism -> TWCoinTypeEthereum
        SupportedChain.BnbSmartChain -> TWCoinTypeSmartChain
    }

    private inline fun <T> withTwString(value: String, block: (COpaquePointer) -> T): T {
        return memScoped {
            val string = TWStringCreateWithUTF8Bytes(value)
                ?: error("Trust Wallet Core failed to allocate a string")
            try {
                block(string)
            } finally {
                TWStringDelete(string)
            }
        }
    }

    private fun COpaquePointer?.toKotlinStringAndDelete(): String {
        val string = this ?: error("Trust Wallet Core returned no string")
        try {
            return TWStringUTF8Bytes(string)?.toKString()
                ?: error("Trust Wallet Core returned an invalid string")
        } finally {
            TWStringDelete(string)
        }
    }

    private fun ByteArray.toTwData(): COpaquePointer {
        return usePinned { pinned ->
            TWDataCreateWithBytes(pinned.addressOf(0).reinterpret(), size.convert())
                ?: error("Trust Wallet Core failed to allocate data")
        }
    }

    private fun COpaquePointer.toByteArray(): ByteArray {
        val size = TWDataSize(this).toInt()
        return TWDataBytes(this)?.readBytes(size)
            ?: error("Trust Wallet Core returned invalid data")
    }
}

private class ProtoWriter {
    private val bytes = mutableListOf<Byte>()

    fun int32(field: Int, value: Int) {
        tag(field, 0)
        varint(value.toLong())
    }

    fun bytes(field: Int, value: ByteArray) {
        tag(field, 2)
        varint(value.size.toLong())
        value.forEach { bytes += it }
    }

    fun string(field: Int, value: String) {
        bytes(field, value.encodeToByteArray())
    }

    fun message(field: Int, value: ByteArray) {
        bytes(field, value)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()

    private fun tag(field: Int, wireType: Int) {
        varint(((field shl 3) or wireType).toLong())
    }

    private fun varint(value: Long) {
        var current = value
        while ((current and 0x7f.inv().toLong()) != 0L) {
            bytes += (((current and 0x7f) or 0x80).toByte())
            current = current ushr 7
        }
        bytes += current.toByte()
    }
}

private data class EthereumSigningOutput(
    val encoded: ByteArray,
    val error: Int,
    val errorMessage: String,
) {
    companion object {
        fun parse(bytes: ByteArray): EthereumSigningOutput {
            val reader = ProtoReader(bytes)
            var encoded = ByteArray(0)
            var error = 0
            var errorMessage = ""

            while (!reader.exhausted) {
                val tag = reader.varint().toInt()
                val field = tag ushr 3
                val wireType = tag and 0x7
                when (field) {
                    1 -> encoded = reader.lengthDelimited(wireType)
                    6 -> error = reader.varint(wireType).toInt()
                    7 -> errorMessage = reader.lengthDelimited(wireType).decodeToString()
                    else -> reader.skip(wireType)
                }
            }

            return EthereumSigningOutput(
                encoded = encoded,
                error = error,
                errorMessage = errorMessage,
            )
        }
    }
}

private class ProtoReader(private val bytes: ByteArray) {
    private var index = 0

    val exhausted: Boolean
        get() = index >= bytes.size

    fun varint(expectedWireType: Int = 0): Long {
        require(expectedWireType == 0) { "Unexpected protobuf wire type $expectedWireType" }
        var shift = 0
        var result = 0L
        while (index < bytes.size) {
            val b = bytes[index++].toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        error("Malformed protobuf varint")
    }

    fun lengthDelimited(expectedWireType: Int): ByteArray {
        require(expectedWireType == 2) { "Unexpected protobuf wire type $expectedWireType" }
        val size = varint().toInt()
        require(size >= 0 && index + size <= bytes.size) { "Malformed protobuf length" }
        val value = bytes.copyOfRange(index, index + size)
        index += size
        return value
    }

    fun skip(wireType: Int) {
        when (wireType) {
            0 -> varint()
            2 -> lengthDelimited(wireType)
            else -> error("Unsupported protobuf wire type $wireType")
        }
    }
}

private fun Long.toUnsignedBytes(): ByteArray {
    require(this >= 0) { "Negative values cannot be encoded as unsigned bytes" }
    if (this == 0L) return byteArrayOf(0)
    var current = this
    val bytes = mutableListOf<Byte>()
    while (current != 0L) {
        bytes += (current and 0xff).toByte()
        current = current ushr 8
    }
    return bytes.asReversed().toByteArray()
}

private fun String.decimalToUnsignedBytes(): ByteArray {
    require(isNotEmpty()) { "Decimal value cannot be empty" }
    require(all { it in '0'..'9' }) { "Decimal value must contain only digits" }
    if (all { it == '0' }) return byteArrayOf(0)

    val digits = map { it - '0' }.toMutableList()
    val bytes = mutableListOf<Byte>()
    while (digits.any { it != 0 }) {
        var remainder = 0
        for (i in digits.indices) {
            val current = remainder * 10 + digits[i]
            digits[i] = current / 256
            remainder = current % 256
        }
        bytes += remainder.toByte()
        while (digits.size > 1 && digits.first() == 0) {
            digits.removeAt(0)
        }
    }
    return bytes.asReversed().toByteArray()
}

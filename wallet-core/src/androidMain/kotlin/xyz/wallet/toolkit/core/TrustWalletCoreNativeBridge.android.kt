package xyz.wallet.toolkit.core

import com.google.protobuf.ByteString
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

        val output: Ethereum.SigningOutput = AnySigner.sign(input, CoinType.ETHEREUM, Ethereum.SigningOutput.parser())

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

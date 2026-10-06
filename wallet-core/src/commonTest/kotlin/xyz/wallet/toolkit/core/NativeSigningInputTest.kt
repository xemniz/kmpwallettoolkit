package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertFailsWith

class NativeSigningInputTest {
    @Test
    fun rejectsSignedOrNegativeLegacyQuantitiesAtTheNativeBoundary() {
        if (!isTrustWalletCoreAvailableInCurrentTestRuntime()) return
        val tx = NativeLegacyTransaction()
        listOf(
            tx.copy(nonce = -1),
            tx.copy(valueWei = "-1"),
            tx.copy(gasPriceWei = "+1"),
            tx.copy(gasLimit = " 1"),
            tx.copy(dataHex = "0x-1"),
            tx.copy(chainId = 8453),
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                TrustWalletCoreNativeBridge.signTransaction(nativeTestMnemonic, SupportedChain.Ethereum, invalid)
            }
        }
    }

    @Test
    fun rejectsSignedOrNegativeType2QuantitiesAtTheNativeBoundary() {
        if (!isTrustWalletCoreAvailableInCurrentTestRuntime()) return
        val payload = """{"chainId":1,"to":"0x3535353535353535353535353535353535353535","valueWei":"0","maxFeePerGasWei":"20000000000","maxPriorityFeePerGasWei":"1000000000","gasLimit":"21000","nonce":0,"dataHex":null,"accessList":[]}"""
        listOf(
            payload.replace("\"nonce\":0", "\"nonce\":-1"),
            payload.replace("\"valueWei\":\"0\"", "\"valueWei\":\"-1\""),
            payload.replace("\"20000000000\"", "\"+1\""),
            payload.replace("\"1000000000\"", "\"１\""),
            payload.replace("\"21000\"", "\"\""),
            payload.replace("\"dataHex\":null", "\"dataHex\":\"0x-1\""),
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                TrustWalletCoreNativeBridge.signEip1559(nativeTestMnemonic, SupportedChain.Ethereum, invalid.encodeToByteArray())
            }
        }
    }
}

private const val nativeTestMnemonic =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

private data class NativeLegacyTransaction(
    override val chainId: Long = 1,
    override val to: String = "0x3535353535353535353535353535353535353535",
    override val valueWei: String = "0",
    override val gasPriceWei: String = "20000000000",
    override val gasLimit: String = "21000",
    override val nonce: Long = 0,
    override val dataHex: String? = null,
) : EvmTransactionData

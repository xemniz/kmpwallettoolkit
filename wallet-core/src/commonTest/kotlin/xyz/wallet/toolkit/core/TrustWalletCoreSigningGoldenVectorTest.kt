package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import xyz.wallet.toolkit.utils.hexToByteArray

class TrustWalletCoreSigningGoldenVectorTest {
    @Test
    fun signsLegacyEthereumTransactionAgainstEthersVectorWhenNativeRuntimeIsAvailable() {
        if (!isTrustWalletCoreAvailableInCurrentTestRuntime()) return

        val wallet = Wallet.fromMnemonicWithTrustWalletCore(testMnemonic)
        val signed = wallet.signTransaction(
            chain = SupportedChain.Ethereum,
            transaction = LegacyGoldenTransaction,
        )

        assertContentEquals(
            expected = legacySignedHex.hexToByteArray(),
            actual = signed,
        )
    }

    @Test
    fun signsEip1559EthereumTransactionAgainstEthersVectorWhenNativeRuntimeIsAvailable() {
        if (!isTrustWalletCoreAvailableInCurrentTestRuntime()) return

        val wallet = Wallet.fromMnemonicWithTrustWalletCore(testMnemonic)
        val signed = wallet.signEip1559Transaction(
            chain = SupportedChain.Ethereum,
            signingPayloadJson = eip1559PayloadJson.encodeToByteArray(),
        )

        assertContentEquals(
            expected = eip1559SignedHex.hexToByteArray(),
            actual = signed,
        )
    }

    private object LegacyGoldenTransaction : EvmTransactionData {
        override val chainId: Long = 1
        override val to: String = "0x3535353535353535353535353535353535353535"
        override val valueWei: String = "1000000000000000000"
        override val gasPriceWei: String = "20000000000"
        override val gasLimit: String = "21000"
        override val nonce: Long = 0
        override val dataHex: String? = null
    }
}

private const val testMnemonic =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

private const val legacySignedHex =
    "0xf86c808504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008026a012b7ebc3264027de9865fe0ef21b43423058c7ea0d1f774cd0454ffa385706faa04af7b0523032ae53a9d9102822c73db6bf3e8c37ff6cb6332b6b1d19e3bb56bc"

private const val eip1559PayloadJson =
    """{"chainId":1,"to":"0x3535353535353535353535353535353535353535","valueWei":"1000000000000000000","maxFeePerGasWei":"20000000000","maxPriorityFeePerGasWei":"1000000000","gasLimit":"21000","nonce":0,"dataHex":null,"accessList":[]}"""

private const val eip1559SignedHex =
    "0x02f8730180843b9aca008504a817c800825208943535353535353535353535353535353535353535880de0b6b3a764000080c080a06f000e8845f2c0811b3af2be55fc2d19f25804fdd89df44a2ebda4a7c8963430a00ca916d6211634be2a2327981bb578f2c5d820251d6757bbce4a079f2145cb14"

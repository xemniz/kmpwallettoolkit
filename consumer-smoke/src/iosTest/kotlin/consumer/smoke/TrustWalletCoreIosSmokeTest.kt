package consumer.smoke

import kotlin.test.Test
import kotlin.test.assertContentEquals
import xyz.wallet.toolkit.core.EvmTransactionData
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.utils.hexToByteArray

class TrustWalletCoreIosSmokeTest {
    @Test
    fun signsLegacyEthereumTransactionWithPublishedIosNativeEngine() {
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

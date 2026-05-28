package consumer.smoke

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Transaction
import xyz.wallet.toolkit.core.WalletEngine
import xyz.wallet.toolkit.core.WalletKit
import xyz.wallet.toolkit.evm.Eip1559Transaction
import xyz.wallet.toolkit.evm.signEip1559Transaction
import xyz.wallet.toolkit.rpc.RpcClient
import xyz.wallet.toolkit.utils.toHexString

class ConsumerSmoke {
    private val kit = WalletKit.withEngine(FakeWalletEngine)

    fun run(): SmokeResult {
        val wallet = kit.importWallet("test test test test test test test test test test test junk")
        val address = kit.address(wallet, SupportedChain.Base)
        val tx = Eip1559Transaction(
            chainId = SupportedChain.Base.id,
            to = address,
            valueWei = "1",
            maxFeePerGasWei = "1000000000",
            maxPriorityFeePerGasWei = "100000000",
            gasLimit = "21000",
            nonce = 7,
        )
        val rawSignedTx = wallet
            .signEip1559Transaction(SupportedChain.Base, tx)
            .toHexString()

        return SmokeResult(
            address = address,
            rawSignedTx = rawSignedTx,
            rpcFactory = RpcClient::withDefaults,
            exportedMnemonic = wallet.exportMnemonic(),
        )
    }
}

data class SmokeResult(
    val address: String,
    val rawSignedTx: String,
    val rpcFactory: (String) -> RpcClient,
    val exportedMnemonic: String,
) {
    override fun toString(): String = "SmokeResult(redacted)"
}

private object FakeWalletEngine : WalletEngine {
    override fun createMnemonic(): String = "test test test test test test test test test test test junk"

    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        return when (chain) {
            SupportedChain.Base -> "0x0000000000000000000000000000000000000001"
            else -> "0x0000000000000000000000000000000000000000"
        }
    }

    override fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray {
        return byteArrayOf(0x01, 0x02)
    }

    override fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray {
        return byteArrayOf(0x02, 0x03, 0x04)
    }
}

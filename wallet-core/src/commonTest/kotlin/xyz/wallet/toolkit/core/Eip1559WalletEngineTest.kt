package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Eip1559WalletEngineTest {

    @Test
    fun walletSignEip1559DelegatesToEngine() {
        val engine = RecordingEngine(returning = byteArrayOf(0x42, 0x43))
        val wallet = Wallet.fromMnemonic("test mnemonic", engine)

        val signed = wallet.signEip1559Transaction(
            chain = SupportedChain.Ethereum,
            signingPayloadJson = byteArrayOf(0x01, 0x02, 0x03),
        )

        assertContentEquals(byteArrayOf(0x42, 0x43), signed)
        assertEquals(1, engine.eip1559Calls.size)
        val call = engine.eip1559Calls.single()
        assertEquals("test mnemonic", call.mnemonic)
        assertEquals(SupportedChain.Ethereum, call.chain)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), call.payload)
    }

    @Test
    fun unsupportedWalletEngineThrowsForSignEip1559() {
        val engine = UnsupportedWalletEngine()
        val error = assertFailsWith<IllegalStateException> {
            engine.signEip1559(
                mnemonic = "m",
                chain = SupportedChain.Ethereum,
                signingPayloadJson = byteArrayOf(),
            )
        }
        assertTrue(
            error.message?.contains("No wallet engine configured") == true,
            "unexpected message: ${error.message}",
        )
    }

    @Test
    fun trustWalletCoreSignEip1559FallsBackUntilRuntimeIsAvailable() {
        val wallet = Wallet.fromMnemonicWithTrustWalletCore("test mnemonic")
        if (isTrustWalletCoreAvailableInCurrentTestRuntime()) {
            val error = assertFailsWith<Throwable> {
                wallet.signEip1559Transaction(
                    chain = SupportedChain.Ethereum,
                    signingPayloadJson = byteArrayOf(0),
                )
            }
            assertTrue(error.message?.isNotBlank() != false)
            return
        }

        val error = assertFailsWith<NotImplementedError> {
            wallet.signEip1559Transaction(
                chain = SupportedChain.Ethereum,
                signingPayloadJson = byteArrayOf(0),
            )
        }
        assertTrue(
            error.message?.contains("signEip1559") == true,
            "expected 'signEip1559' in error message, got: ${error.message}",
        )
    }
}

private data class RecordedEip1559Call(
    val mnemonic: String,
    val chain: SupportedChain,
    val payload: ByteArray,
) {
    override fun toString(): String = "RecordedEip1559Call(redacted)"
}

private class RecordingEngine(private val returning: ByteArray) : WalletEngine {
    val eip1559Calls = mutableListOf<RecordedEip1559Call>()

    override fun createMnemonic(): String = "test mnemonic"

    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String =
        "0x${chain.displayName.lowercase()}-address"

    override fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray = returning

    override fun signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray {
        eip1559Calls.add(RecordedEip1559Call(mnemonic, chain, signingPayloadJson.copyOf()))
        return returning.copyOf()
    }
}

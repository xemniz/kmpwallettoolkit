package xyz.wallet.toolkit.evm

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Transaction
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.core.WalletEngine
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Per T plan §4: no direct in-schema third-party golden vector could be asserted in
 * commonTest (jvm-only execution; TWC jvm bridge is a stub). This test therefore uses
 * the determinism-pair fallback allowed by the spec:
 *
 *  - Assert the payload forwarded to the engine matches [Eip1559Transaction.toSigningPayload]
 *    byte-for-byte. Combined with `Eip1559GoldenVectorTest.goldenJsonDoesNotDrift`, this
 *    pins the input side of the signing hash.
 *  - Assert two successive signings of the same transaction produce byte-identical output
 *    (engine-captured JSON + returned signed bytes).
 *
 * A true end-to-end hex golden vector will be a follow-up Android instrumented test.
 */
class Eip1559SigningGoldenVectorTest {

    private fun sampleTx(
        chainId: Long = 1,
        accessList: List<AccessListEntry> = emptyList(),
    ): Eip1559Transaction = Eip1559Transaction(
        chainId = chainId,
        to = "0x3535353535353535353535353535353535353535",
        valueWei = "1000000000000000000",
        maxFeePerGasWei = "50000000000",
        maxPriorityFeePerGasWei = "1000000000",
        gasLimit = "21000",
        nonce = 42,
        dataHex = null,
        accessList = accessList,
    )

    @Test
    fun signEip1559TransactionForwardsExactPayloadToEngine() {
        val engine = CapturingEngine(returning = byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        val wallet = Wallet.fromMnemonic("test mnemonic", engine)
        val tx = sampleTx()

        val signed = wallet.signEip1559Transaction(SupportedChain.Ethereum, tx)

        assertContentEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), signed)
        assertEquals(1, engine.eip1559Calls.size)
        val captured = engine.eip1559Calls.single()
        assertEquals("test mnemonic", captured.mnemonic)
        assertEquals(SupportedChain.Ethereum, captured.chain)
        assertContentEquals(tx.toSigningPayload(), captured.payload)
    }

    @Test
    fun signEip1559TransactionIsDeterministic() {
        val engine = CapturingEngine(returning = byteArrayOf(0x11, 0x22, 0x33))
        val wallet = Wallet.fromMnemonic("test mnemonic", engine)
        val tx = sampleTx()

        val first = wallet.signEip1559Transaction(SupportedChain.Ethereum, tx)
        val second = wallet.signEip1559Transaction(SupportedChain.Ethereum, tx)

        assertContentEquals(first, second)
        assertEquals(2, engine.eip1559Calls.size)
        assertContentEquals(
            engine.eip1559Calls[0].payload,
            engine.eip1559Calls[1].payload,
        )
    }

    @Test
    fun signEip1559TransactionRejectsChainIdMismatch() {
        val engine = CapturingEngine(returning = byteArrayOf())
        val wallet = Wallet.fromMnemonic("test mnemonic", engine)
        val tx = sampleTx(chainId = 1)

        val error = assertFailsWith<IllegalArgumentException> {
            wallet.signEip1559Transaction(SupportedChain.Base, tx)
        }
        assertTrue(
            error.message?.contains("Chain mismatch") == true,
            "expected 'Chain mismatch' in message, got: ${error.message}",
        )
        assertEquals(0, engine.eip1559Calls.size)
    }

    @Test
    fun signEip1559TransactionForwardsAccessListPayload() {
        val engine = CapturingEngine(returning = byteArrayOf(0x01))
        val wallet = Wallet.fromMnemonic("test mnemonic", engine)
        val tx = sampleTx(
            accessList = listOf(
                AccessListEntry(
                    address = "0x1111111111111111111111111111111111111111",
                    storageKeys = listOf(
                        "0x0000000000000000000000000000000000000000000000000000000000000001",
                    ),
                ),
            ),
        )

        wallet.signEip1559Transaction(SupportedChain.Ethereum, tx)

        val capturedJson = engine.eip1559Calls.single().payload.decodeToString()
        assertTrue(
            capturedJson.contains("\"accessList\":["),
            "expected populated accessList in forwarded payload: $capturedJson",
        )
        assertTrue(
            capturedJson.contains("0x1111111111111111111111111111111111111111"),
            "expected address in forwarded payload: $capturedJson",
        )
    }
}

private data class CapturedEip1559Call(
    val mnemonic: String,
    val chain: SupportedChain,
    val payload: ByteArray,
)

private class CapturingEngine(private val returning: ByteArray) : WalletEngine {
    val eip1559Calls = mutableListOf<CapturedEip1559Call>()

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
        eip1559Calls.add(CapturedEip1559Call(mnemonic, chain, signingPayloadJson.copyOf()))
        return returning.copyOf()
    }
}

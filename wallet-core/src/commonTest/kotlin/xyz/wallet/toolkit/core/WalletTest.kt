package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WalletTest {
    @Test
    fun createsWalletAndDerivesAddressUsingEngine() {
        val wallet = Wallet.create(FakeEngine)

        assertEquals("test mnemonic", wallet.mnemonic)
        assertEquals("0xethereum-address", wallet.address(SupportedChain.Ethereum))
    }

    @Test
    fun signsTransactionUsingEngine() {
        val wallet = Wallet.fromMnemonic("test mnemonic", FakeEngine)
        val signed = wallet.signTransaction(SupportedChain.Base, object : Transaction {})

        assertContentEquals(byteArrayOf(0x01, 0x02), signed)
    }

    @Test
    fun trustWalletCoreConstructorUsesNativeBridgeSeam() {
        val error = assertFailsWith<NotImplementedError> {
            Wallet.createWithTrustWalletCore()
        }
        assertTrue(error.message?.contains("Trust Wallet Core native bridge") == true)
    }

    @Test
    fun trustWalletCoreSignTransactionFallsBackUntilRuntimeIsAvailable() {
        val wallet = Wallet.fromMnemonicWithTrustWalletCore("test mnemonic")
        val error = assertFailsWith<NotImplementedError> {
            wallet.signTransaction(SupportedChain.Ethereum, object : Transaction {})
        }
        assertTrue(error.message?.contains("signTransaction") == true)
    }
}

private object FakeEngine : WalletEngine {
    override fun createMnemonic(): String = "test mnemonic"

    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        return "0x${chain.displayName.lowercase()}-address"
    }

    override fun signTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray {
        return byteArrayOf(0x01, 0x02)
    }
}


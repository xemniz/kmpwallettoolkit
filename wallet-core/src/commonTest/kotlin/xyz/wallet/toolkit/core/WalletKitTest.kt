package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WalletKitTest {
    @Test
    fun createsAndImportsWalletsWithConfiguredEngine() {
        val kit = WalletKit.withEngine(engine = KitFakeEngine)

        val created = kit.createWallet()
        val imported = kit.importWallet("imported mnemonic")

        assertEquals("created mnemonic", created.mnemonic)
        assertEquals("imported mnemonic", imported.mnemonic)
    }

    @Test
    fun derivesAddressForSupportedChain() {
        val kit = WalletKit.withEngine(
            engine = KitFakeEngine,
            supportedChains = listOf(SupportedChain.Base),
        )
        val wallet = kit.importWallet("mnemonic")

        assertEquals("0xbase", kit.address(wallet, SupportedChain.Base))
    }

    @Test
    fun rejectsUnsupportedChainBeforeCallingEngine() {
        val kit = WalletKit.withEngine(
            engine = KitFakeEngine,
            supportedChains = listOf(SupportedChain.Base),
        )
        val wallet = kit.importWallet("mnemonic")

        val error = assertFailsWith<IllegalArgumentException> {
            kit.address(wallet, SupportedChain.Ethereum)
        }

        assertTrue(error.message?.contains("Unsupported chain") == true)
    }

    @Test
    fun signsLegacyAndEip1559Payloads() {
        val kit = WalletKit.withEngine(engine = KitFakeEngine)
        val wallet = kit.importWallet("mnemonic")

        assertContentEquals(
            byteArrayOf(0x01, 0x02),
            kit.signTransaction(wallet, SupportedChain.Ethereum, object : Transaction {}),
        )
        assertContentEquals(
            byteArrayOf(0x03, 0x04),
            kit.signEip1559Payload(wallet, SupportedChain.Ethereum, byteArrayOf(0x7b, 0x7d)),
        )
    }

    @Test
    fun importRejectsBlankMnemonic() {
        val kit = WalletKit.withEngine(engine = KitFakeEngine)

        assertFailsWith<IllegalArgumentException> {
            kit.importWallet(" ")
        }
    }

    @Test
    fun chainListIsDeduplicatedByChainId() {
        val kit = WalletKit.withEngine(
            engine = KitFakeEngine,
            supportedChains = listOf(SupportedChain.Base, SupportedChain.Base),
        )

        assertEquals(listOf(SupportedChain.Base), kit.supportedChains)
    }
}

private object KitFakeEngine : WalletEngine {
    override fun createMnemonic(): String = "created mnemonic"

    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        return when (chain) {
            SupportedChain.Base -> "0xbase"
            SupportedChain.Ethereum -> "0xethereum"
            else -> "0xother"
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
        return byteArrayOf(0x03, 0x04)
    }
}

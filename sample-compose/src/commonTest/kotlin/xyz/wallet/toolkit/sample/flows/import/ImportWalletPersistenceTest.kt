package xyz.wallet.toolkit.sample.flows.import

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Transaction
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.core.WalletEngine
import xyz.wallet.toolkit.sample.state.SecureWalletStorage
import xyz.wallet.toolkit.sample.state.WalletSession

class ImportWalletPersistenceTest {
    @Test
    fun failedSaveKeepsPhraseAndRestoredWalletForRetry() {
        var savesSucceed = false
        val storage = object : SecureWalletStorage {
            override fun save(mnemonic: String): Boolean = savesSucceed
            override fun load(): String? = null
            override fun clear(): Boolean = true
        }
        val session = WalletSession(storage)
        val state = ImportWalletState()
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        state.onInputChange(phrase)
        var restores = 0
        val wallet = Wallet.fromMnemonic(phrase, ImportTestEngine)
        val restore: (String) -> Wallet = { restores++; wallet }

        assertFalse(state.submit(session, restore))
        assertEquals(phrase, state.input)
        assertFalse(state.isSubmitting)
        assertEquals("Could not save wallet. Retry restoring it.", state.errorMessage)

        savesSucceed = true
        assertTrue(state.submit(session, restore))
        assertEquals(1, restores)
        assertSame(wallet, session.wallet)
    }
}

private object ImportTestEngine : WalletEngine {
    override fun createMnemonic(): String = error("Not used")
    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String = "0xowner"
    override fun signTransaction(mnemonic: String, chain: SupportedChain, transaction: Transaction): ByteArray =
        error("Signing is outside this import test")
}

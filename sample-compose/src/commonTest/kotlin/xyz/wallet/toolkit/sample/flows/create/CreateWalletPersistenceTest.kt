package xyz.wallet.toolkit.sample.flows.create

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.sample.state.SecureWalletStorage
import xyz.wallet.toolkit.sample.state.WalletSession

class CreateWalletPersistenceTest {
    @Test
    fun failedSaveKeepsConfirmedWalletForRetry() {
        val storage = RetryStorage()
        val session = WalletSession(storage)
        val wallet = Wallet.fromMnemonic("test recovery phrase")
        val state = CreateWalletState().apply {
            this.wallet = wallet
            confirmed = true
        }

        assertFalse(state.save(session))
        assertSame(wallet, state.wallet)
        assertTrue(state.confirmed)
        assertEquals("Could not save wallet. Retry saving it.", state.error)

        storage.succeeds = true
        assertTrue(state.save(session))
        assertSame(wallet, session.wallet)
        assertSame(wallet, state.wallet)
        assertEquals(2, storage.saves)
    }
}

private class RetryStorage : SecureWalletStorage {
    var succeeds = false
    var saves = 0
    override fun save(mnemonic: String): Boolean {
        saves++
        return succeeds
    }
    override fun load(): String? = null
    override fun clear(): Boolean = true
}

package xyz.wallet.toolkit.sample.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Transaction
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.core.WalletEngine

class WalletSessionTest {
    @Test
    fun failedSaveKeepsCurrentSessionAndRetryUsesSameWallet() {
        val storage = TestStorage()
        val session = WalletSession(storage)
        val original = testWallet()
        val replacement = testWallet()
        assertTrue(session.login("original", original))
        val epoch = session.epoch

        storage.saveSucceeds = false
        assertFalse(session.login("replacement", replacement))
        assertSame(original, session.wallet)
        assertEquals(epoch, session.epoch)

        storage.saveSucceeds = true
        assertTrue(session.login("replacement", replacement))
        assertSame(replacement, session.wallet)
        assertEquals("replacement", storage.mnemonic)
    }

    @Test
    fun failedSignOutPreservesSessionAndAuthorization() {
        val storage = TestStorage()
        val session = WalletSession(storage)
        val wallet = testWallet()
        session.login("phrase", wallet)
        val epoch = session.epoch
        storage.clearSucceeds = false
        var invalidations = 0
        session.addInvalidationListener { invalidations++ }

        assertFalse(session.logout())

        assertSame(wallet, session.wallet)
        assertTrue(session.canSign(epoch))
        assertEquals(0, invalidations)
    }

    @Test
    fun successfulSignOutRevokesAuthorizationBeforeNotifyingAndKeepsJournal() {
        val storage = TestStorage()
        storage.saveJournal("unfinished")
        val session = WalletSession(storage)
        session.login("phrase", testWallet())
        val epoch = session.epoch
        var notified = false
        session.addInvalidationListener {
            notified = true
            assertNull(session.wallet)
            assertFalse(session.canSign(epoch))
        }

        assertTrue(session.logout())

        assertTrue(notified)
        assertNull(storage.mnemonic)
        assertEquals("unfinished", storage.loadJournal())
    }

    @Test
    fun hydrationReadsStorageOnceAndDoesNotRewriteMnemonic() {
        val storage = TestStorage().apply { mnemonic = "stored" }
        val restored = testWallet()
        val session = WalletSession(storage, restoreWallet = { restored })

        assertTrue(session.hydrateOnce())
        assertTrue(session.hydrateOnce())

        assertSame(restored, session.wallet)
        assertEquals(1, storage.loads)
        assertEquals(0, storage.saves)
    }

    @Test
    fun missingStorageNeverReportsSuccessfulPersistence() {
        assertFalse(MissingSecureWalletStorage.save("phrase"))
        assertFalse(MissingSecureWalletStorage.clear())
        assertNull(MissingSecureWalletStorage.load())
        assertFalse(MissingSecureOperationStorage.saveJournal("unfinished"))
        assertNull(MissingSecureOperationStorage.loadJournal())
    }
}

private class TestStorage : SecureWalletStorage, SecureOperationStorage {
    var mnemonic: String? = null
    private var journal: String? = null
    var saveSucceeds = true
    var clearSucceeds = true
    var loads = 0
    var saves = 0

    override fun save(mnemonic: String): Boolean {
        saves++
        if (!saveSucceeds) return false
        this.mnemonic = mnemonic
        return true
    }

    override fun load(): String? {
        loads++
        return mnemonic
    }

    override fun clear(): Boolean {
        if (!clearSucceeds) return false
        mnemonic = null
        return true
    }

    override fun loadJournal(): String? = journal

    override fun saveJournal(serialized: String): Boolean {
        journal = serialized
        return true
    }

    override fun toString(): String = "TestStorage(redacted)"
}

private fun testWallet(): Wallet = Wallet.fromMnemonic("test phrase", object : WalletEngine {
    override fun createMnemonic(): String = "test phrase"
    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String = "0xowner"
    override fun signTransaction(mnemonic: String, chain: SupportedChain, transaction: Transaction): ByteArray =
        error("Signing is outside this session test")
})

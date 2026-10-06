package xyz.wallet.toolkit.sample.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet

class WalletSession(
    private val storage: SecureWalletStorage = SecureWalletStorageRuntime.get(),
    private val restoreWallet: (String) -> Wallet = Wallet::fromMnemonicWithTrustWalletCore,
) {
    var wallet: Wallet? by mutableStateOf<Wallet?>(null)
        private set
    var selectedChain: SupportedChain by mutableStateOf(SupportedChain.Ethereum)
    var lastTxHash: String? by mutableStateOf<String?>(null)
    var epoch: Long by mutableStateOf(0L)
        private set
    var restorationFailed: Boolean by mutableStateOf(false)
        private set
    private var hydrated = false
    private val invalidationListeners = mutableListOf<() -> Unit>()

    fun hydrateOnce(): Boolean {
        if (hydrated) return wallet != null
        hydrated = true
        try {
            val mnemonic = storage.load() ?: return false
            val restored = restoreWallet(mnemonic)
            restored.address(selectedChain)
            wallet = restored
            epoch++
            return true
        } catch (_: Exception) {
            restorationFailed = true
            return false
        }
    }

    fun login(mnemonic: String, wallet: Wallet): Boolean {
        val saved = try {
            storage.save(mnemonic)
        } catch (_: Exception) {
            false
        }
        if (!saved) return false
        hydrated = true
        restorationFailed = false
        this.wallet = wallet
        lastTxHash = null
        invalidate()
        return true
    }

    fun logout(): Boolean {
        val cleared = try {
            storage.clear()
        } catch (_: Exception) {
            false
        }
        if (!cleared) return false
        wallet = null
        lastTxHash = null
        invalidate()
        return true
    }

    fun canSign(expectedEpoch: Long): Boolean = wallet != null && epoch == expectedEpoch

    fun addInvalidationListener(listener: () -> Unit): () -> Unit {
        invalidationListeners.add(listener)
        return { invalidationListeners.remove(listener) }
    }

    private fun invalidate() {
        epoch++
        invalidationListeners.toList().forEach { listener -> runCatching(listener) }
    }

    override fun toString(): String =
        "WalletSession(wallet=${if (wallet == null) "null" else "REDACTED"}, chain=${selectedChain.id})"
}

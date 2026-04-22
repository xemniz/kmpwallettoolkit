package xyz.wallet.toolkit.sample.state

/**
 * Single slot for the platform [SecureWalletStorage]. The host app
 * (`sample-app` on Android, `iosApp` on iOS) installs an instance at
 * process start; commonMain consumers read via [get].
 *
 * If no host installs one, [get] returns [NoOpSecureWalletStorage] — a
 * transient in-memory stand-in so tests and dev runs don't crash.
 * Persistence is lost on process death in that case.
 */
object SecureWalletStorageRuntime {
    private var installed: SecureWalletStorage? = null

    fun install(storage: SecureWalletStorage) {
        installed = storage
    }

    fun get(): SecureWalletStorage = installed ?: NoOpSecureWalletStorage
}

internal object NoOpSecureWalletStorage : SecureWalletStorage {
    private var slot: String? = null

    override fun save(mnemonic: String) {
        slot = mnemonic
    }

    override fun load(): String? = slot

    override fun clear() {
        slot = null
    }

    override fun toString(): String = "NoOpSecureWalletStorage"
}

package xyz.wallet.toolkit.sample.state

/**
 * Single slot for the platform [SecureWalletStorage]. The host app
 * (`sample-app` on Android, `iosApp` on iOS) installs an instance at
 * process start; commonMain consumers read via [get].
 *
 * Missing platform storage cannot report successful persistence.
 */
object SecureWalletStorageRuntime {
    private var installed: SecureWalletStorage? = null

    fun install(storage: SecureWalletStorage) {
        installed = storage
    }

    fun get(): SecureWalletStorage = installed ?: MissingSecureWalletStorage

    fun getOperationStorage(): SecureOperationStorage =
        installed as? SecureOperationStorage ?: MissingSecureOperationStorage
}

internal object MissingSecureWalletStorage : SecureWalletStorage {
    override fun save(mnemonic: String): Boolean = false
    override fun load(): String? = null
    override fun clear(): Boolean = false
    override fun toString(): String = "MissingSecureWalletStorage"
}

internal object MissingSecureOperationStorage : SecureOperationStorage {
    override val journalReadFailed: Boolean = true
    override fun loadJournal(): String? = null
    override fun saveJournal(serialized: String): Boolean = false
    override fun toString(): String = "MissingSecureOperationStorage"
}

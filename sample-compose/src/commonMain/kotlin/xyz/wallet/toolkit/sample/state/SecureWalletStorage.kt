package xyz.wallet.toolkit.sample.state

/**
 * Platform-backed secret store for the showcase wallet's mnemonic.
 *
 * Implementations must persist with platform standard at-rest encryption
 * (Android Keystore-backed EncryptedSharedPreferences; iOS Keychain).
 * Implementations must not log, println, or reference the stored value
 * in toString.
 *
 * Calls are synchronous; callers are responsible for dispatching off the
 * main thread when I/O cost matters. Keeping the contract non-suspend
 * simplifies Kotlin/Native interop — the iOS host installs a Swift class
 * that conforms to this protocol without needing async bridging.
 */
interface SecureWalletStorage {
    fun save(mnemonic: String): Boolean
    fun load(): String?
    fun clear(): Boolean
}

/** Encrypted unfinished-operation records are stored separately from the mnemonic. */
interface SecureOperationStorage {
    /** Distinguishes a missing journal from a platform read failure where exceptions cannot cross interop. */
    val journalReadFailed: Boolean get() = false
    fun loadJournal(): String?
    fun saveJournal(serialized: String): Boolean
}

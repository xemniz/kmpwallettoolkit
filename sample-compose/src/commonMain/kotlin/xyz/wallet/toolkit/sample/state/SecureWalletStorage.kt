package xyz.wallet.toolkit.sample.state

/**
 * Platform-backed secret store for the showcase wallet's mnemonic.
 *
 * Implementations must persist with platform standard at-rest encryption
 * (Android Keystore-backed EncryptedSharedPreferences; iOS Keychain).
 * Implementations must not log, println, or reference the stored value
 * in toString — see CLAUDE.md §4.1.
 *
 * Calls are synchronous; callers are responsible for dispatching off the
 * main thread when I/O cost matters. Keeping the contract non-suspend
 * simplifies Kotlin/Native interop — the iOS host installs a Swift class
 * that conforms to this protocol without needing async bridging.
 */
interface SecureWalletStorage {
    fun save(mnemonic: String)
    fun load(): String?
    fun clear()
}

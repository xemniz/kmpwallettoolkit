package xyz.wallet.toolkit.sample.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import xyz.wallet.toolkit.sample.state.SecureWalletStorage

private const val PREFS_FILE = "xyz.wallet.toolkit.sample.secure"
private const val KEY_MNEMONIC = "mnemonic"

/**
 * EncryptedSharedPreferences-backed mnemonic storage. The master key lives
 * in the AndroidKeyStore (AES256_GCM scheme) so the ciphertext on disk is
 * only decryptable on this device.
 *
 * CLAUDE.md §4.1: never log the stored value; `toString()` is redacted.
 */
class AndroidSecureWalletStorage(context: Context) : SecureWalletStorage {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun save(mnemonic: String) {
        prefs.edit().putString(KEY_MNEMONIC, mnemonic).commit()
    }

    override fun load(): String? = prefs.getString(KEY_MNEMONIC, null)

    override fun clear() {
        prefs.edit().remove(KEY_MNEMONIC).commit()
    }

    override fun toString(): String = "AndroidSecureWalletStorage"
}

package xyz.wallet.toolkit.sample.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import xyz.wallet.toolkit.sample.state.SecureWalletStorage
import xyz.wallet.toolkit.sample.state.SecureOperationStorage

private const val PREFS_FILE = "xyz.wallet.toolkit.sample.secure"
private const val KEY_MNEMONIC = "mnemonic"
private const val KEY_OPERATION_JOURNAL = "unfinished-operations-v1"

/**
 * EncryptedSharedPreferences-backed mnemonic storage. The master key lives
 * in the AndroidKeyStore (AES256_GCM scheme) so the ciphertext on disk is
 * only decryptable on this device.
 *
 * Stored values must never appear in logs or errors.
 */
class AndroidSecureWalletStorage(context: Context) : SecureWalletStorage, SecureOperationStorage {
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

    override fun save(mnemonic: String): Boolean =
        prefs.edit().putString(KEY_MNEMONIC, mnemonic).commit()

    override fun load(): String? = prefs.getString(KEY_MNEMONIC, null)

    override fun clear(): Boolean =
        prefs.edit().remove(KEY_MNEMONIC).commit()

    override fun loadJournal(): String? = prefs.getString(KEY_OPERATION_JOURNAL, null)

    override fun saveJournal(serialized: String): Boolean =
        prefs.edit().putString(KEY_OPERATION_JOURNAL, serialized).commit()

    override fun toString(): String = "AndroidSecureWalletStorage"
}

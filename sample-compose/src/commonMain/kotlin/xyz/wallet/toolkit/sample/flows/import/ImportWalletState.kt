package xyz.wallet.toolkit.sample.flows.import

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.sample.state.WalletSession

/** Recovery words and a restored wallet stay in memory until persistence succeeds. */
class ImportWalletState {
    var input: String by mutableStateOf("")
        private set

    var errorMessage: String? by mutableStateOf<String?>(null)
        private set

    val submissionError: Boolean get() = errorMessage != null
    var isSubmitting: Boolean by mutableStateOf(false)
        private set
    private var restoredWallet: Wallet? = null

    val validation: ValidationResult
        get() = validate(input)

    fun onInputChange(newValue: String) {
        input = newValue
        errorMessage = null
        restoredWallet = null
    }

    fun submit(
        session: WalletSession,
        restoreWallet: (String) -> Wallet = Wallet::fromMnemonicWithTrustWalletCore,
    ): Boolean {
        val current = validation
        if (current !is ValidationResult.Valid || isSubmitting) return false
        isSubmitting = true
        val phrase = current.words.joinToString(" ")
        try {
            val wallet = restoredWallet ?: restoreWallet(phrase).also {
                // Derive before saving so an invalid phrase is rejected on this screen.
                it.address(session.selectedChain)
                restoredWallet = it
            }
            if (!session.login(phrase, wallet)) {
                errorMessage = "Could not save wallet. Retry restoring it."
                return false
            }
            errorMessage = null
            return true
        } catch (_: Exception) {
            errorMessage = "Could not restore wallet — check the phrase and try again."
            return false
        } finally {
            isSubmitting = false
        }
    }

    override fun toString(): String =
        "ImportWalletState(input=REDACTED, validation=${validation::class.simpleName}, " +
            "submissionError=$submissionError, isSubmitting=$isSubmitting)"
}

@Composable
fun rememberImportWalletState(): ImportWalletState = remember { ImportWalletState() }

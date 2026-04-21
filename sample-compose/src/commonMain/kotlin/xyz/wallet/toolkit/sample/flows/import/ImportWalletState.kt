package xyz.wallet.toolkit.sample.flows.import

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Plain Compose state holder for the mnemonic-import screen.
 *
 * CLAUDE.md §4.1: [input] contains user-supplied secret material. It MUST NOT
 * be interpolated into [toString], logs, or exception messages. The overridden
 * [toString] prints a fixed "REDACTED" marker regardless of [input] contents.
 */
class ImportWalletState {
    var input: String by mutableStateOf("")
        private set

    var submissionError: Boolean by mutableStateOf(false)
        private set

    var isSubmitting: Boolean by mutableStateOf(false)

    val validation: ValidationResult
        get() = validate(input)

    fun onInputChange(newValue: String) {
        input = newValue
        // Any edit clears a prior failure so the user sees a fresh slate.
        submissionError = false
    }

    fun markSubmissionError() {
        submissionError = true
    }

    override fun toString(): String =
        "ImportWalletState(input=REDACTED, validation=${validation::class.simpleName}, " +
            "submissionError=$submissionError, isSubmitting=$isSubmitting)"
}

@Composable
fun rememberImportWalletState(): ImportWalletState = remember { ImportWalletState() }

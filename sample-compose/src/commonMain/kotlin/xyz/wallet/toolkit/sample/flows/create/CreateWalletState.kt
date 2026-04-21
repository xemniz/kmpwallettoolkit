package xyz.wallet.toolkit.sample.flows.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import xyz.wallet.toolkit.core.Wallet

/**
 * In-memory state holder for the Create-wallet flow.
 *
 * Intentionally NOT a data class: auto-generated `toString()` would expose the
 * stored `Wallet.mnemonic` via `Wallet.toString()` and leak the confirm-challenge
 * word list. The hand-rolled `toString()` below redacts both (CLAUDE.md §4.1).
 */
class CreateWalletState {
    var wallet: Wallet? by mutableStateOf<Wallet?>(null)
    var revealed: Boolean by mutableStateOf(false)
    var confirmIndex: Int by mutableStateOf(0)
    var confirmOptions: List<String> by mutableStateOf(emptyList())
    var confirmed: Boolean by mutableStateOf(false)
    var error: String? by mutableStateOf<String?>(null)

    /**
     * Prepares the confirm-word challenge from an already-revealed mnemonic.
     * Returns true on success; false when the mnemonic does not split into the
     * expected 12 words (in which case the caller should surface the flat
     * "Wallet creation failed" label and clear the wallet).
     */
    fun prepareConfirm(mnemonic: String): Boolean {
        val words = mnemonic.trim().split(Regex("\\s+"))
        if (words.size != 12) return false
        // §4.2 UX-only: random pick from a list already displayed on screen; not security material.
        val index = Random.nextInt(0, words.size)
        val correct = words[index]
        val decoyPool = words.toMutableList().apply { removeAt(index) }.distinct().filter { it != correct }
        // §4.2 UX-only: random pick from a list already displayed on screen; not security material.
        val decoys = decoyPool.shuffled(Random).take(3)
        // §4.2 UX-only: random pick from a list already displayed on screen; not security material.
        val options = (decoys + correct).shuffled(Random)
        confirmIndex = index
        confirmOptions = options
        return true
    }

    override fun toString(): String =
        "CreateWalletState(hasWallet=${wallet != null}, revealed=$revealed, confirmed=$confirmed, error=$error)"
}

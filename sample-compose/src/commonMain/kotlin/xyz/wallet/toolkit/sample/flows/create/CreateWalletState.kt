package xyz.wallet.toolkit.sample.flows.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    override fun toString(): String =
        "CreateWalletState(hasWallet=${wallet != null}, revealed=$revealed, confirmed=$confirmed, error=$error)"
}

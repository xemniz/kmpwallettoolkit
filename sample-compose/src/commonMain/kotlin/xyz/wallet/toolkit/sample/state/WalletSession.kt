package xyz.wallet.toolkit.sample.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet

/**
 * In-memory session for the sample-compose showcase.
 *
 * Intentionally NOT a data class: auto-generated `toString()` would invoke
 * `Wallet.toString()` and risk leaking mnemonic bytes (CLAUDE.md §4.1).
 * The hand-rolled `toString()` below prints the literal "REDACTED" when a
 * wallet is present and never references `wallet.mnemonic`.
 *
 * Portfolio state lives in Koin (see `PortfolioRepository`), not here — a
 * repository owned by DI is the right scope for data that must survive
 * screen navigation. Session-local state is limited to: what wallet is
 * logged in, and the last broadcast tx hash for the Tx-status screen.
 */
class WalletSession {
    var wallet: Wallet? by mutableStateOf<Wallet?>(null)
    var selectedChain: SupportedChain by mutableStateOf(SupportedChain.Ethereum)
    var lastTxHash: String? by mutableStateOf<String?>(null)

    override fun toString(): String =
        "WalletSession(wallet=${if (wallet == null) "null" else "REDACTED"}, chain=${selectedChain.id}, lastTxHash=$lastTxHash)"
}

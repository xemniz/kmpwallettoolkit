package xyz.wallet.toolkit.sample.flows.tx

import xyz.wallet.toolkit.rpc.TransactionReceipt

sealed class TxStatusState {
    /**
     * Still waiting on a receipt. [elapsedSeconds] is the time since the
     * polling effect started. [stalled] flips true once we cross the 5-minute
     * soft-timeout; we keep polling, we just change the subtext.
     */
    data class Pending(val elapsedSeconds: Int, val stalled: Boolean, val retrying: Boolean = false) : TxStatusState()

    /** Receipt decoded with `status == "0x1"`. */
    data class Confirmed(val receipt: TransactionReceipt) : TxStatusState()

    /** Receipt decoded with `status == "0x0"` — reverted on chain. */
    data class Failed(val reason: String) : TxStatusState()
}

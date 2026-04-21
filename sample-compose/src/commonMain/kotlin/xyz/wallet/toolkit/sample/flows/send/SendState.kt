package xyz.wallet.toolkit.sample.flows.send

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-memory holder for the Send flow form.
 *
 * Intentionally NOT a `data class`: the compiler-generated `toString` /
 * `equals` would expose recipient / amount / gas values — all of which are
 * transactionally sensitive (CLAUDE.md §4.1). The hand-rolled [toString]
 * below returns a fixed redacted literal; do not add fields to it.
 *
 * The class is a plain `class` with `mutableStateOf`-delegated `var`s so
 * Compose observes field changes. `chainId` is pinned from `Route.Send`
 * and not editable by the user (spec: no chain switching inside Send).
 */
class SendState(chainId: Long) {
    var recipientRaw: String by mutableStateOf("")
    var recipientNormalized: String? by mutableStateOf(null)
    var amountEth: String by mutableStateOf("")
    var maxFeeGwei: String by mutableStateOf("")
    var maxPriorityGwei: String by mutableStateOf("")
    val chainId: Long = chainId
    var submission: SubmissionStatus by mutableStateOf(SubmissionStatus.Idle)

    override fun toString(): String = "SendState(redacted)"
}

sealed class SubmissionStatus {
    object Idle : SubmissionStatus()
    object Submitting : SubmissionStatus()
    data class Error(val userMessage: String) : SubmissionStatus()
}

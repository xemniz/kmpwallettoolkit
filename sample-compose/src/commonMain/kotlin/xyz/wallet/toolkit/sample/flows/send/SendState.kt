package xyz.wallet.toolkit.sample.flows.send

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable form state. Its string representation excludes entered transaction details. */
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

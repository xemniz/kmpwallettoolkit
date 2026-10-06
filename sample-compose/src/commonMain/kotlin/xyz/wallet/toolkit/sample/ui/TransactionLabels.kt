package xyz.wallet.toolkit.sample.ui

import xyz.wallet.toolkit.sample.execution.OperationStatus
import xyz.wallet.toolkit.sample.execution.StepKind
import xyz.wallet.toolkit.sample.execution.StepStatus

internal fun StepKind.displayName(): String = when (this) {
    StepKind.Send -> "Send"
    StepKind.ResetAllowance -> "Reset allowance to zero"
    StepKind.Approve -> "Approve exact trade amount"
    StepKind.Swap -> "Swap"
}

internal fun StepStatus.displayName(): String = when (this) {
    StepStatus.Planned -> "Not submitted"
    StepStatus.Prepared -> "Submission outcome uncertain; checking the network"
    StepStatus.Pending -> "Waiting for confirmation"
    StepStatus.Confirmed -> "Confirmed"
    StepStatus.Reverted -> "Reverted"
    StepStatus.NonceConsumedUnknownOutcome -> "Confirmation unavailable"
}

internal fun OperationStatus.displayName(): String = when (this) {
    OperationStatus.Executing -> "Submitting reviewed transactions"
    OperationStatus.Monitoring -> "Checking transaction progress"
    OperationStatus.NeedsReview -> "Fresh review required"
    OperationStatus.Confirmed -> "Confirmed"
    OperationStatus.Reverted -> "Transaction reverted"
    OperationStatus.UnknownOutcome -> "Confirmation unavailable"
    OperationStatus.Failed -> "Submission stopped"
}

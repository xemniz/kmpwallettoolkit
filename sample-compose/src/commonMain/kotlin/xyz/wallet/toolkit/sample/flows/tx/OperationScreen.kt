package xyz.wallet.toolkit.sample.flows.tx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import xyz.wallet.toolkit.core.ChainRegistry
import xyz.wallet.toolkit.sample.execution.OperationKind
import xyz.wallet.toolkit.sample.execution.OperationStatus
import xyz.wallet.toolkit.sample.execution.TransactionExecutionRepository
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.state.LocalWalletSession
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.BackBar
import xyz.wallet.toolkit.sample.ui.MonoText
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton
import xyz.wallet.toolkit.sample.ui.displayName

@Composable
fun OperationScreen(route: Route.Operation, navigator: Navigator) {
    val execution: TransactionExecutionRepository = koinInject()
    val operations by execution.operations.collectAsState()
    val storageError by execution.storageError.collectAsState()
    val wallet = LocalWalletSession.current.wallet
    val operation = operations.firstOrNull { it.id == route.id }
    val chain = operation?.let { ChainRegistry.byId(it.chainId) }
    val visible = wallet != null && chain != null && operation?.owner == wallet.address(chain).lowercase()

    PhoneFrame {
        BackBar(onBack = { navigator.replace(Route.Home) }, title = "Transaction progress")
        if (!visible) {
            Text("This operation is unavailable for the current wallet.", color = WalletColors.textSecondary)
            return@PhoneFrame
        }
        Text(operation.status.displayName(), color = WalletColors.textPrimary)
        operation.message?.let { Text(it, color = WalletColors.textSecondary) }
        operation.steps.forEach { step ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(step.kind.displayName(), color = WalletColors.textPrimary)
                Text(step.status.displayName(), color = WalletColors.textSecondary)
                step.hash?.let { MonoText(it) }
            }
        }
        if (storageError) {
            Text("Could not save transaction progress. Further signing is paused.", color = WalletColors.textSecondary)
            TextButton(onClick = { execution.retryStorage() }) { Text("Retry storage") }
        }
        if (operation.status in setOf(OperationStatus.NeedsReview, OperationStatus.UnknownOutcome, OperationStatus.Failed)) {
            PrimaryButton("Review again", onClick = {
                navigator.push(when (operation.kind) {
                    OperationKind.Send -> Route.Send(operation.chainId)
                    OperationKind.Swap -> Route.Swap(operation.chainId, operation.id)
                })
            })
        }
        PrimaryButton("Back to Home", onClick = { navigator.replace(Route.Home) })
    }
}

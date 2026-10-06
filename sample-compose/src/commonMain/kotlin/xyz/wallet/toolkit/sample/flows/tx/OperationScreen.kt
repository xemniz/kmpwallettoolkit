package xyz.wallet.toolkit.sample.flows.tx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import xyz.wallet.toolkit.core.ChainRegistry
import xyz.wallet.toolkit.sample.execution.OperationKind
import xyz.wallet.toolkit.sample.execution.OperationStatus
import xyz.wallet.toolkit.sample.execution.TransactionExecutionRepository
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.platform.rememberClipboardTextHandler
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
    val clipboard = rememberClipboardTextHandler()
    var copiedHash by remember(route.id) { mutableStateOf<String?>(null) }
    val operations by execution.operations.collectAsState()
    val storageError by execution.storageError.collectAsState()
    val wallet = LocalWalletSession.current.wallet
    val operation = operations.firstOrNull { it.id == route.id }
    val chain = operation?.let { ChainRegistry.byId(it.chainId) }
    val visible = wallet != null && chain != null && operation?.owner == wallet.address(chain).lowercase()

    LaunchedEffect(copiedHash) {
        if (copiedHash != null) {
            delay(1_500L)
            copiedHash = null
        }
    }

    PhoneFrame {
        BackBar(onBack = { navigator.replace(Route.Home) }, title = "Transaction progress")
        if (!visible) {
            Text("This operation is unavailable for the current wallet.", color = WalletColors.textSecondary)
            return@PhoneFrame
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(operation.status.displayName(), color = WalletColors.textPrimary)
            if (operation.status == OperationStatus.UnknownOutcome) {
                Text(
                    "We couldn't confirm the result on this network. This transaction may have completed. Check its transaction ID before sending again.",
                    color = WalletColors.textSecondary,
                )
            }
            operation.message?.let { Text(it, color = WalletColors.textSecondary) }
            operation.steps.forEach { step ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(step.kind.displayName(), color = WalletColors.textPrimary)
                    Text(step.status.displayName(), color = WalletColors.textSecondary)
                    step.hash?.let { hash ->
                        MonoText(hash)
                        TextButton(onClick = {
                            clipboard.setText(hash)
                            copiedHash = hash
                        }) { Text(if (copiedHash == hash) "Copied" else "Copy transaction ID") }
                    }
                }
            }
            if (storageError) {
                Text("Could not save transaction progress. Further signing is paused.", color = WalletColors.textSecondary)
                TextButton(onClick = { execution.retryStorage() }) { Text("Retry storage") }
            }
        }
        if (operation.status in setOf(OperationStatus.NeedsReview, OperationStatus.Failed)) {
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

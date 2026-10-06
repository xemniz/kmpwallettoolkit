package xyz.wallet.toolkit.sample.flows.tx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import xyz.wallet.toolkit.rpc.ReceiptPollingState
import xyz.wallet.toolkit.rpc.RpcClient
import xyz.wallet.toolkit.rpc.pollTransactionReceipt

@Composable
fun TxPollingEffect(txHash: String, rpc: RpcClient, onState: (TxStatusState) -> Unit) {
    LaunchedEffect(txHash, rpc) {
        rpc.pollTransactionReceipt(transactionHash = txHash, onState = { state ->
            when (state) {
                is ReceiptPollingState.Pending -> onState(TxStatusState.Pending(state.elapsedSeconds, state.stalled, state.retrying))
                is ReceiptPollingState.Received -> onState(
                    if (state.receipt.status == "0x1") TxStatusState.Confirmed(state.receipt)
                    else TxStatusState.Failed("Transaction reverted"),
                )
            }
        })
    }
}

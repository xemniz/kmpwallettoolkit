package xyz.wallet.toolkit.sample.flows.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import xyz.wallet.toolkit.sample.execution.ExecutionReview
import xyz.wallet.toolkit.sample.execution.TransactionExecutionRepository
import xyz.wallet.toolkit.sample.flows.send.ReviewAndSignSheet
import xyz.wallet.toolkit.core.ChainRegistry
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.state.LocalWalletSession
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.BackBar
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

@Composable
fun SwapScreen(route: Route.Swap, navigator: Navigator) {
    val session = LocalWalletSession.current
    val wallet = session.wallet
    val chain: SupportedChain? = ChainRegistry.byId(route.chainId)

    if (wallet == null || chain == null) {
        PhoneFrame {
            BackBar(onBack = { navigator.pop() }, title = "Swap")
            Text(
                text = if (wallet == null) "No wallet" else "Unsupported chain",
                color = WalletColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val vmFactory: SwapViewModelFactory = koinInject()
    val scope = rememberCoroutineScope()
    val vm = remember(chain, wallet) { vmFactory.create(chain, wallet.address(chain), scope) }
    DisposableEffect(vm) { onDispose { vm.dispose() } }
    val execution: TransactionExecutionRepository = koinInject()
    var selectedQuote by remember(vm) { mutableStateOf<AcceptedSwapQuote?>(null) }
    var review by remember(vm) { mutableStateOf<ExecutionReview?>(null) }
    var preparing by remember(vm) { mutableStateOf(false) }
    var error by remember(vm) { mutableStateOf<String?>(null) }

    val ui by vm.state.collectAsState()
    LaunchedEffect(vm, route.operationId) {
        val saved = execution.operations.value.firstOrNull { it.id == route.operationId && it.owner == wallet.address(chain).lowercase() && it.chainId == chain.id }
        saved?.swapIntent?.let { intent ->
            vm.restoreDraft(
                TokenRef(intent.sellSymbol, intent.sellSymbol, intent.sellAddress, intent.sellDecimals, chain, null),
                TokenRef(intent.buySymbol, intent.buySymbol, intent.buyAddress, intent.buyDecimals, chain, null),
                rawToAmount(intent.sellAmountRaw, intent.sellDecimals, intent.sellDecimals),
            )
        }
    }
    var picker by remember { mutableStateOf<PickerSide?>(null) }
    LaunchedEffect(ui.quote) {
        if (selectedQuote?.let { !vm.isCurrent(it) } == true) {
            review = null
            selectedQuote = null
        }
    }

    PhoneFrame {
        BackBar(onBack = { navigator.pop() }, title = "Swap — ${chain.displayName}")

        Spacer(Modifier.height(4.dp))

        SellCard(
            token = ui.sell,
            amount = ui.amountInput,
            onAmountChange = vm::onAmountChange,
            onPickToken = { picker = PickerSide.Sell },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, WalletColors.outline, RoundedCornerShape(14.dp))
                    .clickable { vm.onFlip() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("⇅", color = WalletColors.textPrimary, fontSize = 14.sp)
            }
        }

        BuyCard(
            token = ui.buy,
            quote = ui.quote,
            onPickToken = { picker = PickerSide.Buy },
        )

        Spacer(Modifier.height(12.dp))

        QuoteSummary(ui = ui)

        Spacer(Modifier.height(0.dp).weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            error?.let { Text(it, color = WalletColors.textSecondary, fontSize = 12.sp) }
            PrimaryButton(
                text = if (preparing) "Preparing review…" else "Review",
                enabled = ui.quote is QuoteStatus.Value && !preparing,
                onClick = {
                    val selection = vm.captureQuote() ?: return@PrimaryButton
                    preparing = true
                    error = null
                    scope.launch {
                        try {
                            val prepared = execution.prepareSwap(chain, wallet.address(chain).lowercase(), selection.quote)
                            if (vm.isCurrent(selection)) {
                                selectedQuote = selection
                                review = prepared
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "Could not prepare the review. Check pending operations and try again."
                        } finally {
                            preparing = false
                        }
                    }
                },
            )
        }
    }

    review?.let { prepared ->
        ReviewAndSignSheet(
            review = prepared,
            error = error,
            onDismiss = { review = null; selectedQuote = null },
            onConfirm = {
                val selection = selectedQuote
                if (selection == null || !vm.isCurrent(selection) || session.wallet !== wallet) {
                    review = null
                    error = "Quote changed. Review again."
                } else {
                    try {
                        val id = execution.start(prepared)
                        vm.consumeQuote(selection)
                        review = null
                        navigator.replace(Route.Operation(id))
                    } catch (_: Exception) {
                        error = "Could not start. Close the review and try again."
                    }
                }
            },
        )
    }

    picker?.let { side ->
        TokenPickerSheet(
            chain = chain,
            query = ui.searchQuery,
            results = ui.searchResults,
            onQueryChange = vm::onSearchQueryChange,
            onPick = { token ->
                vm.onPick(side, token)
                picker = null
            },
            onDismiss = {
                vm.onSearchQueryChange("")
                picker = null
            },
        )
    }
}

@Composable
private fun SellCard(
    token: TokenRef,
    amount: String,
    onAmountChange: (String) -> Unit,
    onPickToken: () -> Unit,
) {
    CardShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("You pay", color = WalletColors.textSecondary, fontSize = 11.sp)
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    singleLine = true,
                    placeholder = { Text("0.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            TokenChip(token = token, onClick = onPickToken)
        }
    }
}

@Composable
private fun BuyCard(
    token: TokenRef,
    quote: QuoteStatus,
    onPickToken: () -> Unit,
) {
    CardShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("You receive", color = WalletColors.textSecondary, fontSize = 11.sp)
                val display = when (quote) {
                    is QuoteStatus.Value -> rawToAmount(quote.quote.buyAmountRaw, token.decimals)
                    is QuoteStatus.Loading -> "…"
                    else -> "—"
                }
                Text(
                    text = display,
                    color = WalletColors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TokenChip(token = token, onClick = onPickToken)
        }
    }
}

@Composable
private fun CardShell(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WalletColors.surface)
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun TokenChip(token: TokenRef, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, WalletColors.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "${token.symbol} ▾",
            color = WalletColors.textPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun QuoteSummary(ui: SwapUiState) {
    val line: String = when (val q = ui.quote) {
        is QuoteStatus.Idle -> "Enter an amount to get a quote."
        is QuoteStatus.Loading -> "Fetching quote…"
        is QuoteStatus.Error -> q.userMessage
        is QuoteStatus.Value -> {
            val min = rawToAmount(q.quote.minBuyAmountRaw, ui.buy.decimals)
            "Min received: $min ${ui.buy.symbol} · slippage 1.0%"
        }
    }
    Text(line, color = WalletColors.textSecondary, fontSize = 12.sp)
}

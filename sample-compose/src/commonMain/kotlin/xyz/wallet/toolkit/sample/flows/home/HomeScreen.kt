package xyz.wallet.toolkit.sample.flows.home

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.portfolio.PortfolioChange24h
import xyz.wallet.toolkit.sample.portfolio.PortfolioState
import xyz.wallet.toolkit.sample.portfolio.formatPercent
import xyz.wallet.toolkit.sample.portfolio.formatUsd
import xyz.wallet.toolkit.sample.state.LocalWalletSession
import xyz.wallet.toolkit.sample.state.SecureWalletStorageRuntime
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText
import xyz.wallet.toolkit.sample.ui.PhoneFrame

private val homeChains: List<SupportedChain> =
    listOf(SupportedChain.Ethereum, SupportedChain.Base)

/**
 * Portfolio-style Home screen (design A). Renders:
 *  - address + chain chip (chain chip opens a Ethereum/Base switcher dialog)
 *  - total USD for the selected chain + 24h delta
 *  - Send / Receive / Swap (disabled) / Buy (disabled) action tiles
 *  - per-token Assets list from Zerion
 *  - Sign out
 *
 * Send is enabled only when the selected chain has a loaded portfolio with
 * a non-zero native balance — prevents entering the send flow with no ETH
 * to pay for gas.
 */
@Composable
fun HomeScreen(navigator: Navigator, vm: HomeViewModel = koinInject()) {
    val session = LocalWalletSession.current
    val wallet = session.wallet
    val ui by vm.ui.collectAsState()

    var showReceive by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }
    var showChainPicker by remember { mutableStateOf(false) }

    LaunchedEffect(wallet) {
        if (wallet != null) vm.ensureLoaded(wallet, homeChains)
    }

    PhoneFrame {
        if (wallet == null) {
            MonoText(text = "No wallet")
            OutlinedButton(
                onClick = { navigator.pop() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletColors.textPrimary),
            ) { Text("Back") }
            return@PhoneFrame
        }

        val selectedChain = homeChains.firstOrNull { it.id == ui.selectedChainId }
            ?: SupportedChain.Ethereum
        val portfolio = ui.portfolioFor(ui.selectedChainId)

        TopBar(
            address = wallet.address(SupportedChain.Ethereum).lowercase(),
            chain = selectedChain,
            onChainClick = { showChainPicker = true },
        )

        Spacer(Modifier.height(8.dp))

        TotalBalance(portfolio = portfolio)

        Spacer(Modifier.height(16.dp))

        ActionTiles(
            sendEnabled = portfolio is PortfolioState.Value && hasNativeBalance(portfolio),
            onSend = { navigator.push(Route.Send(chainId = ui.selectedChainId)) },
            onReceive = { showReceive = true },
            onSwap = { navigator.push(Route.Swap(chainId = ui.selectedChainId)) },
        )

        Spacer(Modifier.height(20.dp))

        AssetsHeader(count = (portfolio as? PortfolioState.Value)?.snapshot?.tokens?.size)

        PortfolioList(
            chain = selectedChain,
            entry = portfolio,
            onRetry = { vm.refresh(wallet, selectedChain) },
            modifier = Modifier.weight(1f),
        )

        TextButton(
            onClick = { showSignOut = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign out", color = WalletColors.textSecondary)
        }
    }

    if (showReceive) {
        AlertDialog(
            onDismissRequest = { showReceive = false },
            confirmButton = {
                TextButton(onClick = { showReceive = false }) { Text("OK") }
            },
            title = { Text("Receive") },
            text = { Text("Coming soon") },
        )
    }

    if (showChainPicker) {
        AlertDialog(
            onDismissRequest = { showChainPicker = false },
            confirmButton = {},
            title = { Text("Switch chain") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    homeChains.forEach { chain ->
                        TextButton(
                            onClick = {
                                vm.selectChain(chain.id)
                                showChainPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = chain.displayName,
                                color = WalletColors.textPrimary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
        )
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            confirmButton = {
                TextButton(onClick = {
                    showSignOut = false
                    runCatching { SecureWalletStorageRuntime.get().clear() }
                    vm.clearCache()
                    session.wallet = null
                    session.lastTxHash = null
                    navigator.replace(Route.Welcome)
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOut = false }) { Text("Cancel") }
            },
            title = { Text("Sign out?") },
            text = { Text("Your recovery phrase will be removed from this device. You can restore from backup to return.") },
        )
    }
}

private fun hasNativeBalance(state: PortfolioState.Value): Boolean {
    val native = state.snapshot.native ?: return false
    val numeric = native.quantityDecimal.trim()
    if (numeric.isEmpty()) return false
    return numeric.any { it in '1'..'9' }
}

@Composable
private fun TopBar(
    address: String,
    chain: SupportedChain,
    onChainClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Main wallet",
                color = WalletColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            MonoText(text = shortAddr(address))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, WalletColors.outline, RoundedCornerShape(16.dp))
                .clickable(onClick = onChainClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "◇ ${chain.displayName} ▾",
                color = WalletColors.textPrimary,
                fontSize = 12.sp,
            )
        }
    }
}

private fun shortAddr(address: String): String =
    if (address.length < 10) address else "${address.take(6)}…${address.takeLast(4)}"

@Composable
private fun TotalBalance(portfolio: PortfolioState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "TOTAL BALANCE",
            color = WalletColors.textSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(4.dp))
        val (amount, delta) = when (portfolio) {
            is PortfolioState.Loading -> "…" to null
            is PortfolioState.Error -> "—" to null
            is PortfolioState.Value -> formatUsd(portfolio.snapshot.totalUsd) to portfolio.snapshot.change24h
        }
        Text(
            text = amount,
            color = WalletColors.textPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (delta != null) {
            DeltaLabel(delta)
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeltaLabel(delta: PortfolioChange24h) {
    val positive = delta.absoluteUsd >= 0
    val amount = formatUsd(delta.absoluteUsd)
    val signedAmount = if (positive && !amount.startsWith("-")) "+$amount" else amount
    Text(
        text = "$signedAmount · ${formatPercent(delta.percent)} today",
        color = if (positive) WalletColors.accent else WalletColors.textSecondary,
        fontSize = 12.sp,
    )
}

@Composable
private fun ActionTiles(
    sendEnabled: Boolean,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onSwap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionTile(
            label = "Send",
            modifier = Modifier.weight(1f),
            enabled = sendEnabled,
            filled = true,
            onClick = onSend,
        )
        ActionTile(
            label = "Receive",
            modifier = Modifier.weight(1f),
            onClick = onReceive,
        )
        ActionTile(
            label = "Swap",
            modifier = Modifier.weight(1f),
            onClick = onSwap,
        )
        ActionTile(label = "Buy", modifier = Modifier.weight(1f), enabled = false)
    }
}

@Composable
private fun ActionTile(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (filled && enabled) WalletColors.accent else WalletColors.surface
    val fg = when {
        !enabled -> WalletColors.textSecondary
        filled -> androidx.compose.ui.graphics.Color.White
        else -> WalletColors.textPrimary
    }
    val base = modifier
        .clip(shape)
        .background(bg)
        .then(if (filled && enabled) Modifier else Modifier.border(1.dp, WalletColors.outline, shape))
    val withClick = if (enabled && onClick != null) base.clickable(onClick = onClick) else base
    Box(
        modifier = withClick.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AssetsHeader(count: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Assets",
            color = WalletColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        if (count != null) {
            Text(text = count.toString(), color = WalletColors.textSecondary, fontSize = 11.sp)
        }
    }
}

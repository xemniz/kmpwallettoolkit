package xyz.wallet.toolkit.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.core.ChainRegistry
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSampleApp() {
    var mnemonic by remember { mutableStateOf("Tap 'Create Wallet' to start") }
    var address by remember { mutableStateOf("Address will appear here") }
    var status by remember { mutableStateOf("Idle") }
    var chainInfo by remember { mutableStateOf("") }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("KMP Wallet Toolkit") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.padding(top = 4.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "🔑 Wallet Creation", style = MaterialTheme.typography.titleMedium)

                        Button(
                            onClick = {
                                println("WalletSample: Create Wallet clicked")
                                status = "Creating wallet..."
                                try {
                                    val wallet = Wallet.createWithTrustWalletCore()
                                    val derivedAddress = wallet.address(SupportedChain.Ethereum)
                                    mnemonic = wallet.mnemonic
                                    address = derivedAddress
                                    status = "Success"
                                    println("WalletSample: Wallet created OK")
                                } catch (e: Throwable) {
                                    println("WalletSample: Wallet creation failed: ${e.message}")
                                    mnemonic = "-"
                                    address = "-"
                                    status = "Failed: ${e.message ?: e::class.simpleName}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Create Wallet + Derive Ethereum Address")
                        }

                        Text(text = "Status: $status")
                        HorizontalDivider()
                        Text(text = "Mnemonic:", style = MaterialTheme.typography.labelLarge)
                        Text(text = mnemonic, fontFamily = FontFamily.Monospace)
                        Text(text = "Address:", style = MaterialTheme.typography.labelLarge)
                        Text(text = address, fontFamily = FontFamily.Monospace)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "⛓️ Chain Registry", style = MaterialTheme.typography.titleMedium)

                        OutlinedButton(
                            onClick = {
                                println("WalletSample: List All Chains clicked")
                                chainInfo = ChainRegistry.all().joinToString("\n") { chain ->
                                    "${chain.displayName}  (id=${chain.id}, ticker=${chain.ticker})"
                                }
                                println("WalletSample: chainInfo = $chainInfo")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("List All Chains")
                        }

                        if (chainInfo.isNotEmpty()) {
                            Text(text = chainInfo, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

package xyz.wallet.toolkit.sample.flows.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.theme.WalletColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenPickerSheet(
    chain: SupportedChain,
    query: String,
    results: SearchStatus,
    onQueryChange: (String) -> Unit,
    onPick: (TokenRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WalletColors.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Select token",
                color = WalletColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search by name or symbol") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )

            val defaults = DefaultTokens.forChain(chain)
            val tokens: List<TokenRef> = when {
                query.isBlank() -> defaults
                results is SearchStatus.Value -> results.tokens
                else -> emptyList()
            }

            val statusLine: String? = when {
                query.isBlank() -> null
                results is SearchStatus.Loading -> "Searching…"
                results is SearchStatus.Error -> results.userMessage
                results is SearchStatus.Value && results.tokens.isEmpty() -> "No matches."
                else -> null
            }
            if (statusLine != null) {
                Text(statusLine, color = WalletColors.textSecondary, fontSize = 12.sp)
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(tokens, key = { t -> (t.address ?: "native") + "@" + t.chain.id }) { token ->
                    TokenRow(token = token, onClick = { onPick(token) })
                }
            }
        }
    }
}

@Composable
private fun TokenRow(token: TokenRef, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WalletColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = token.symbol.take(3),
            color = WalletColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(token.symbol, color = WalletColors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(token.name, color = WalletColors.textSecondary, fontSize = 11.sp, maxLines = 1)
        }
    }
}

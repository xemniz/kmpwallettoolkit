package xyz.wallet.toolkit.sample.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.wallet.toolkit.sample.theme.WalletColors

/**
 * Header row with a back chevron on the left and an optional centered title.
 * Flow screens use it to expose a visible back affordance — system back is
 * not wired for the common source set, so the chevron is the canonical way.
 */
@Composable
fun BackBar(
    onBack: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                color = WalletColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (title != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = title,
                color = WalletColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

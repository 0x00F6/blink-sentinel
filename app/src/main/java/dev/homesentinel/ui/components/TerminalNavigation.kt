package dev.homesentinel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.homesentinel.R

@Composable
fun TerminalNavigation(selectedTab: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val separator = colors.outlineVariant
    Surface(
        color = colors.surfaceContainerLowest,
        modifier = Modifier.drawWithContent {
            drawContent()
            drawLine(separator, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        },
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val icons = listOf(R.drawable.ic_home, R.drawable.ic_network, R.drawable.ic_blink, R.drawable.ic_event_log)
            listOf("Home", "Network", "Blink", "Event log").forEachIndexed { index, label ->
                val selected = index == selectedTab
                val accent = if (selected) colors.primary else colors.primary.copy(alpha = 0.65f)
                Column(
                    Modifier.weight(1f).selectable(selected, role = Role.Tab, onClick = { onSelect(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = if (selected) colors.primaryContainer else colors.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, if (selected) colors.primary.copy(alpha = 0.6f) else colors.outlineVariant),
                    ) {
                        Icon(
                            painterResource(icons[index]),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(12.dp).size(24.dp),
                        )
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
                }
            }
        }
    }
}

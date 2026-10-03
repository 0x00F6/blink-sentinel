package dev.homesentinel.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.homesentinel.ui.theme.LocalTerminalTheme

/** Static circuit grid: cached paths, no animation, polling, or background work. */
@Composable
fun Modifier.terminalBackdrop(): Modifier {
    if (!LocalTerminalTheme.current) return this
    val gridColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
    return drawWithCache {
        val spacing = 32.dp.toPx()
        val grid =
            Path().apply {
                var x = spacing
                while (x < size.width) {
                    moveTo(x, 0f)
                    lineTo(x, size.height)
                    x += spacing
                }
                var y = spacing
                while (y < size.height) {
                    moveTo(0f, y)
                    lineTo(size.width, y)
                    y += spacing
                }
            }
        val stroke = Stroke(width = 0.5.dp.toPx())
        onDrawBehind { drawPath(grid, gridColor, style = stroke) }
    }
}

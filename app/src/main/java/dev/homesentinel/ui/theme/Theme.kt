package dev.homesentinel.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light =
    lightColorScheme(
        primary = Color(0xFF007B75),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6F5EE),
        onPrimaryContainer = Color(0xFF003D39),
        secondary = Color(0xFF264865),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0EBF4),
        onSecondaryContainer = Color(0xFF143149),
        tertiary = Color(0xFF856116),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFEDC8),
        onTertiaryContainer = Color(0xFF503B08),
        background = Color(0xFFF4F7FB),
        onBackground = Color(0xFF142B42),
        surface = Color.White,
        onSurface = Color(0xFF142B42),
        surfaceVariant = Color(0xFFE7EEF4),
        onSurfaceVariant = Color(0xFF526575),
        surfaceDim = Color(0xFFE2E9F0),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF9FBFD),
        surfaceContainer = Color(0xFFF0F5F9),
        surfaceContainerHigh = Color(0xFFE7EEF4),
        surfaceContainerHighest = Color(0xFFDFE8F0),
        outline = Color(0xFF748590),
        outlineVariant = Color(0xFFDCE5ED),
        error = Color(0xFFBA3038),
        onError = Color.White,
        errorContainer = Color(0xFFFFE4E5),
        onErrorContainer = Color(0xFF7D1821),
        inverseSurface = Color(0xFF19334A),
        inverseOnSurface = Color(0xFFF1F6FA),
        inversePrimary = Color(0xFF64DEC9),
        surfaceTint = Color(0xFF007B75),
    )

// Terminal-inspired colors from the visual reference; no dynamic wallpaper colors override them.
private val Dark =
    darkColorScheme(
        primary = Color(0xFF00FF88),
        onPrimary = Color(0xFF002013),
        primaryContainer = Color(0xFF06291C),
        onPrimaryContainer = Color(0xFF77FFBB),
        secondary = Color(0xFF00D1FF),
        onSecondary = Color(0xFF00232A),
        secondaryContainer = Color(0xFF0A1B1C),
        onSecondaryContainer = Color(0xFFC0F1ED),
        tertiary = Color(0xFFFFD36A),
        onTertiary = Color(0xFF322500),
        tertiaryContainer = Color(0xFF231C0C),
        onTertiaryContainer = Color(0xFFFFDF91),
        background = Color(0xFF0A0A0A),
        onBackground = Color(0xFFE0EDE7),
        surface = Color(0xFF0B1210),
        onSurface = Color(0xFFE0EDE7),
        surfaceVariant = Color(0xFF1A2620),
        onSurfaceVariant = Color(0xFFA2B6AA),
        surfaceDim = Color(0xFF0A0A0A),
        surfaceBright = Color(0xFF23332A),
        surfaceContainerLowest = Color(0xFF050907),
        surfaceContainerLow = Color(0xFF0B1410),
        surfaceContainer = Color(0xFF101C16),
        surfaceContainerHigh = Color(0xFF192820),
        surfaceContainerHighest = Color(0xFF22372B),
        outline = Color(0xFF348A61),
        outlineVariant = Color(0xFF17452F),
        error = Color(0xFFFF7B89),
        onError = Color(0xFF480916),
        errorContainer = Color(0xFF35121C),
        onErrorContainer = Color(0xFFFFBAC3),
        inverseSurface = Color(0xFFE0EDE7),
        inverseOnSurface = Color(0xFF13251B),
        inversePrimary = Color(0xFF007B75),
        surfaceTint = Color(0xFF00FF88),
    )

val LocalTerminalTheme = staticCompositionLocalOf { false }

private val SentinelTypography =
    Typography(
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.6).sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
    )

private val SentinelShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

private val TerminalTypography =
    SentinelTypography.copy(
        headlineSmall =
            SentinelTypography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineMedium =
            SentinelTypography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.5).sp,
            ),
        titleLarge = SentinelTypography.titleLarge.copy(fontFamily = FontFamily.Monospace),
        titleMedium =
            SentinelTypography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        labelLarge = SentinelTypography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        labelMedium = SentinelTypography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        labelSmall = SentinelTypography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
    )

private val TerminalShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(10.dp),
        large = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

@Composable
fun BlinkSentinelTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTerminalTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) Dark else Light,
            typography = if (darkTheme) TerminalTypography else SentinelTypography,
            shapes = if (darkTheme) TerminalShapes else SentinelShapes,
            content = content,
        )
    }
}

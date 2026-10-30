package com.metronomo.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF080A0D)
val Surface = Color(0xFF12161C)
val SurfaceHigh = Color(0xFF19202A)
val SurfaceHigher = Color(0xFF212935)

val Amber = Color(0xFFFFC24B)
val AmberSoft = Color(0xFFFFE2A3)
val AmberDeep = Color(0xFF8A5B00)
val Teal = Color(0xFF6FE3C9)
val TextPrimary = Color(0xFFEEF2F7)
val TextMuted = Color(0xFF8B98A9)
val Hairline = Color(0xFF232B36)
val Danger = Color(0xFFFF7A6B)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241804),
    primaryContainer = Color(0xFF3B2A08),
    onPrimaryContainer = AmberSoft,
    secondary = Teal,
    onSecondary = Color(0xFF03211B),
    secondaryContainer = Color(0xFF0E332C),
    onSecondaryContainer = Teal,
    tertiary = AmberSoft,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMuted,
    surfaceContainer = SurfaceHigh,
    surfaceContainerHigh = SurfaceHigher,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Danger,
    onError = Color(0xFF2A0603),
)

/** Tabular/monospaced numerals so the displays never wobble. */
val NumeralStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.sp,
)

private val AppTypography = Typography(
    displayLarge = NumeralStyle.copy(fontSize = 64.sp, lineHeight = 68.sp),
    displayMedium = NumeralStyle.copy(fontSize = 44.sp, lineHeight = 48.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MetronomoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

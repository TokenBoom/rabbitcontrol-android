package com.rabbitcontrol.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbitcontrol.android.ui.components.ProvideRabbitTextToolbar

object RabbitSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
}

object RabbitRadius {
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Pill = 999.dp
}

object RabbitElevation {
    val None = 0.dp
    val Card = 0.dp
    val Floating = 2.dp
    val Sheet = 2.dp
}

@Immutable
data class RabbitPalette(
    val isDark: Boolean,
    val primary: Color,
    val primaryText: Color,
    val primarySoft: Color,
    val canvas: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val field: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val muted: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val heroDarkBg: Color,
    val heroCardBg: Color,
)

object SessionPalette {
    val canvas = Color(0xFF000000)
    val chrome = Color(0xFF121212)
    val chromeElevated = Color(0xFF1A1A1A)
    val surface = Color(0xFF222222)
    val surfaceActive = Color(0xFF2E2E2E)
    val trackpad = Color(0xFF0C0C0C)
    val border = Color(0xFF2B2B2B)
    val borderSubtle = Color(0x26FFFFFF)
    val text = Color(0xFFF5F5F5)
    val muted = Color(0xFFA3A3A3)
    val accent = Color(0xFF3370FF)
    val accentText = Color(0xFF6B9BFF)
    val danger = Color(0xFFC9303B)
    val dangerSoft = Color(0xFF3A1B20)
    val dangerText = Color(0xFFFF8C94)
    val success = Color(0xFF22C55E)
    val warning = Color(0xFFF59E0B)
    val scrim = Color(0x99000000)
}

private val LightPalette = RabbitPalette(
    isDark = false,
    primary = Color(0xFF3370FF),
    primaryText = Color(0xFF285EDB),
    primarySoft = Color(0xFFEEF3FF),
    canvas = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFFAFAFA),
    field = Color(0xFFF5F5F5),
    line = Color(0xFFE5E5E5),
    lineStrong = Color(0xFFD4D4D4),
    text = Color(0xFF171717),
    muted = Color(0xFF737373),
    success = Color(0xFF156B41),
    successSoft = Color(0xFFE7F6EE),
    warning = Color(0xFFB45309),
    warningSoft = Color(0xFFFEF3C7),
    danger = Color(0xFFB92530),
    dangerSoft = Color(0xFFFCECEE),
    heroDarkBg = Color(0xFF141414),
    heroCardBg = Color(0xFF1F1F1F),
)

private val DarkPalette = RabbitPalette(
    isDark = true,
    primary = Color(0xFF3370FF),
    primaryText = Color(0xFF6B9BFF),
    primarySoft = Color(0xFF262626),
    canvas = Color(0xFF0A0A0A),
    surface = Color(0xFF161616),
    surfaceSubtle = Color(0xFF1C1C1C),
    field = Color(0xFF222222),
    line = Color(0xFF292929),
    lineStrong = Color(0xFF3A3A3A),
    text = Color(0xFFF5F5F5),
    muted = Color(0xFF9E9E9E),
    success = Color(0xFF6EE7B7),
    successSoft = Color(0xFF133126),
    warning = Color(0xFFFBBF24),
    warningSoft = Color(0xFF332510),
    danger = Color(0xFFFF8E96),
    dangerSoft = Color(0xFF3A1A20),
    heroDarkBg = Color(0xFF0A0A0A),
    heroCardBg = Color(0xFF161616),
)

val LocalRabbitPalette = staticCompositionLocalOf { LightPalette }

private val LightScheme = lightColorScheme(
    primary = LightPalette.primary,
    onPrimary = Color.White,
    primaryContainer = LightPalette.primarySoft,
    onPrimaryContainer = LightPalette.primaryText,
    secondaryContainer = LightPalette.primarySoft,
    onSecondaryContainer = LightPalette.text,
    background = LightPalette.canvas,
    onBackground = LightPalette.text,
    surface = LightPalette.surface,
    onSurface = LightPalette.text,
    surfaceVariant = LightPalette.field,
    onSurfaceVariant = LightPalette.muted,
    surfaceContainerLowest = LightPalette.surface,
    surfaceContainerLow = LightPalette.surfaceSubtle,
    surfaceContainer = LightPalette.surface,
    surfaceContainerHigh = LightPalette.field,
    surfaceContainerHighest = LightPalette.line,
    outline = LightPalette.line,
    outlineVariant = LightPalette.lineStrong,
    error = LightPalette.danger,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.primary,
    onPrimary = Color.White,
    primaryContainer = DarkPalette.primarySoft,
    onPrimaryContainer = DarkPalette.primaryText,
    secondaryContainer = DarkPalette.primarySoft,
    onSecondaryContainer = DarkPalette.text,
    background = DarkPalette.canvas,
    onBackground = DarkPalette.text,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.text,
    surfaceVariant = DarkPalette.field,
    onSurfaceVariant = DarkPalette.muted,
    surfaceContainerLowest = DarkPalette.canvas,
    surfaceContainerLow = DarkPalette.surface,
    surfaceContainer = DarkPalette.surface,
    surfaceContainerHigh = DarkPalette.surfaceSubtle,
    surfaceContainerHighest = DarkPalette.field,
    outline = DarkPalette.line,
    outlineVariant = DarkPalette.lineStrong,
    error = DarkPalette.danger,
    onError = Color(0xFF3A1A20),
)

private val RabbitTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
    ),
)

@Composable
fun RabbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val selectionColors = TextSelectionColors(
        handleColor = palette.primary,
        backgroundColor = palette.primary.copy(alpha = 0.22f),
    )
    CompositionLocalProvider(
        LocalRabbitPalette provides palette,
        LocalTextSelectionColors provides selectionColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = RabbitTypography,
        ) {
            ProvideRabbitTextToolbar(content = content)
        }
    }
}

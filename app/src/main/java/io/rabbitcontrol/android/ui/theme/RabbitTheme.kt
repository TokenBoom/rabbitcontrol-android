package io.rabbitcontrol.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class RabbitPalette(
    val primary: Color,
    val primarySoft: Color,
    val canvas: Color,
    val surface: Color,
    val field: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val success: Color,
    val successSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val heroDarkBg: Color,
    val heroCardBg: Color,
)

private val LightPalette = RabbitPalette(
    primary = Color(0xFF3370FF),
    primarySoft = Color(0xFFEBF1FF),
    canvas = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    field = Color(0xFFF7F8FA),
    line = Color(0xFFD9DFE8),
    text = Color(0xFF1D2129),
    muted = Color(0xFF505E70),
    success = Color(0xFF17623D),
    successSoft = Color(0xFFE7F6EE),
    danger = Color(0xFFA82932),
    dangerSoft = Color(0xFFFCECEE),
    heroDarkBg = Color(0xFF1C2029),
    heroCardBg = Color(0xFF232833),
)

private val DarkPalette = RabbitPalette(
    primary = Color(0xFF3370FF),
    primarySoft = Color(0xFF1E2D4A),
    canvas = Color(0xFF11151C),
    surface = Color(0xFF1A202A),
    field = Color(0xFF222A36),
    line = Color(0xFF333D4D),
    text = Color(0xFFF1F4FA),
    muted = Color(0xFFB8C4D5),
    success = Color(0xFF88DEB0),
    successSoft = Color(0xFF163325),
    danger = Color(0xFFFFA8AD),
    dangerSoft = Color(0xFF3B1B1F),
    heroDarkBg = Color(0xFF161A22),
    heroCardBg = Color(0xFF1E232D),
)

val LocalRabbitPalette = staticCompositionLocalOf { LightPalette }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3370FF),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFEBF1FF),
    onSecondaryContainer = Color(0xFF102449),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1D2129),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D2129),
    surfaceVariant = Color(0xFFF7F8FA),
    onSurfaceVariant = Color(0xFF505E70),
    outline = Color(0xFFD9DFE8),
    error = Color(0xFFA82932),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3370FF),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFF283952),
    onSecondaryContainer = Color(0xFFF1F4FA),
    background = Color(0xFF11151C),
    onBackground = Color(0xFFF1F4FA),
    surface = Color(0xFF1A202A),
    onSurface = Color(0xFFF1F4FA),
    surfaceVariant = Color(0xFF222A36),
    onSurfaceVariant = Color(0xFFB8C4D5),
    outline = Color(0xFF414C5D),
    error = Color(0xFFFFA8AD),
)

private val RabbitTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif),
)

@Composable
fun RabbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalRabbitPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = RabbitTypography,
            content = content,
        )
    }
}

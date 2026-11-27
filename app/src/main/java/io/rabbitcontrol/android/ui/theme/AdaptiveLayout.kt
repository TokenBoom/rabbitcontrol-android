package io.rabbitcontrol.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class RabbitWidthClass {
    Compact,
    Medium,
    Expanded,
}

@Immutable
data class RabbitLayoutInfo(
    val widthClass: RabbitWidthClass,
    val isTabletDevice: Boolean,
)

fun widthClassFor(widthDp: Dp): RabbitWidthClass = when {
    widthDp < 600.dp -> RabbitWidthClass.Compact
    widthDp < 840.dp -> RabbitWidthClass.Medium
    else -> RabbitWidthClass.Expanded
}

@Composable
fun rememberRabbitLayoutInfo(maxWidth: Dp): RabbitLayoutInfo {
    val config = LocalConfiguration.current
    val isTabletDevice = config.smallestScreenWidthDp >= 600
    val widthClass = widthClassFor(maxWidth)
    return remember(widthClass, isTabletDevice) {
        RabbitLayoutInfo(
            widthClass = widthClass,
            isTabletDevice = isTabletDevice,
        )
    }
}

@Composable
fun RabbitVerticalDivider(modifier: Modifier = Modifier) {
    val palette = LocalRabbitPalette.current
    Box(
        modifier = modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(palette.line),
    )
}

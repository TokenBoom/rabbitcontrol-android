package com.rabbitcontrol.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import com.rabbitcontrol.android.ui.theme.RabbitElevation
import com.rabbitcontrol.android.ui.theme.RabbitRadius
import com.rabbitcontrol.android.ui.theme.RabbitSpacing
import com.rabbitcontrol.android.ui.theme.SessionPalette

@Composable
fun RabbitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = LocalRabbitPalette.current.surface,
    borderColor: Color = LocalRabbitPalette.current.line,
    shape: Shape = RoundedCornerShape(RabbitRadius.Lg),
    contentPadding: PaddingValues = PaddingValues(RabbitSpacing.Lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = if (onClick != null) {
        modifier.clip(shape).clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = cardModifier,
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = RabbitElevation.Card,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun RabbitTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    contentColor: Color = LocalRabbitPalette.current.text,
    mutedColor: Color = LocalRabbitPalette.current.muted,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = RabbitSpacing.Sm, vertical = RabbitSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = contentColor,
                )
            }
        } else {
            Spacer(modifier = Modifier.width(RabbitSpacing.Sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RabbitSpacing.Xs),
            content = actions,
        )
    }
}

@Composable
fun <T> RabbitSegmentedControl(
    options: List<T>,
    selected: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = LocalRabbitPalette.current.field,
    trackBorderColor: Color = LocalRabbitPalette.current.line,
    activeSurfaceColor: Color = LocalRabbitPalette.current.surface,
    activeBorderColor: Color = LocalRabbitPalette.current.lineStrong,
    activeTextColor: Color = LocalRabbitPalette.current.primaryText,
    inactiveTextColor: Color = LocalRabbitPalette.current.muted,
) {
    val trackShape = RoundedCornerShape(RabbitRadius.Md)
    val itemShape = RoundedCornerShape(9.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(trackColor)
            .border(1.dp, trackBorderColor, trackShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(itemShape)
                    .background(if (isSelected) activeSurfaceColor else Color.Transparent)
                    .then(
                        if (isSelected) {
                            Modifier.border(1.dp, activeBorderColor, itemShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = RabbitSpacing.Sm, vertical = RabbitSpacing.Sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelProvider(option),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) activeTextColor else inactiveTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

enum class StatusTone { Success, Primary, Warning, Danger, Muted }

@Composable
fun RabbitStatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val palette = LocalRabbitPalette.current
    val (bg, fg) = when (tone) {
        StatusTone.Success -> palette.successSoft to palette.success
        StatusTone.Primary -> palette.primarySoft to palette.primaryText
        StatusTone.Warning -> palette.warningSoft to palette.warning
        StatusTone.Danger -> palette.dangerSoft to palette.danger
        StatusTone.Muted -> palette.field to palette.muted
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(RabbitRadius.Pill))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(fg),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

@Composable
fun RabbitPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    height: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(RabbitRadius.Md),
    containerColor: Color = LocalRabbitPalette.current.primary,
) {
    val palette = LocalRabbitPalette.current
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = shape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = palette.line,
            disabledContentColor = palette.muted,
        ),
        modifier = modifier.height(height),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(RabbitSpacing.Sm))
        }
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun RabbitSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    textColor: Color = LocalRabbitPalette.current.text,
    borderColor: Color = LocalRabbitPalette.current.lineStrong,
    shape: Shape = RoundedCornerShape(RabbitRadius.Md),
) {
    val palette = LocalRabbitPalette.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = palette.surface,
            contentColor = textColor,
        ),
        modifier = modifier.height(height),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun RabbitDangerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    icon: ImageVector? = null,
) {
    val palette = LocalRabbitPalette.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(RabbitRadius.Md),
        border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = palette.dangerSoft,
            contentColor = palette.danger,
        ),
        modifier = modifier.height(height),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(RabbitSpacing.Sm))
        }
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun RabbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation? = null,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val palette = LocalRabbitPalette.current
    var passwordVisible by remember(isPassword) { mutableStateOf(false) }
    val effectiveTrailingIcon: (@Composable () -> Unit)? = when {
        trailingIcon != null -> trailingIcon
        isPassword -> {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = if (passwordVisible) {
                            stringResource(R.string.common_hide_password)
                        } else {
                            stringResource(R.string.common_show_password)
                        },
                        tint = if (passwordVisible) palette.primaryText else palette.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        else -> null
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, color = palette.muted, fontSize = 14.sp)
            },
            leadingIcon = leadingIcon,
            trailingIcon = effectiveTrailingIcon,
            singleLine = singleLine,
            textStyle = textStyle,
            visualTransformation = visualTransformation
                ?: if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions ?: KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            ),
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(RabbitRadius.Md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedContainerColor = if (palette.isDark) palette.surfaceSubtle else Color.White,
                unfocusedContainerColor = if (palette.isDark) palette.field else Color(0xFFFAFAFA),
                focusedBorderColor = palette.primary,
                unfocusedBorderColor = palette.lineStrong,
                cursorColor = palette.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun RabbitSwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = palette.muted)
            }
        }
        Spacer(modifier = Modifier.width(RabbitSpacing.Md))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.primary,
                uncheckedThumbColor = palette.muted,
                uncheckedTrackColor = palette.field,
                uncheckedBorderColor = palette.lineStrong,
            ),
        )
    }
}

@Composable
fun RabbitEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = LocalRabbitPalette.current
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        RabbitCard(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RabbitSpacing.Sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(RabbitRadius.Md))
                        .background(palette.primarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = palette.primaryText, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(RabbitSpacing.Md))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(6.dp))
                Text(description, fontSize = 13.sp, color = palette.muted, textAlign = TextAlign.Center)
                if (!actionLabel.isNullOrBlank() && onAction != null) {
                    Spacer(modifier = Modifier.height(RabbitSpacing.Lg))
                    RabbitSecondaryButton(
                        label = actionLabel,
                        onClick = onAction,
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun RabbitSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = RabbitSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = palette.muted,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun RabbitDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    message: String? = null,
    dismissLabel: String? = null,
    onDismissAction: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    darkSessionStyle: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val palette = LocalRabbitPalette.current
    val surfaceColor = if (darkSessionStyle) SessionPalette.chromeElevated else palette.surface
    val borderColor = if (darkSessionStyle) SessionPalette.border else palette.line
    val titleColor = if (darkSessionStyle) SessionPalette.text else palette.text
    val bodyColor = if (darkSessionStyle) SessionPalette.muted else palette.muted
    val fieldColor = if (darkSessionStyle) SessionPalette.surface else palette.field
    val primaryColor = if (darkSessionStyle) SessionPalette.accent else palette.primary
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp
    val isTablet = config.smallestScreenWidthDp >= 600
    val maxDialogWidth = when {
        darkSessionStyle && isLandscape -> 340.dp
        isTablet -> 440.dp
        else -> 360.dp
    }
    val contentPad = if (isLandscape) 16.dp else 20.dp
    val buttonHeight = if (isLandscape) 40.dp else 44.dp

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = if (isLandscape) 12.dp else 24.dp)
                .widthIn(max = maxDialogWidth)
                .fillMaxWidth(),
            shape = RoundedCornerShape(RabbitRadius.Xl),
            color = surfaceColor,
            tonalElevation = 0.dp,
            shadowElevation = RabbitElevation.Floating,
            border = BorderStroke(1.dp, borderColor),
        ) {
            ProvideRabbitTextToolbar {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPad),
                ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 8.dp))
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = bodyColor,
                    )
                }
                if (content != null) {
                    Spacer(modifier = Modifier.height(if (!message.isNullOrBlank()) (if (isLandscape) 10.dp else 12.dp) else 10.dp))
                    content()
                }
                Spacer(modifier = Modifier.height(if (isLandscape) 14.dp else 20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (dismissLabel != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .clip(RoundedCornerShape(RabbitRadius.Md))
                                .background(fieldColor)
                                .border(1.dp, borderColor, RoundedCornerShape(RabbitRadius.Md))
                                .clickable { (onDismissAction ?: onDismissRequest)() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = dismissLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = titleColor,
                            )
                        }
                    }
                    val confirmContainer = if (destructive) Color(0xFFD1343F) else primaryColor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight)
                            .clip(RoundedCornerShape(RabbitRadius.Md))
                            .alpha(if (confirmEnabled) 1f else 0.42f)
                            .background(confirmContainer)
                            .clickable(enabled = confirmEnabled, onClick = onConfirm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = confirmLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RabbitAdaptiveSheet(
    onDismissRequest: () -> Unit,
    maxDialogWidth: Dp = 480.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val useCenteredDialog = LocalConfiguration.current.screenWidthDp >= 600
    if (!useCenteredDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = palette.surface,
            tonalElevation = 0.dp,
            content = content,
        )
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = maxDialogWidth)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(RabbitRadius.Xl),
                color = palette.surface,
                tonalElevation = 0.dp,
                shadowElevation = RabbitElevation.Sheet,
                border = BorderStroke(1.dp, palette.line),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = RabbitSpacing.Lg),
                    content = content,
                )
            }
        }
    }
}

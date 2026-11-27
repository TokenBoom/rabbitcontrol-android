package io.rabbitcontrol.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AppLockStore
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitRadius

fun canUseBiometricUnlock(context: android.content.Context): Boolean {
    val res = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK,
    )
    return res == BiometricManager.BIOMETRIC_SUCCESS
}

@Composable
fun AppLockOverlay(
    appLockStore: AppLockStore,
) {
    val state by appLockStore.state.collectAsStateWithLifecycle()
    if (!state.enabled || !state.locked) return

    BackHandler(enabled = true) {}

    val palette = LocalRabbitPalette.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val bioReady = remember(state.biometricEnabled, context) {
        state.biometricEnabled && activity != null && canUseBiometricUnlock(context)
    }

    var pin by remember(state.locked) { mutableStateOf("") }
    var wrongPin by remember(state.locked) { mutableStateOf(false) }
    var bioAutoTriggered by remember(state.locked) { mutableStateOf(false) }

    val launchBiometric: () -> Unit = remember(activity, bioReady) {
        {
            if (bioReady && activity != null) {
                val executor = ContextCompat.getMainExecutor(activity)
                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            appLockStore.unlockViaBiometric()
                        }
                    },
                )
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(AppStrings.get(R.string.app_lock_bio_prompt_title, "验证身份解锁"))
                    .setSubtitle(AppStrings.get(R.string.app_lock_bio_prompt_sub, "使用指纹或面容解锁 RabbitControl"))
                    .setNegativeButtonText(AppStrings.get(R.string.app_lock_use_pin, "使用密码解锁"))
                    .build()
                runCatching { prompt.authenticate(info) }
            }
        }
    }

    LaunchedEffect(state.locked, bioReady) {
        if (state.locked && bioReady && !bioAutoTriggered) {
            bioAutoTriggered = true
            launchBiometric()
        }
    }

    fun submitDigit(d: String) {
        if (pin.length >= 6) return
        wrongPin = false
        val next = pin + d
        pin = next
        if (next.length == state.pinLength) {
            if (!appLockStore.verifyPin(next)) {
                wrongPin = true
                pin = ""
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = palette.canvas,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = palette.primaryText,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.app_lock_overlay_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_lock_overlay_sub, state.pinLength),
                fontSize = 13.sp,
                color = palette.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(state.pinLength) { idx ->
                    val filled = idx < pin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    wrongPin -> palette.danger
                                    filled -> palette.primary
                                    else -> Color.Transparent
                                },
                            )
                            .border(
                                width = 1.5.dp,
                                color = when {
                                    wrongPin -> palette.danger
                                    filled -> palette.primary
                                    else -> palette.lineStrong
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (wrongPin) stringResource(R.string.app_lock_wrong_pin) else " ",
                fontSize = 12.sp,
                color = palette.danger,
            )
            Spacer(modifier = Modifier.height(16.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("bio", "0", "del"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { key ->
                            when (key) {
                                "bio" -> {
                                    if (bioReady) {
                                        PinPadKeyButton(onClick = launchBiometric) {
                                            Icon(
                                                imageVector = Icons.Rounded.Fingerprint,
                                                contentDescription = stringResource(R.string.app_lock_bio_btn),
                                                tint = palette.primaryText,
                                                modifier = Modifier.size(26.dp),
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(68.dp))
                                    }
                                }
                                "del" -> {
                                    PinPadKeyButton(
                                        onClick = {
                                            if (pin.isNotEmpty()) {
                                                pin = pin.dropLast(1)
                                            }
                                            wrongPin = false
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                            contentDescription = stringResource(R.string.common_delete),
                                            tint = palette.text,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                else -> {
                                    PinPadKeyButton(onClick = { submitDigit(key) }) {
                                        Text(
                                            text = key,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = palette.text,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (bioReady) {
                Spacer(modifier = Modifier.height(14.dp))
                TextButton(onClick = launchBiometric) {
                    Icon(Icons.Rounded.Fingerprint, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.app_lock_bio_btn), color = palette.primaryText, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PinPadKeyButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Surface(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = palette.surface,
        border = BorderStroke(1.dp, palette.line),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

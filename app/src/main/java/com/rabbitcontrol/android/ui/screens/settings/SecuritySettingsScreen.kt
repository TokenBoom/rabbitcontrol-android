package com.rabbitcontrol.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.data.AccountRepository
import com.rabbitcontrol.android.core.data.AppLockStore
import com.rabbitcontrol.android.core.data.InteractionPrefsStore
import com.rabbitcontrol.android.core.error.RcUiErrorCodec
import com.rabbitcontrol.android.core.i18n.AppStrings
import com.rabbitcontrol.android.core.nativebridge.DeviceCertStatusModel
import com.rabbitcontrol.android.ui.components.RabbitCard
import com.rabbitcontrol.android.ui.components.RabbitDialog
import com.rabbitcontrol.android.ui.components.RabbitPrimaryButton
import com.rabbitcontrol.android.ui.components.RabbitSecondaryButton
import com.rabbitcontrol.android.ui.components.RabbitSegmentedControl
import com.rabbitcontrol.android.ui.components.RabbitStatusPill
import com.rabbitcontrol.android.ui.components.RabbitSwitchRow
import com.rabbitcontrol.android.ui.components.RabbitTextField
import com.rabbitcontrol.android.ui.components.RabbitToast
import com.rabbitcontrol.android.ui.components.RabbitTopBar
import com.rabbitcontrol.android.ui.components.StatusTone
import com.rabbitcontrol.android.ui.components.canUseBiometricUnlock
import com.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import com.rabbitcontrol.android.ui.theme.RabbitRadius
import com.rabbitcontrol.android.ui.theme.RabbitSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PinDialogMode {
    SetPin,
    VerifyToDisable,
    VerifyToChange,
}

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    val palette = LocalRabbitPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val appLockStore: AppLockStore = koinInject()
    val prefsStore: InteractionPrefsStore = koinInject()
    val accountRepo: AccountRepository = koinInject()

    val lockState by appLockStore.state.collectAsStateWithLifecycle()
    val prefs by prefsStore.state.collectAsStateWithLifecycle()
    val bioHardwareSupported = remember(context) { canUseBiometricUnlock(context) }

    var pinDialogMode by remember { mutableStateOf<PinDialogMode?>(null) }
    var certStatus by remember { mutableStateOf<DeviceCertStatusModel?>(null) }
    var loadingCert by remember { mutableStateOf(false) }
    var renewingCert by remember { mutableStateOf(false) }

    fun loadCertStatus() {
        loadingCert = true
        scope.launch {
            runCatching { accountRepo.getDeviceCertStatus() }
                .onSuccess { certStatus = it }
            loadingCert = false
        }
    }

    LaunchedEffect(Unit) {
        loadCertStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .then(if (embedded) Modifier else Modifier.systemBarsPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RabbitTopBar(
            title = stringResource(R.string.sec_title),
            onBack = if (embedded) null else onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
        ) {
            // Card 1: Client App Lock
            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = palette.primaryText, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.sec_app_lock_section),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                        modifier = Modifier.weight(1f),
                    )
                    RabbitStatusPill(
                        label = stringResource(if (lockState.enabled) R.string.sec_lock_on else R.string.sec_lock_off),
                        tone = if (lockState.enabled) StatusTone.Success else StatusTone.Muted,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                RabbitSwitchRow(
                    title = stringResource(R.string.app_lock_enable_title),
                    subtitle = stringResource(R.string.app_lock_enable_sub),
                    checked = lockState.enabled,
                    onCheckedChange = { wantEnable ->
                        pinDialogMode = if (wantEnable) PinDialogMode.SetPin else PinDialogMode.VerifyToDisable
                    },
                )

                if (lockState.enabled) {
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .clickable { pinDialogMode = PinDialogMode.VerifyToChange }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.app_lock_change_pin),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.app_lock_pin_len_tag, lockState.pinLength),
                            fontSize = 12.sp,
                            color = palette.muted,
                        )
                    }

                    if (bioHardwareSupported) {
                        HorizontalDivider(color = palette.line, thickness = 1.dp)
                        RabbitSwitchRow(
                            title = stringResource(R.string.app_lock_bio_title),
                            subtitle = stringResource(R.string.app_lock_bio_sub),
                            checked = lockState.biometricEnabled,
                            onCheckedChange = appLockStore::setBiometricEnabled,
                        )
                    }

                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.app_lock_timeout_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.text,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val timeoutOptions = listOf(0, 60, 300, -1)
                    RabbitSegmentedControl(
                        options = timeoutOptions,
                        selected = lockState.autoLockTimeoutSec.let { cur ->
                            if (cur in timeoutOptions) cur else 60
                        },
                        labelProvider = { sec ->
                            when (sec) {
                                0 -> AppStrings.get(R.string.app_lock_timeout_immediate, "立即")
                                60 -> AppStrings.get(R.string.app_lock_timeout_1m, "1分钟")
                                300 -> AppStrings.get(R.string.app_lock_timeout_5m, "5分钟")
                                else -> AppStrings.get(R.string.app_lock_timeout_never, "永不")
                            }
                        },
                        onSelect = appLockStore::setAutoLockTimeoutSec,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    RabbitSecondaryButton(
                        label = stringResource(R.string.app_lock_now_btn),
                        onClick = appLockStore::lockNow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Card 2: Session Security Preferences
            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = palette.primaryText, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.sec_session_section),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                RabbitSwitchRow(
                    title = stringResource(R.string.sec_mtls_default_title),
                    subtitle = stringResource(R.string.sec_mtls_default_sub),
                    checked = prefs.encryptByDefault,
                    onCheckedChange = { on -> prefsStore.update { it.copy(encryptByDefault = on) } },
                )
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow(
                    title = stringResource(R.string.sec_lock_on_disconnect_title),
                    subtitle = stringResource(R.string.sec_lock_on_disconnect_sub),
                    checked = prefs.autoLockRemoteOnDisconnect,
                    onCheckedChange = { on -> prefsStore.update { it.copy(autoLockRemoteOnDisconnect = on) } },
                )
            }

            // Card 3: Local mTLS Device Certificate
            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.VerifiedUser, null, tint = palette.primaryText, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.sec_cert_section),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                        modifier = Modifier.weight(1f),
                    )
                    val cs = certStatus
                    val (certTone, certLabel) = when {
                        cs == null -> StatusTone.Muted to stringResource(R.string.sec_cert_loading)
                        cs.serialHex.isNullOrBlank() -> StatusTone.Warning to stringResource(R.string.sec_cert_missing)
                        cs.needsRenewal || !cs.valid -> StatusTone.Warning to stringResource(R.string.sec_cert_needs_renewal)
                        else -> StatusTone.Success to stringResource(R.string.sec_cert_valid)
                    }
                    RabbitStatusPill(label = certLabel, tone = certTone)
                    IconButton(
                        onClick = { loadCertStatus() },
                        enabled = !loadingCert && !renewingCert,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.common_refresh), tint = palette.muted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val cs = certStatus
                if (cs != null && !cs.serialHex.isNullOrBlank()) {
                    CertMetaRow(
                        label = stringResource(R.string.sec_cert_fingerprint),
                        value = cs.fingerprintSha256?.take(24)?.plus("…") ?: "--",
                        mono = true,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CertMetaRow(
                        label = stringResource(R.string.sec_cert_serial),
                        value = cs.serialHex.take(20),
                        mono = true,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CertMetaRow(
                        label = stringResource(R.string.sec_cert_expires),
                        value = formatUnixTimestamp(cs.expiresAtUnixSec),
                        mono = true,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sec_cert_none_desc),
                        fontSize = 12.sp,
                        color = palette.muted,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                RabbitPrimaryButton(
                    label = stringResource(if (renewingCert) R.string.sec_cert_renewing else R.string.sec_cert_renew_btn),
                    busy = renewingCert,
                    enabled = !renewingCert,
                    onClick = {
                        renewingCert = true
                        scope.launch {
                            runCatching { accountRepo.renewDeviceCert() }
                                .onSuccess { updated ->
                                    certStatus = updated
                                    RabbitToast.success(AppStrings.get(R.string.sec_cert_renew_ok, "设备证书已续签"))
                                }
                                .onFailure { err ->
                                    val uiErr = RcUiErrorCodec.fromThrowable(err)
                                    RabbitToast.error(uiErr.message.ifBlank { err.message ?: "Renew failed" })
                                }
                            renewingCert = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    val activeMode = pinDialogMode
    if (activeMode != null) {
        PinConfigDialog(
            mode = activeMode,
            appLockStore = appLockStore,
            onDismiss = { pinDialogMode = null },
            onSwitchToSetPin = { pinDialogMode = PinDialogMode.SetPin },
        )
    }
}

@Composable
private fun PinConfigDialog(
    mode: PinDialogMode,
    appLockStore: AppLockStore,
    onDismiss: () -> Unit,
    onSwitchToSetPin: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    var pin1 by remember(mode) { mutableStateOf("") }
    var pin2 by remember(mode) { mutableStateOf("") }
    var errorMsg by remember(mode) { mutableStateOf<String?>(null) }

    val title = when (mode) {
        PinDialogMode.SetPin -> stringResource(R.string.app_lock_set_pin_title)
        PinDialogMode.VerifyToDisable -> stringResource(R.string.app_lock_disable_title)
        PinDialogMode.VerifyToChange -> stringResource(R.string.app_lock_verify_old_title)
    }

    RabbitDialog(
        title = title,
        onDismissRequest = onDismiss,
        dismissLabel = stringResource(R.string.common_cancel),
        confirmLabel = stringResource(R.string.common_save),
        confirmEnabled = if (mode == PinDialogMode.SetPin) {
            appLockStore.isValidPinFormat(pin1) && pin1 == pin2
        } else {
            appLockStore.isValidPinFormat(pin1)
        },
        onConfirm = {
            when (mode) {
                PinDialogMode.SetPin -> {
                    if (!appLockStore.isValidPinFormat(pin1)) {
                        errorMsg = AppStrings.get(R.string.app_lock_pin_format_err, "请输入 4 至 6 位数字密码")
                    } else if (pin1 != pin2) {
                        errorMsg = AppStrings.get(R.string.app_lock_pin_mismatch_err, "两次输入的密码不一致")
                    } else {
                        appLockStore.setPin(pin1)
                        RabbitToast.success(AppStrings.get(R.string.app_lock_pin_saved, "应用锁已设置"))
                        onDismiss()
                    }
                }
                PinDialogMode.VerifyToDisable -> {
                    if (appLockStore.disableWithPin(pin1)) {
                        RabbitToast.info(AppStrings.get(R.string.app_lock_disabled_toast, "已关闭应用锁"))
                        onDismiss()
                    } else {
                        errorMsg = AppStrings.get(R.string.app_lock_wrong_pin, "PIN 码错误，请重试")
                        pin1 = ""
                    }
                }
                PinDialogMode.VerifyToChange -> {
                    if (appLockStore.verifyPin(pin1)) {
                        onSwitchToSetPin()
                    } else {
                        errorMsg = AppStrings.get(R.string.app_lock_wrong_pin, "PIN 码错误，请重试")
                        pin1 = ""
                    }
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RabbitTextField(
                value = pin1,
                onValueChange = { next ->
                    val filtered = next.filter { it.isDigit() }.take(6)
                    pin1 = filtered
                    errorMsg = null
                },
                label = stringResource(
                    if (mode == PinDialogMode.SetPin) R.string.app_lock_new_pin_label
                    else R.string.app_lock_current_pin_label,
                ),
                placeholder = stringResource(R.string.app_lock_pin_hint),
                isPassword = true,
                keyboardType = KeyboardType.NumberPassword,
            )
            if (mode == PinDialogMode.SetPin) {
                RabbitTextField(
                    value = pin2,
                    onValueChange = { next ->
                        pin2 = next.filter { it.isDigit() }.take(6)
                        errorMsg = null
                    },
                    label = stringResource(R.string.app_lock_confirm_pin_label),
                    placeholder = stringResource(R.string.app_lock_pin_hint),
                    isPassword = true,
                    keyboardType = KeyboardType.NumberPassword,
                )
            }
            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg!!, color = palette.danger, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CertMetaRow(
    label: String,
    value: String,
    mono: Boolean = false,
) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = palette.muted)
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = palette.text,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatUnixTimestamp(unixSec: Long): String {
    if (unixSec <= 0L) return "--"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(unixSec * 1000L))
}

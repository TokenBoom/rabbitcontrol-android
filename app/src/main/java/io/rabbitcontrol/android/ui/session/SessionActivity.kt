package io.rabbitcontrol.android.ui.session

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rabbitcontrol.android.error.RcErrorAction
import io.rabbitcontrol.android.MainActivity
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AppLockStore
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.i18n.AppLocaleManager
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.input.ScancodeMapper
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.session.Core
import io.rabbitcontrol.android.ui.components.AppLockOverlay
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitToastHost
import io.rabbitcontrol.android.ui.components.RcErrorHost
import io.rabbitcontrol.android.ui.theme.RabbitTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class SessionActivity : FragmentActivity() {
    private val viewModel: SessionViewModel by viewModel()
    private val appLockStore: AppLockStore by inject()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        setContent {
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            val isTabletDevice = resources.configuration.smallestScreenWidthDp >= 600
            LaunchedEffect(prefs.orientationPref, isTabletDevice) {
                requestedOrientation = if (isTabletDevice) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    when (prefs.orientationPref) {
                        OrientationPreference.AutoSensor -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        OrientationPreference.LandscapeFirst -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        OrientationPreference.PortraitTrackpad -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }
            RabbitTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DesktopSessionScreen(
                        viewModel = viewModel,
                        onOpenTool = { kind ->
                            val route = when (kind) {
                                DeviceToolKind.FileTransfer -> "transfer"
                                DeviceToolKind.Terminal -> "terminal"
                                DeviceToolKind.PortMap -> "portmap"
                                DeviceToolKind.WatchMode -> return@DesktopSessionScreen
                            }
                            startActivity(
                                Intent(this@SessionActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(MainActivity.EXTRA_NAV_TOOL, route),
                            )
                        },
                        onExitSession = { finish() },
                    )
                    AppLockOverlay(appLockStore = appLockStore)
                    RabbitToastHost(modifier = Modifier.align(Alignment.TopCenter))
                    RcErrorHost(
                        onAction = { action, err, onRetry ->
                            when (action) {
                                RcErrorAction.RETRY -> onRetry?.invoke()
                                RcErrorAction.OPEN_PERMISSIONS -> {
                                    runCatching {
                                        startActivity(
                                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                                RcErrorAction.CONTACT_ADMIN -> {
                                    RabbitToast.info(
                                        AppStrings.get(
                                            R.string.err_contact_admin_code,
                                            "请联系管理员并附上错误码：${err.code}",
                                            err.code,
                                        ),
                                    )
                                }
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }

        applyImmersiveBars()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveBars()
        viewModel.onForeground()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocaleManager.refreshLocalizedContext(this, newConfig)
        applyImmersiveBars()
    }

    override fun onPause() {
        val kg = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        val locked = kg?.isKeyguardLocked == true
        viewModel.onBackground(locked)
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isStreaming = viewModel.sessionState.value.status is SessionStatus.Streaming
        if (isStreaming &&
            !viewModel.drawerOpen.value &&
            event.keyCode != KeyEvent.KEYCODE_BACK &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            val mapped = ScancodeMapper.keyCodeToScancodeAndShift(event.keyCode)
            if (mapped != null && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
                val (sc, needsShift) = mapped
                val pressed = event.action == KeyEvent.ACTION_DOWN
                val sticky = viewModel.stickyModifiers.value
                val metaMods = ScancodeMapper.metaStateToModifierBits(event.metaState)
                val combinedMods = metaMods or sticky
                val shiftAlreadyHeld = (combinedMods and (ScancodeMapper.MOD_LEFT_SHIFT or ScancodeMapper.MOD_RIGHT_SHIFT)) != 0
                val effectiveMods = if (needsShift) combinedMods or ScancodeMapper.MOD_LEFT_SHIFT else combinedMods

                if (pressed) {
                    if (needsShift && !shiftAlreadyHeld) {
                        Core.sendKey(ScancodeMapper.SC_LEFT_SHIFT, true, effectiveMods)
                    }
                    Core.sendKey(sc, true, effectiveMods)
                } else {
                    Core.sendKey(sc, false, effectiveMods)
                    if (needsShift && !shiftAlreadyHeld) {
                        Core.sendKey(ScancodeMapper.SC_LEFT_SHIFT, false, combinedMods)
                    }
                    if (!ScancodeMapper.isModifierScancode(sc)) {
                        viewModel.consumeStickyModifiers()
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun applyImmersiveBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.post {
                runCatching {
                    window.decorView.windowInsetsController?.let { controller ->
                        controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            }
        }
    }
}

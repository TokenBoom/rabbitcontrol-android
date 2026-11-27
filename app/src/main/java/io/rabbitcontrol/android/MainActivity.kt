package io.rabbitcontrol.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rabbitcontrol.android.error.RcErrorAction
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.AppLockStore
import io.rabbitcontrol.android.core.data.AppThemeMode
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.i18n.AppLocaleManager
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.ui.components.AppLockOverlay
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitToastHost
import io.rabbitcontrol.android.ui.components.RcErrorHost
import io.rabbitcontrol.android.ui.screens.auth.AuthScreen
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.screens.detail.DeviceDetailScreen
import io.rabbitcontrol.android.ui.screens.home.HomeScreen
import io.rabbitcontrol.android.ui.screens.settings.AboutScreen
import io.rabbitcontrol.android.ui.screens.settings.GeneralSettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.HostSettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.InteractionSettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.NetworkSettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.SecuritySettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.UpdateCenterScreen
import io.rabbitcontrol.android.ui.screens.tools.FileTransferScreen
import io.rabbitcontrol.android.ui.screens.tools.PortMapScreen
import io.rabbitcontrol.android.ui.screens.tools.TerminalScreen
import io.rabbitcontrol.android.ui.session.SessionActivity
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.math.roundToInt

private val NavSlideEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

class MainActivity : FragmentActivity() {
    private val accountRepo: AccountRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val prefsStore: InteractionPrefsStore by inject()
    private val appLockStore: AppLockStore by inject()
    private var pendingToolRoute by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLocaleManager.refreshLocalizedContext(this)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingToolRoute = intent?.getStringExtra(EXTRA_NAV_TOOL)
        lifecycleScope.launch {
            if (!accountRepo.restored.value) {
                runCatching { accountRepo.restore() }
            }
        }
        splashScreen.setKeepOnScreenCondition { !accountRepo.restored.value }
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            ObjectAnimator.ofFloat(
                splashScreenViewProvider.view,
                View.ALPHA,
                1f,
                0f,
            ).apply {
                interpolator = DecelerateInterpolator()
                duration = 220L
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            splashScreenViewProvider.remove()
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            splashScreenViewProvider.remove()
                        }
                    },
                )
                start()
            }
        }

        setContent {
            val prefs by prefsStore.state.collectAsStateWithLifecycle()
            val darkTheme = when (prefs.themeMode) {
                AppThemeMode.System -> isSystemInDarkTheme()
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
            }
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                )
                onDispose {}
            }
            RabbitTheme(darkTheme = darkTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RabbitControlNavRoot(
                        accountRepo = accountRepo,
                        sessionManager = sessionManager,
                        pendingToolRoute = pendingToolRoute,
                        onConsumePendingTool = { pendingToolRoute = null },
                        onOpenSessionActivity = {
                            startActivity(Intent(this@MainActivity, SessionActivity::class.java))
                        },
                        onOpenPreviewActivity = {
                            startActivity(Intent(this@MainActivity, PreviewActivity::class.java))
                        },
                    )
                    AppLockOverlay(appLockStore = appLockStore)
                    RabbitToastHost(modifier = Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        AppLocaleManager.refreshLocalizedContext(this, newConfig)
        super.onConfigurationChanged(newConfig)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingToolRoute = intent.getStringExtra(EXTRA_NAV_TOOL)
    }

    companion object {
        const val EXTRA_NAV_TOOL = "nav_tool"
    }
}

@Composable
private fun RabbitControlNavRoot(
    accountRepo: AccountRepository,
    sessionManager: SessionManager,
    pendingToolRoute: String?,
    onConsumePendingTool: () -> Unit,
    onOpenSessionActivity: () -> Unit,
    onOpenPreviewActivity: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val restored by accountRepo.restored.collectAsStateWithLifecycle()
    val profile by accountRepo.profile.collectAsStateWithLifecycle()
    val server by accountRepo.server.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!accountRepo.restored.value) {
            runCatching { accountRepo.restore() }
        }
    }

    if (!restored) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.canvas),
        )
        return
    }

    if (profile == null || profile?.needsPassword == true) {
        val gateStep = when {
            profile?.needsPassword == true -> AuthStep.Password
            server != null -> AuthStep.Login
            else -> AuthStep.Server
        }
        AuthScreen(
            initialStep = gateStep,
            onlyServer = false,
            canNavigateBack = false,
            onBack = {},
            onAuthenticated = {},
        )
        return
    }

    val navController = rememberNavController()
    val defaultDeviceName = stringResource(R.string.home_device_default_name)
    var selectedDevice by remember {
        mutableStateOf(
            RemoteDevice(
                id = "",
                name = defaultDeviceName,
                os = "",
                online = false,
                busy = false,
                disabled = false,
            ),
        )
    }

    LaunchedEffect(pendingToolRoute) {
        val route = pendingToolRoute ?: return@LaunchedEffect
        val cur = sessionManager.state.value
        if (cur.targetId.isNotBlank()) {
            selectedDevice = RemoteDevice(
                id = cur.targetId,
                name = cur.deviceName.ifBlank { cur.targetId },
                os = cur.deviceOs,
                online = true,
                busy = true,
                disabled = false,
            )
            when (route) {
                "transfer" -> navController.navigate("transfer") { launchSingleTop = true }
                "terminal" -> navController.navigate("terminal") { launchSingleTop = true }
                "portmap" -> navController.navigate("portmap") { launchSingleTop = true }
            }
        }
        onConsumePendingTool()
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas),
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(300, easing = NavSlideEasing),
                initialOffsetX = { it },
            )
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(300, easing = NavSlideEasing),
                targetOffsetX = { -(it * 0.25f).roundToInt() },
            ) + fadeOut(
                animationSpec = tween(300, easing = NavSlideEasing),
                targetAlpha = 0.86f,
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(280, easing = NavSlideEasing),
                initialOffsetX = { -(it * 0.25f).roundToInt() },
            ) + fadeIn(
                animationSpec = tween(280, easing = NavSlideEasing),
                initialAlpha = 0.86f,
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(280, easing = NavSlideEasing),
                targetOffsetX = { it },
            )
        },
    ) {
        composable("home") {
            HomeScreen(
                onOpenAuth = { step, onlyServer ->
                    navController.navigate("auth/${step.name}/$onlyServer")
                },
                onOpenDevice = { device ->
                    selectedDevice = device
                    navController.navigate("detail")
                },
                onReturnToDesktop = onOpenSessionActivity,
                onOpenProfileRoute = { route -> navController.navigate(route) },
            )
        }

        composable(
            route = "auth/{step}/{onlyServer}",
            arguments = listOf(
                navArgument("step") { type = NavType.StringType },
                navArgument("onlyServer") { type = NavType.BoolType },
            ),
        ) { entry ->
            val stepName = entry.arguments?.getString("step") ?: AuthStep.Server.name
            val step = runCatching { AuthStep.valueOf(stepName) }.getOrDefault(AuthStep.Server)
            val onlyServer = entry.arguments?.getBoolean("onlyServer") ?: false
            AuthScreen(
                initialStep = step,
                onlyServer = onlyServer,
                canNavigateBack = true,
                onBack = { navController.popBackStack() },
                onAuthenticated = { navController.popBackStack() },
            )
        }

        composable("detail") {
            DeviceDetailScreen(
                device = selectedDevice,
                onBack = { navController.popBackStack() },
                onEnterDesktop = { _ -> onOpenSessionActivity() },
                onOpenTool = { kind ->
                    when (kind) {
                        DeviceToolKind.FileTransfer -> navController.navigate("transfer")
                        DeviceToolKind.Terminal -> navController.navigate("terminal")
                        DeviceToolKind.PortMap -> navController.navigate("portmap")
                        DeviceToolKind.WatchMode -> onOpenSessionActivity()
                    }
                },
            )
        }

        composable("transfer") {
            FileTransferScreen(
                deviceName = selectedDevice.name,
                onBack = { navController.popBackStack() },
            )
        }

        composable("terminal") {
            TerminalScreen(
                deviceName = selectedDevice.name,
                onBack = { navController.popBackStack() },
            )
        }

        composable("portmap") {
            PortMapScreen(
                deviceName = selectedDevice.name,
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/host") {
            HostSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAuth = { step, onlyServer ->
                    navController.navigate("auth/${step.name}/$onlyServer")
                },
            )
        }

        composable("settings/interaction") {
            InteractionSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPreviewCheck = onOpenPreviewActivity,
            )
        }

        composable("settings/network") {
            NetworkSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/general") {
            GeneralSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/updates") {
            UpdateCenterScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/security") {
            SecuritySettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/about") {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    RcErrorHost(
        onAction = { action, err, onRetry ->
            when (action) {
                RcErrorAction.RETRY -> onRetry?.invoke()
                RcErrorAction.RELOGIN -> navController.navigate("auth/${AuthStep.Login.name}/false") {
                    launchSingleTop = true
                }
                RcErrorAction.CHANGE_PASSWORD -> navController.navigate("auth/${AuthStep.Password.name}/false") {
                    launchSingleTop = true
                }
                RcErrorAction.OPEN_SETTINGS -> navController.navigate("settings/network") {
                    launchSingleTop = true
                }
                RcErrorAction.OPEN_PERMISSIONS -> {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }.onFailure {
                        val appDetails = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(appDetails) }
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
                RcErrorAction.NONE -> Unit
            }
        },
    )
}

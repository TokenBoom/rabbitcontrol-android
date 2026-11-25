package io.rabbitcontrol.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.AppThemeMode
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.ui.components.RabbitToastHost
import io.rabbitcontrol.android.ui.screens.auth.AuthScreen
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.screens.detail.DeviceDetailScreen
import io.rabbitcontrol.android.ui.screens.home.HomeScreen
import io.rabbitcontrol.android.ui.screens.settings.AboutScreen
import io.rabbitcontrol.android.ui.screens.settings.HostSettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.InteractionSettingsScreen
import io.rabbitcontrol.android.ui.screens.tools.FileTransferScreen
import io.rabbitcontrol.android.ui.screens.tools.PortMapScreen
import io.rabbitcontrol.android.ui.screens.tools.TerminalScreen
import io.rabbitcontrol.android.ui.session.SessionActivity
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val accountRepo: AccountRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val prefsStore: InteractionPrefsStore by inject()
    private var pendingToolRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
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
                    RabbitToastHost(modifier = Modifier.align(Alignment.TopCenter))
                }
            }
        }
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
    var selectedDevice by remember {
        mutableStateOf(
            RemoteDevice(
                id = "",
                name = "远程设备",
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

    NavHost(navController = navController, startDestination = "home") {
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
                onOpenPreviewCheck = onOpenPreviewActivity,
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
            )
        }

        composable("settings/about") {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

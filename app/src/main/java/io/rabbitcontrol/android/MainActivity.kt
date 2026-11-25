package io.rabbitcontrol.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.ui.screens.auth.AuthScreen
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.screens.detail.DeviceDetailScreen
import io.rabbitcontrol.android.ui.screens.home.HomeScreen
import io.rabbitcontrol.android.ui.screens.tools.FileTransferScreen
import io.rabbitcontrol.android.ui.screens.tools.PortMapScreen
import io.rabbitcontrol.android.ui.screens.tools.TerminalScreen
import io.rabbitcontrol.android.ui.session.SessionActivity
import io.rabbitcontrol.android.ui.theme.RabbitTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val sessionManager: SessionManager by inject()
    private var pendingToolRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingToolRoute = intent?.getStringExtra(EXTRA_NAV_TOOL)

        setContent {
            RabbitTheme {
                RabbitControlNavRoot(
                    sessionManager = sessionManager,
                    pendingToolRoute = pendingToolRoute,
                    onConsumePendingTool = { pendingToolRoute = null },
                    onOpenSessionActivity = {
                        startActivity(Intent(this, SessionActivity::class.java))
                    },
                    onOpenPreviewActivity = {
                        startActivity(Intent(this, PreviewActivity::class.java))
                    },
                )
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
    sessionManager: SessionManager,
    pendingToolRoute: String?,
    onConsumePendingTool: () -> Unit,
    onOpenSessionActivity: () -> Unit,
    onOpenPreviewActivity: () -> Unit,
) {
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
    }
}

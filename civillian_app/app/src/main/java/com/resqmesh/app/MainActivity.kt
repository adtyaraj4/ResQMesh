package com.resqmesh.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.resqmesh.app.mesh.MeshViewModel
import com.resqmesh.app.mesh.TransportType
import com.resqmesh.app.ui.EmergencyDetailsScreen
import com.resqmesh.app.ui.HomeScreen
import com.resqmesh.app.ui.MessageListScreen
import com.resqmesh.app.ui.NetworkScreen
import com.resqmesh.app.ui.theme.ResQMeshTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MeshViewModel by viewModels()

    // Updated from both onCreate (cold start) and onNewIntent (app already
    // running — the common case for a notification tap, since the intent
    // uses FLAG_ACTIVITY_CLEAR_TOP/NEW_TASK to reuse the existing instance
    // rather than recreating it). A Compose LaunchedEffect keyed on this
    // value's changes performs the actual navigation.
    private val pendingMessageId = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // The app must keep functioning even if some permissions are
            // denied (e.g. notifications) — nothing to do here beyond
            // letting the OS-level grant state take effect; each feature
            // (location fetch, notification post) already checks/guards
            // for its own permission at the point of use.
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingMessageId.value = intent?.getStringExtra(EXTRA_OPEN_MESSAGE_ID)

        setContent {
            ResQMeshTheme {
                var showNotificationRationale by remember {
                    mutableStateOf(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !viewModel.hasShownNotificationRationale()
                    )
                }

                if (showNotificationRationale) {
                    AlertDialog(
                        onDismissRequest = { showNotificationRationale = false },
                        title = { Text("Emergency Alerts") },
                        text = {
                            Text(
                                "ResQMesh needs notification permission so you can receive " +
                                    "urgent danger alerts from nearby people even when the app " +
                                    "is not open."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showNotificationRationale = false
                                viewModel.markNotificationRationaleShown()
                                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) {
                                Text("Allow Emergency Alerts")
                            }
                        }
                    )
                }

                val navController = rememberNavController()
                ResQMeshNavHost(navController, viewModel)

                val messageIdToOpen by pendingMessageId
                androidx.compose.runtime.LaunchedEffect(messageIdToOpen) {
                    val id = messageIdToOpen
                    if (id != null) {
                        navController.navigate("details/$id")
                        pendingMessageId.value = null
                    }
                }
            }
        }

        requestRequiredPermissions()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMessageId.value = intent.getStringExtra(EXTRA_OPEN_MESSAGE_ID)
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    companion object {
        const val EXTRA_OPEN_MESSAGE_ID = "extra_open_message_id"
    }
}

@androidx.compose.runtime.Composable
private fun ResQMeshNavHost(navController: NavHostController, viewModel: MeshViewModel) {
    val connections by viewModel.connections.collectAsState()
    val messageLog by viewModel.messageLog.collectAsState()
    val networkStats by viewModel.networkStats.collectAsState()
    val gatewayState by viewModel.gatewayState.collectAsState()
    val batteryPercent by viewModel.batteryPercent.collectAsState()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val nearbyCount = connections.count { it.transportType == TransportType.NEARBY }
            HomeScreen(
                nodeId = viewModel.myNodeId,
                nearbyNodeCount = nearbyCount,
                gatewayState = gatewayState,
                batteryPercent = batteryPercent,
                networkStats = networkStats,
                onEmergency = { type -> viewModel.sendEmergency(type) },
                onOpenMessages = { navController.navigate("messages") },
                onOpenNetwork = { navController.navigate("network") }
            )
        }
        composable("messages") {
            MessageListScreen(
                messages = messageLog,
                onBack = { navController.popBackStack() },
                onOpenMessage = { messageId -> navController.navigate("details/$messageId") }
            )
        }
        composable("network") {
            NetworkScreen(
                nodeId = viewModel.myNodeId,
                connections = connections,
                gatewayState = gatewayState,
                stats = networkStats,
                onBack = { navController.popBackStack() }
            )
        }
        composable("details/{messageId}") { backStackEntry ->
            val messageId = backStackEntry.arguments?.getString("messageId")
            val tracked = messageLog.firstOrNull { it.packet.messageId == messageId }
            EmergencyDetailsScreen(
                tracked = tracked,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

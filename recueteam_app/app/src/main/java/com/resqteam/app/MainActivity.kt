package com.resqteam.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.resqteam.app.ui.DashboardScreen
import com.resqteam.app.ui.DashboardViewModel
import com.resqteam.app.ui.IncidentDetailScreen
import com.resqteam.app.ui.theme.ResQTeamTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Whatever was granted/denied, kick (or re-kick) the connection loop.
        // The gateway manager reports PermissionMissing/DeviceNotPaired states
        // that the dashboard surfaces — it never crashes either way (spec 20/34).
        viewModel.reconnect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRuntimePermissions()

        setContent {
            ResQTeamTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(
                            viewModel = viewModel,
                            onOpenIncident = { messageId ->
                                navController.navigate("incident/$messageId")
                            }
                        )
                    }
                    composable(
                        route = "incident/{messageId}",
                        arguments = listOf(navArgument("messageId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val messageId = backStackEntry.arguments?.getString("messageId").orEmpty()
                        IncidentDetailScreen(
                            messageId = messageId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_SCAN
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}

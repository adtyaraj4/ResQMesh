package com.resqmesh.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.resqmesh.app.data.NodeIdManager
import com.resqmesh.app.nearby.NearbyTransport
import com.resqmesh.app.ui.HomeScreen
import com.resqmesh.app.ui.theme.ResQMeshTheme

class MainActivity : ComponentActivity() {

    private lateinit var nodeId: String
    private lateinit var transport: NearbyTransport

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                startMesh()
            } else {
                // For the Phase 1 prototype we just log via status flow.
                // A production build should explain why each permission is
                // required for offline emergency communication to work.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nodeId = NodeIdManager(applicationContext).getOrCreateNodeId()
        transport = NearbyTransport(applicationContext, nodeId)

        setContent {
            ResQMeshTheme {
                val status by transport.status.collectAsState()
                val discovered by transport.discoveredEndpoints.collectAsState()
                val connected by transport.connectedEndpoints.collectAsState()
                val messages by transport.incomingMessages.collectAsState()

                HomeScreen(
                    nodeId = nodeId,
                    status = status,
                    discoveredCount = discovered.size,
                    connectedEndpoints = connected.toList(),
                    messages = messages,
                    onSendTestPacket = {
                        transport.broadcastText("Hello from $nodeId at ${System.currentTimeMillis()}")
                    }
                )
            }
        }

        requestRequiredPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        transport.stopAll()
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

    private fun startMesh() {
        transport.startAdvertising()
        transport.startDiscovery()
    }
}

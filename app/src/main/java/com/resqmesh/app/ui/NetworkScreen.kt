package com.resqmesh.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.app.mesh.GatewayConnectionState
import com.resqmesh.app.mesh.NetworkStats
import com.resqmesh.app.mesh.TransportPeer
import com.resqmesh.app.mesh.TransportType

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NetworkScreen(
    nodeId: String,
    connections: List<TransportPeer>,
    gatewayState: GatewayConnectionState,
    stats: NetworkStats,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ResQMesh Network") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            SectionCard("Your Node") {
                Text(nodeId, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(12.dp))

            val nearbyPeers = connections.filter { it.transportType == TransportType.NEARBY }
            SectionCard("Mesh") {
                Text(
                    if (nearbyPeers.isNotEmpty()) "● ACTIVE" else "○ WAITING FOR NODES",
                    style = MaterialTheme.typography.titleMedium
                )
                if (nearbyPeers.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Nearby Nodes", style = MaterialTheme.typography.labelLarge)
                    nearbyPeers.forEach { peer ->
                        Text("● ${peer.displayName}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard("Gateway") {
                Text(
                    text = gatewayStatusIndicator(gatewayState) + " " + gatewayStatusLabel(gatewayState),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard("Message Stats") {
                StatRow("Queued", stats.queued)
                StatRow("Forwarded", stats.forwarded)
                StatRow("Received", stats.received)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

fun gatewayStatusIndicator(state: GatewayConnectionState): String =
    if (state == GatewayConnectionState.CONNECTED) "●" else "○"

fun gatewayStatusLabel(state: GatewayConnectionState): String = when (state) {
    GatewayConnectionState.CONNECTED -> "ESP32 CONNECTED"
    GatewayConnectionState.SCANNING -> "SEARCHING FOR ESP32..."
    GatewayConnectionState.CONNECTING -> "CONNECTING TO ESP32..."
    GatewayConnectionState.RECONNECTING -> "RECONNECTING..."
    GatewayConnectionState.ERROR -> "ERROR — CHECK BLUETOOTH/PERMISSIONS"
    GatewayConnectionState.DISCONNECTED -> "NOT CONNECTED"
}

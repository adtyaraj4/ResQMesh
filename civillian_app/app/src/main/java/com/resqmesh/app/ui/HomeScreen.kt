package com.resqmesh.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.app.mesh.GatewayConnectionState
import com.resqmesh.app.mesh.MeshPacketType
import com.resqmesh.app.mesh.NetworkStats

@Composable
fun HomeScreen(
    nodeId: String,
    nearbyNodeCount: Int,
    gatewayState: GatewayConnectionState,
    batteryPercent: Int?,
    networkStats: NetworkStats,
    onEmergency: (MeshPacketType) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNetwork: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "RESQMESH",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "OFFLINE EMERGENCY COMMUNICATION",
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(Modifier.height(12.dp))

            StatusRow(
                meshActive = nearbyNodeCount > 0,
                gatewayState = gatewayState
            )

            Spacer(Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    InfoLine("Node", nodeId)
                    InfoLine("Nearby Nodes", nearbyNodeCount.toString())
                    InfoLine("Battery", batteryPercent?.let { "$it%" } ?: "Unknown")
                    InfoLine("Queued", networkStats.queued.toString())
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onEmergency(MeshPacketType.TRAPPED) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) {
                Text("🚨 I'M IN DANGER", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmergencyButton(
                    label = "🏥 MEDICAL",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFC62828)
                ) { onEmergency(MeshPacketType.MEDICAL) }
                EmergencyButton(
                    label = "🚗 EVACUATION",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFEF6C00)
                ) { onEmergency(MeshPacketType.EVACUATION) }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmergencyButton(
                    label = "📦 SUPPLIES",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF6D4C41)
                ) { onEmergency(MeshPacketType.SUPPLIES) }
                EmergencyButton(
                    label = "🟢 SAFE",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF2E7D32)
                ) { onEmergency(MeshPacketType.SAFE) }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onOpenMessages, modifier = Modifier.weight(1f)) {
                    Text("Messages")
                }
                OutlinedButton(onClick = onOpenNetwork, modifier = Modifier.weight(1f)) {
                    Text("Network")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(meshActive: Boolean, gatewayState: GatewayConnectionState) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (meshActive) "●" else "○", color = if (meshActive) Color(0xFF2E7D32) else Color.Gray)
            Spacer(Modifier.width(6.dp))
            Text(if (meshActive) "MESH ACTIVE" else "WAITING FOR NODES", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val connected = gatewayState == GatewayConnectionState.CONNECTED
            Text(gatewayStatusIndicator(gatewayState), color = if (connected) Color(0xFF2E7D32) else Color.Gray)
            Spacer(Modifier.width(6.dp))
            Text("GATEWAY " + gatewayStatusLabel(gatewayState), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmergencyButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

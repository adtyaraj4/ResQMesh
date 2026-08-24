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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resqmesh.app.nearby.NearbyTransport

/**
 * Phase 1 test screen.
 *
 * This is intentionally NOT the final emergency-button home screen from
 * Section 6 of the spec yet. It exists to verify the offline transport
 * milestone: Node ID display, peer discovery/connection status, and a
 * button to send + a log to view a simple text packet between two phones.
 * The full emergency UI (🚨 I'M TRAPPED etc.) comes in the next phase
 * once this transport is confirmed working on real hardware.
 */
@Composable
fun HomeScreen(
    nodeId: String,
    status: String,
    discoveredCount: Int,
    connectedEndpoints: List<String>,
    messages: List<NearbyTransport.ReceivedMessage>,
    onSendTestPacket: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "RESQMESH — PHASE 1 TEST",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Offline peer-to-peer transport verification",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(16.dp))

            InfoCard(label = "Node ID", value = nodeId)
            Spacer(Modifier.height(8.dp))
            InfoCard(label = "Transport status", value = status)
            Spacer(Modifier.height(8.dp))
            InfoCard(label = "Nearby nodes discovered", value = discoveredCount.toString())
            Spacer(Modifier.height(8.dp))
            InfoCard(
                label = "Connected nodes (${connectedEndpoints.size})",
                value = if (connectedEndpoints.isEmpty()) "None yet" else connectedEndpoints.joinToString()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onSendTestPacket,
                modifier = Modifier.fillMaxWidth(),
                enabled = connectedEndpoints.isNotEmpty()
            ) {
                Text("Send test packet to connected peers")
            }

            Spacer(Modifier.height(16.dp))
            Text("Received packets", style = MaterialTheme.typography.titleMedium)
            Divider(modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages.reversed()) { msg ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "From: ${msg.fromEndpointId}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

package com.resqteam.app.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqteam.app.bluetooth.GatewayState
import com.resqteam.app.data.IncidentEntity
import com.resqteam.app.data.EmergencyPriority
import com.resqteam.app.ui.theme.severityColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenIncident: (String) -> Unit
) {
    val gatewayState by viewModel.gatewayState.collectAsState()
    val stats by viewModel.gatewayStats.collectAsState()
    val incidents by viewModel.activeIncidents.collectAsState()
    val lastPacket by viewModel.lastPacketEvent.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "RESQTEAM",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        GatewayStatusCard(gatewayState, stats, onReconnect = viewModel::reconnect)
        Spacer(Modifier.height(12.dp))

        // ALWAYS show the most recent raw line received from the ESP32.
        // This is intentionally independent of JSON parsing.
        lastPacket?.let { event ->
            RawPacketBanner(event.rawLine, event.parsed)
            Spacer(Modifier.height(12.dp))
        }

        SeverityCountRow(incidents)
        Spacer(Modifier.height(16.dp))

        Text(
            "ACTIVE EMERGENCIES: ${incidents.size}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (incidents.isEmpty()) {
            Text(
                "No active incidents. Waiting for gateway packets…",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(incidents, key = { it.messageId }) { incident ->
                    IncidentCard(incident, onClick = { onOpenIncident(incident.messageId) })
                }
            }
        }
    }
}

@Composable
private fun GatewayStatusCard(state: GatewayState, stats: com.resqteam.app.bluetooth.GatewayStats, onReconnect: () -> Unit) {
    val connected = state is GatewayState.Connected
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            if (connected) com.resqteam.app.ui.theme.SeverityLow else com.resqteam.app.ui.theme.SeverityCritical,
                            CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (connected) "GATEWAY CONNECTED" else gatewayStateLabel(state),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "ESP32 • Bluetooth Classic" + (stats.lastPacketAtMillis?.let {
                    " • Last packet ${timeFormatter.format(Date(it))}"
                } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (!connected) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onReconnect) { Text("RECONNECT") }
            }
        }
    }
}

private fun gatewayStateLabel(state: GatewayState): String = when (state) {
    is GatewayState.Connected -> "GATEWAY CONNECTED"
    GatewayState.Connecting -> "CONNECTING…"
    GatewayState.Disconnected -> "GATEWAY DISCONNECTED"
    GatewayState.BluetoothUnavailable -> "BLUETOOTH DISABLED"
    GatewayState.PermissionMissing -> "BLUETOOTH PERMISSION NEEDED"
    GatewayState.DeviceNotPaired -> "ResQTeam-ESP32 NOT PAIRED"
}

@Composable
private fun RawPacketBanner(raw: String, parsed: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = com.resqteam.app.ui.theme.SeverityLow.copy(alpha = 0.15f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (parsed) "ESP32 PACKET RECEIVED" else "ESP32 RAW PACKET RECEIVED",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                raw,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SeverityCountRow(incidents: List<IncidentEntity>) {
    val counts = EmergencyPriority.entries
        .filter { it != EmergencyPriority.STATUS }
        .associateWith { tier -> incidents.count { it.priority == tier.level } }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        counts.forEach { (tier, count) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = severityColor(tier.level).copy(alpha = 0.15f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(count.toString(), fontWeight = FontWeight.Bold, color = severityColor(tier.level))
                    Text(tier.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: IncidentEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(severityColor(incident.priority), CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    EmergencyPriority.fromLevel(incident.priority).label,
                    color = severityColor(incident.priority),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(incident.type.replace('_', ' '), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            incident.peopleCount?.let {
                Text("$it people" + (incident.injuredCount?.let { inj -> " • $inj injured" } ?: ""))
            }
            if (incident.latitude != null && incident.longitude != null) {
                Text(
                    "\uD83D\uDCCD ${"%.4f".format(incident.latitude)}, ${"%.4f".format(incident.longitude)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Received: ${timeFormatter.format(Date(incident.receivedAt))} • Node: ${incident.sourceNodeId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

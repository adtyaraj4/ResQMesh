package com.resqmesh.app.ui
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.resqmesh.app.mesh.MessageStatus
import com.resqmesh.app.mesh.TrackedMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EmergencyDetailsScreen(
    tracked: TrackedMessage?,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Details") },
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
            if (tracked == null) {
                Text("Message not found.", style = MaterialTheme.typography.bodyMedium)
                return@Scaffold
            }
            val packet = tracked.packet

            Text(
                text = "🚨 ${packet.priority.name} EMERGENCY",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(packet.type.name, style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    DetailRow("Source", packet.sourceNodeId)
                    DetailRow(
                        "Location",
                        if (packet.latitude != null && packet.longitude != null) {
                            "${packet.latitude}, ${packet.longitude}"
                        } else "LOCATION UNAVAILABLE"
                    )
                    if (packet.locationAccuracy != null) {
                        DetailRow("Accuracy", "${packet.locationAccuracy} m")
                    }
                    DetailRow("Time", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(packet.timestamp)))
                    DetailRow("Battery", packet.batteryLevel?.let { "$it%" } ?: "Unknown")
                    DetailRow("Priority", packet.priority.name)
                    DetailRow("Hops", packet.hopCount.toString())
                    DetailRow("TTL remaining", packet.ttl.toString())
                    DetailRow("Status", statusLabel(tracked.status))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun statusLabel(status: MessageStatus): String = when (status) {
    MessageStatus.STORED -> "STORED — waiting for nearby node"
    MessageStatus.FORWARDED -> "FORWARDED"
    MessageStatus.GATEWAY_SENT -> "SENT TO GATEWAY"
    MessageStatus.RECEIVED -> "RECEIVED"
    MessageStatus.ACKNOWLEDGED -> "ACKNOWLEDGED"
    MessageStatus.EXPIRED -> "EXPIRED"
}

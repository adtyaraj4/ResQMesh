package com.resqteam.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqteam.app.data.EmergencyPriority
import com.resqteam.app.data.IncidentStatus
import com.resqteam.app.ui.theme.severityColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fullFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun IncidentDetailScreen(
    messageId: String,
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val incident by viewModel.incidentById(messageId).collectAsState(initial = null)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("← Back") }
        Spacer(Modifier.height(12.dp))

        val current = incident
        if (current == null) {
            Text("Incident not found.")
            return@Column
        }

        Text(
            EmergencyPriority.fromLevel(current.priority).label + " EMERGENCY",
            color = severityColor(current.priority),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            current.type.replace('_', ' '),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        DetailRow("Incident", current.messageId)
        DetailRow("Source", current.sourceNodeId)
        DetailRow("Priority", EmergencyPriority.fromLevel(current.priority).label)
        current.peopleCount?.let { DetailRow("People", it.toString()) }
        current.injuredCount?.let { DetailRow("Injured", it.toString()) }
        if (current.latitude != null && current.longitude != null) {
            DetailRow("Location", "${current.latitude}, ${current.longitude}")
        }
        DetailRow("Received", fullFormatter.format(Date(current.receivedAt)))
        current.battery?.let { DetailRow("Battery", "$it%") }
        current.ttl?.let { DetailRow("TTL", it.toString()) }
        current.hopCount?.let { DetailRow("Hops", it.toString()) }
        if (current.duplicateCount > 0) {
            DetailRow("Duplicate packets", current.duplicateCount.toString())
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text("Status: ${current.status}", fontWeight = FontWeight.Bold)
        current.acknowledgedAt?.let {
            Text("Acknowledged: ${fullFormatter.format(Date(it))} • Operator: ${current.operatorId ?: "?"}")
        }
        Spacer(Modifier.height(12.dp))

        val status = runCatching { IncidentStatus.valueOf(current.status) }.getOrDefault(IncidentStatus.NEW)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status == IncidentStatus.NEW) {
                Button(onClick = { viewModel.acknowledge(current.messageId) }) { Text("ACKNOWLEDGE") }
            }
            if (status == IncidentStatus.NEW || status == IncidentStatus.ACKNOWLEDGED) {
                Button(onClick = { viewModel.markResponding(current.messageId) }) { Text("RESPONDING") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.markRescued(current.messageId) }) { Text("MARK RESCUED") }
            OutlinedButton(onClick = { viewModel.markResolved(current.messageId) }) { Text("RESOLVE") }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.fillMaxWidth(0.35f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

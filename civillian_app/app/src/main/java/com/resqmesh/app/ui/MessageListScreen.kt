package com.resqmesh.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun MessageListScreen(
    messages: List<TrackedMessage>,
    onBack: () -> Unit,
    onOpenMessage: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Messages") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text("No messages yet.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { tracked ->
                MessageRow(tracked, onClick = { onOpenMessage(tracked.packet.messageId) })
            }
        }
    }
}

@Composable
private fun MessageRow(tracked: TrackedMessage, onClick: () -> Unit) {
    val packet = tracked.packet
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${emoji(packet.type.name)} ${packet.type.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(packet.sourceNodeId, style = MaterialTheme.typography.bodySmall)
            Text(packet.priority.name, style = MaterialTheme.typography.bodySmall)
            Text(timeAgo(tracked.lastUpdatedMillis), style = MaterialTheme.typography.bodySmall)
            Text("● " + statusLabel(tracked.status), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun emoji(type: String): String = when (type) {
    "TRAPPED" -> "🚨"
    "MEDICAL" -> "🏥"
    "EVACUATION" -> "🚗"
    "SUPPLIES" -> "📦"
    "SAFE" -> "🟢"
    "ACK" -> "✅"
    else -> "⚠️"
}

private fun statusLabel(status: MessageStatus): String = when (status) {
    MessageStatus.STORED -> "Waiting for nearby node"
    MessageStatus.FORWARDED -> "Forwarded"
    MessageStatus.GATEWAY_SENT -> "Sent to gateway"
    MessageStatus.RECEIVED -> "Received"
    MessageStatus.ACKNOWLEDGED -> "Acknowledged"
    MessageStatus.EXPIRED -> "Expired"
}

private fun timeAgo(millis: Long): String {
    val diffSeconds = (System.currentTimeMillis() - millis) / 1000
    return when {
        diffSeconds < 60 -> "just now"
        diffSeconds < 3600 -> "${diffSeconds / 60} min ago"
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }
}

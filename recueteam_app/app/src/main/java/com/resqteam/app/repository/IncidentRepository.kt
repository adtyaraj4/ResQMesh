package com.resqteam.app.repository

import com.resqteam.app.bluetooth.BluetoothGatewayManager
import com.resqteam.app.data.IncidentDao
import com.resqteam.app.data.IncidentEntity
import com.resqteam.app.data.IncidentStatus
import com.resqteam.app.data.OperatorIdManager
import com.resqteam.app.data.PacketParseResult
import com.resqteam.app.data.ResQMessage
import com.resqteam.app.data.ResQPacketParser
import com.resqteam.app.notification.EmergencyNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Emitted once per accepted line, for the "Test Packet Received" milestone-1 UI. */
data class RawPacketEvent(val rawLine: String, val parsed: Boolean)

class IncidentRepository(
    private val dao: IncidentDao,
    private val gateway: BluetoothGatewayManager,
    private val notifier: EmergencyNotificationManager,
    private val operatorIdManager: OperatorIdManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _rawPacketEvents = MutableSharedFlow<RawPacketEvent>(extraBufferCapacity = 32)
    val rawPacketEvents = _rawPacketEvents.asSharedFlow()

    fun activeIncidents(): Flow<List<IncidentEntity>> = dao.observeActiveIncidents()
    fun history(): Flow<List<IncidentEntity>> = dao.observeHistory()
    fun activeCount(): Flow<Int> = dao.observeActiveCount()
    fun incidentById(messageId: String): Flow<IncidentEntity?> = dao.observeById(messageId)

    fun start() {
        gateway.start()
        scope.launch {
            gateway.rawLines.collect { line -> handleLine(line) }
        }
    }

    private suspend fun handleLine(line: String) {
        // Every non-blank line from the ESP32 is now displayed/ingested.
        // JSON packets use their normal ResQMesh fields; anything else is
        // wrapped by ResQPacketParser as a RAW PACKET incident.
        if (line.isBlank()) return

        when (val result = ResQPacketParser.parse(line)) {
            is PacketParseResult.Invalid -> {
                // Only truly empty/unusable lines reach here. Keep the transport
                // diagnostic visible instead of silently losing it.
                gateway.recordInvalid()
                _rawPacketEvents.emit(RawPacketEvent(line, parsed = false))
            }
            is PacketParseResult.Success -> {
                ingest(result.message)
                _rawPacketEvents.emit(RawPacketEvent(line, parsed = !result.message.type.startsWith("RAW PACKET:")))
            }
        }
    }

    /** Spec section 24: dedup by messageId; new incidents get created, repeats get refreshed. */
    private suspend fun ingest(message: ResQMessage) {
        val now = System.currentTimeMillis()
        val existing = dao.findByMessageId(message.messageId)

        if (existing != null) {
            gateway.recordDuplicate()
            dao.update(
                existing.copy(
                    lastReceivedAt = now,
                    duplicateCount = existing.duplicateCount + 1,
                    hopCount = message.hopCount ?: existing.hopCount
                )
            )
            return
        }

        gateway.recordPacketAccepted()
        val entity = IncidentEntity(
            messageId = message.messageId,
            sourceNodeId = message.sourceNodeId,
            type = message.type,
            priority = message.priority,
            latitude = message.latitude,
            longitude = message.longitude,
            nodeTimestamp = message.timestamp,
            battery = message.battery,
            ttl = message.ttl,
            hopCount = message.hopCount,
            peopleCount = message.peopleCount,
            injuredCount = message.injuredCount,
            status = IncidentStatus.NEW.name,
            receivedAt = now,
            lastReceivedAt = now
        )
        dao.insert(entity)

        if (entity.priority >= 4) { // CRITICAL or HIGH
            notifier.notifyNewIncident(entity)
        }
    }

    suspend fun acknowledge(messageId: String) = transitionTo(messageId) {
        it.copy(
            status = IncidentStatus.ACKNOWLEDGED.name,
            acknowledgedAt = System.currentTimeMillis(),
            operatorId = operatorIdManager.getOperatorId()
        )
    }

    suspend fun markResponding(messageId: String) = transitionTo(messageId) {
        it.copy(status = IncidentStatus.RESPONDING.name, respondingAt = System.currentTimeMillis())
    }

    suspend fun markRescued(messageId: String) = transitionTo(messageId) {
        it.copy(status = IncidentStatus.RESCUED.name, resolvedAt = System.currentTimeMillis())
    }

    suspend fun markResolved(messageId: String) = transitionTo(messageId) {
        it.copy(status = IncidentStatus.RESOLVED.name, resolvedAt = System.currentTimeMillis())
    }

    private suspend fun transitionTo(messageId: String, mutate: (IncidentEntity) -> IncidentEntity) {
        val incident = dao.findByMessageId(messageId) ?: return
        dao.update(mutate(incident))
    }
}

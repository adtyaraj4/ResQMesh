package com.resqmesh.app.mesh

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class MessageStatus {
    STORED,        // no peer available on any transport yet, queued locally
    FORWARDED,     // sent out on at least one transport (Nearby and/or BLE)
    GATEWAY_SENT,  // sent out specifically while the BLE gateway had a connected peer
    RECEIVED,      // arrived from the network, addressed to (or broadcast for) this node
    ACKNOWLEDGED,  // reserved for when ACK round-trip is wired up (not yet reachable)
    EXPIRED        // TTL hit zero before it could be forwarded further
}

enum class MessageDirection { OUTGOING, INCOMING }

data class TrackedMessage(
    val packet: MeshPacket,
    val status: MessageStatus,
    val direction: MessageDirection,
    val lastUpdatedMillis: Long
)

data class NetworkStats(
    val queued: Int = 0,
    val forwarded: Int = 0,
    val received: Int = 0
)

/**
 * Transport-agnostic mesh routing engine with in-memory store-and-forward
 * and per-message status tracking for the UI.
 *
 * Store-and-forward here is IN-MEMORY ONLY — a queued message survives
 * peers coming and going while the app process is alive (which is what
 * the 3-phone demo scenario needs: Phone A stores until Phone B is in
 * range, then auto-forwards with no re-press of the button), but does
 * NOT survive an app restart or device reboot. Persisting the queue
 * (e.g. via Room) is a follow-up, not done here.
 *
 * ACK packets are not yet round-tripped end-to-end, so MessageStatus.ACKNOWLEDGED
 * is defined but currently unreachable — it's there so the UI/status model
 * doesn't need to change again once ACK is wired up.
 */
class MeshManager(
    private val localNodeId: String,
    private val transports: List<MeshTransport>,
    private val seenMessageTtlMillis: Long = DEFAULT_SEEN_MESSAGE_TTL_MILLIS
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val seenMessages = ConcurrentHashMap<String, Long>()
    private val peersByTransport = ConcurrentHashMap<TransportType, List<TransportPeer>>()

    private val pendingLock = Any()
    private val pendingOutgoing = mutableListOf<MeshPacket>()

    private val messageLogMap = ConcurrentHashMap<String, TrackedMessage>()
    private val _messageLog = MutableStateFlow<List<TrackedMessage>>(emptyList())
    val messageLog: StateFlow<List<TrackedMessage>> = _messageLog.asStateFlow()

    private val forwardedCounter = AtomicInteger(0)
    private val receivedCounter = AtomicInteger(0)
    private val _networkStats = MutableStateFlow(NetworkStats())
    val networkStats: StateFlow<NetworkStats> = _networkStats.asStateFlow()

    private val _allConnections = MutableStateFlow<List<TransportPeer>>(emptyList())
    val allConnections: StateFlow<List<TransportPeer>> = _allConnections.asStateFlow()

    private val _deliveredPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    val deliveredPackets: SharedFlow<MeshPacket> = _deliveredPackets.asSharedFlow()

    private var started = false

    suspend fun start() {
        if (started) return
        started = true

        transports.forEach { transport ->
            transport.start()

            scope.launch {
                transport.observeConnections().collect { peers ->
                    peersByTransport[transport.transportType] = peers
                    recomputeAllConnections()
                    retryPendingMessages()
                }
            }

            scope.launch {
                transport.observeIncomingPackets().collect { packet ->
                    handleIncoming(packet, fromTransport = transport.transportType)
                }
            }
        }
    }

    suspend fun stop() {
        started = false
        transports.forEach { it.stop() }
    }

    /**
     * Originates a brand-new packet from THIS node (an emergency button
     * press). If no peer is connected on any transport right now, the
     * packet is queued (status STORED) instead of dropped, and will be
     * sent automatically the moment any transport reports a connection —
     * no re-press required, per the store-and-forward requirement.
     */
    suspend fun originate(packet: MeshPacket) {
        markSeen(packet.messageId)
        _deliveredPackets.tryEmit(packet)

        if (hasAnyConnectedPeer()) {
            broadcastToAllTransports(packet)
            updateLog(packet, statusForFreshSend(), MessageDirection.OUTGOING)
        } else {
            synchronized(pendingLock) { pendingOutgoing.add(packet) }
            updateLog(packet, MessageStatus.STORED, MessageDirection.OUTGOING)
        }
        publishStats()
    }

    private suspend fun retryPendingMessages() {
        if (!hasAnyConnectedPeer()) return
        val toSend = synchronized(pendingLock) {
            val copy = pendingOutgoing.toList()
            pendingOutgoing.clear()
            copy
        }
        if (toSend.isEmpty()) return
        toSend.forEach { packet ->
            broadcastToAllTransports(packet)
            updateLog(packet, statusForFreshSend(), MessageDirection.OUTGOING)
        }
        publishStats()
    }

    private suspend fun handleIncoming(packet: MeshPacket, fromTransport: TransportType) {
        if (packet.version != MeshPacket.PROTOCOL_VERSION) {
            Log.w(TAG, "Dropping packet with unsupported protocol version ${packet.version}")
            return
        }
        if (isSeen(packet.messageId)) {
            return // duplicate — already processed, never re-notify/re-forward
        }
        markSeen(packet.messageId)

        val forThisNode = packet.destinationNodeId == null || packet.destinationNodeId == localNodeId
        if (forThisNode) {
            receivedCounter.incrementAndGet()
            updateLog(packet, MessageStatus.RECEIVED, MessageDirection.INCOMING)
            _deliveredPackets.tryEmit(packet)
        }

        val shouldForward = packet.destinationNodeId == null || packet.destinationNodeId != localNodeId
        if (!shouldForward) {
            publishStats()
            return
        }

        val forwarded = packet.forwarded()
        if (forwarded.isExpired) {
            Log.d(TAG, "TTL expired for ${packet.messageId}, not forwarding further")
            if (forThisNode) updateLog(packet, MessageStatus.EXPIRED, MessageDirection.INCOMING)
            publishStats()
            return
        }

        broadcastToAllTransports(forwarded)
        forwardedCounter.incrementAndGet()
        publishStats()
    }

    private suspend fun broadcastToAllTransports(packet: MeshPacket) {
        transports.forEach { transport -> transport.broadcast(packet) }
    }

    private fun hasAnyConnectedPeer(): Boolean =
        peersByTransport.values.any { it.isNotEmpty() }

    private fun statusForFreshSend(): MessageStatus {
        val gatewayConnected = peersByTransport[TransportType.BLE_GATEWAY]?.isNotEmpty() == true
        return if (gatewayConnected) MessageStatus.GATEWAY_SENT else MessageStatus.FORWARDED
    }

    private fun isSeen(messageId: String): Boolean {
        evictExpiredSeenEntries()
        return seenMessages.containsKey(messageId)
    }

    private fun markSeen(messageId: String) {
        seenMessages[messageId] = System.currentTimeMillis()
    }

    private fun evictExpiredSeenEntries() {
        val cutoff = System.currentTimeMillis() - seenMessageTtlMillis
        seenMessages.entries.removeIf { it.value < cutoff }
    }

    private fun recomputeAllConnections() {
        _allConnections.value = peersByTransport.values.flatten()
    }

    private fun updateLog(packet: MeshPacket, status: MessageStatus, direction: MessageDirection) {
        messageLogMap[packet.messageId] = TrackedMessage(
            packet = packet,
            status = status,
            direction = direction,
            lastUpdatedMillis = System.currentTimeMillis()
        )
        _messageLog.value = messageLogMap.values.sortedByDescending { it.lastUpdatedMillis }
    }

    private fun publishStats() {
        val queued = synchronized(pendingLock) { pendingOutgoing.size }
        _networkStats.value = NetworkStats(
            queued = queued,
            forwarded = forwardedCounter.get(),
            received = receivedCounter.get()
        )
    }

    companion object {
        private const val TAG = "MeshManager"
        const val DEFAULT_SEEN_MESSAGE_TTL_MILLIS = 10 * 60 * 1000L // 10 minutes
    }
}

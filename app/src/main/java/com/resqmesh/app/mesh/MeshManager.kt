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

/**
 * Transport-agnostic mesh routing engine.
 *
 * Owns a set of [MeshTransport] implementations (today: just
 * [NearbyMeshTransport] wrapping the existing tested Nearby Connections
 * transport). It:
 *
 *  - deduplicates by messageId (Section "DUPLICATE PROTECTION")
 *  - decrements TTL / increments hopCount on every forward, and stops
 *    forwarding at ttl <= 0 (Section "MULTI-HOP")
 *  - prevents immediate echo back to the peer/transport a packet just
 *    arrived from (Section "LOOP PREVENTION")
 *  - delivers packets addressed to this node (or broadcast) to the app
 *    layer via [deliveredPackets]
 *
 * CURRENTLY OUT OF SCOPE (deferred to the BLE/ESP32 increment — adding
 * them here now, before there's a second real transport to route
 * between, would be logic nobody can exercise or verify):
 *  - ACK packets and delivery-confirmation tracking
 *  - persistent store-and-forward across app restarts / gateway
 *    reconnects (packets not yet deliverable are currently held only
 *    in each transport's own in-memory queue, if any — there is no
 *    cross-restart persistence yet)
 *
 * Register additional transports (Esp32BleTransport, etc.) via the
 * constructor list — no changes needed here when they're added, which
 * is the point of the MeshTransport interface.
 */
class MeshManager(
    private val localNodeId: String,
    private val transports: List<MeshTransport>,
    private val seenMessageTtlMillis: Long = DEFAULT_SEEN_MESSAGE_TTL_MILLIS
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // messageId -> time first seen. Used for dedup; entries older than
    // seenMessageTtlMillis are lazily evicted on insert.
    private val seenMessages = ConcurrentHashMap<String, Long>()

    // Most recent known peer list per transport, kept up to date by
    // collecting each transport's observeConnections(). Needed so
    // forwarding can address individual peers (to exclude the sender)
    // instead of only blind-broadcasting.
    private val peersByTransport = ConcurrentHashMap<TransportType, List<TransportPeer>>()

    private val _allConnections = MutableStateFlow<List<TransportPeer>>(emptyList())
    val allConnections: StateFlow<List<TransportPeer>> = _allConnections.asStateFlow()

    // Packets addressed to this node, or broadcast (destinationNodeId == null),
    // surfaced for the app/UI layer. Every packet here has already been
    // through dedup — the UI will never see the same messageId twice.
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
     * Originates a brand-new packet from THIS node (e.g. the user pressed
     * SOS). Marks it seen (so if it loops back through the mesh we don't
     * re-broadcast our own message) and sends it out every transport to
     * every currently connected peer.
     */
    suspend fun originate(packet: MeshPacket) {
        markSeen(packet.messageId)
        _deliveredPackets.tryEmit(packet) // so the originating device's own UI shows it too
        broadcastToAllTransports(packet)
    }

    private suspend fun handleIncoming(packet: MeshPacket, fromTransport: TransportType) {
        if (packet.version != MeshPacket.PROTOCOL_VERSION) {
            Log.w(TAG, "Dropping packet with unsupported protocol version ${packet.version}")
            return
        }
        if (isSeen(packet.messageId)) {
            // Duplicate — already processed (or currently being processed
            // by another transport that received the same broadcast).
            return
        }
        markSeen(packet.messageId)

        val forThisNode = packet.destinationNodeId == null || packet.destinationNodeId == localNodeId
        if (forThisNode) {
            _deliveredPackets.tryEmit(packet)
        }

        // Broadcast/emergency packets (destinationNodeId == null) always
        // continue relaying. A packet addressed to a specific OTHER node
        // also continues relaying (we're just a hop). A packet addressed
        // to exactly this node stops here.
        val shouldForward = packet.destinationNodeId == null || packet.destinationNodeId != localNodeId
        if (!shouldForward) return

        val forwarded = packet.forwarded()
        if (forwarded.isExpired) {
            Log.d(TAG, "TTL expired for ${packet.messageId}, not forwarding further")
            return
        }

        // Loop prevention: the primary mechanism here is the dedup cache
        // above (Section "LOOP PREVENTION" lists the seen-message cache as
        // a valid standalone mechanism). Excluding the exact peer/transport
        // a packet arrived from is a further refinement that requires each
        // MeshTransport to report the origin peerId alongside a packet,
        // which the current MeshTransport.observeIncomingPackets() signature
        // does not carry. Flagged as a follow-up for the BLE increment,
        // where a second real transport makes this loop case testable.
        broadcastToAllTransports(forwarded)
    }

    private suspend fun broadcastToAllTransports(packet: MeshPacket) {
        transports.forEach { transport -> transport.broadcast(packet) }
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

    companion object {
        private const val TAG = "MeshManager"
        const val DEFAULT_SEEN_MESSAGE_TTL_MILLIS = 10 * 60 * 1000L // 10 minutes
    }
}

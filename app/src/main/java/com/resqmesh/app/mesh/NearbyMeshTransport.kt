package com.resqmesh.app.mesh

import android.util.Log
import com.resqmesh.app.nearby.NearbyTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * Adapts the existing, already-tested [NearbyTransport] to the
 * [MeshTransport] contract. This class does NOT reimplement peer
 * discovery/connection — it delegates every one of those calls to
 * [nearbyTransport] unchanged. Its only job is translation:
 *
 *   MeshPacket  --(encode)-->  raw text  --(NearbyTransport.sendText)-->  wire
 *   wire  --(NearbyTransport.messageEvents)-->  raw text  --(decode)-->  MeshPacket
 *
 * If a peer sends non-MeshPacket text (e.g. a Phase 1 manual test
 * packet), it is dropped rather than crashing the routing pipeline —
 * see the mapNotNull in observeIncomingPackets().
 */
class NearbyMeshTransport(
    private val nearbyTransport: NearbyTransport
) : MeshTransport {

    override val transportType: TransportType = TransportType.NEARBY

    override suspend fun start() {
        nearbyTransport.startAdvertising()
        nearbyTransport.startDiscovery()
    }

    override suspend fun stop() {
        nearbyTransport.stopAll()
    }

    override suspend fun send(peerId: String, packet: MeshPacket) {
        nearbyTransport.sendText(peerId, MeshPacketCodec.encode(packet))
    }

    override suspend fun broadcast(packet: MeshPacket) {
        nearbyTransport.broadcastText(MeshPacketCodec.encode(packet))
    }

    override fun observeIncomingPackets(): Flow<MeshPacket> =
        nearbyTransport.messageEvents.mapNotNull { received ->
            val packet = MeshPacketCodec.decodeOrNull(received.text)
            if (packet == null) {
                Log.w(TAG, "Dropped non-MeshPacket payload from ${received.fromEndpointId}")
            }
            packet
        }

    override fun observeConnections(): Flow<List<TransportPeer>> =
        nearbyTransport.connectionEvents.map { endpointIds ->
            val now = System.currentTimeMillis()
            endpointIds.map { id ->
                TransportPeer(
                    peerId = id,
                    transportType = TransportType.NEARBY,
                    displayName = id,
                    state = TransportConnectionState.CONNECTED,
                    lastSeenMillis = now
                )
            }
        }

    companion object {
        private const val TAG = "NearbyMeshTransport"
    }
}

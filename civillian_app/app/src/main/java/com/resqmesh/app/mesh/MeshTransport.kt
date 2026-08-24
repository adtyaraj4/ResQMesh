package com.resqmesh.app.mesh

import kotlinx.coroutines.flow.Flow

/**
 * Common contract MeshManager programs against. MeshManager never needs
 * to know whether it's talking to Nearby Connections, a BLE-connected
 * ESP32 gateway, or (in the future) something else — it only sees
 * MeshPacket in, MeshPacket out, and peer state changes.
 *
 * Every packet a transport emits from observeIncomingPackets() must
 * already be a fully decoded, validated MeshPacket — transport-specific
 * framing/encoding is the transport implementation's job, not
 * MeshManager's.
 */
interface MeshTransport {
    val transportType: TransportType

    suspend fun start()
    suspend fun stop()

    /** Sends to a specific peer on this transport. */
    suspend fun send(peerId: String, packet: MeshPacket)

    /** Sends to every currently connected peer on this transport. */
    suspend fun broadcast(packet: MeshPacket)

    fun observeIncomingPackets(): Flow<MeshPacket>
    fun observeConnections(): Flow<List<TransportPeer>>
}

package com.resqmesh.app.mesh

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.nio.charset.StandardCharsets

/**
 * Adapts [Esp32BleManager] to [MeshTransport] so [MeshManager] can treat
 * "send to the ESP32 gateway" identically to "send to a Nearby peer" —
 * it never branches on transport type for routing decisions.
 *
 * There is exactly one logical peer on this transport (the gateway
 * itself), so [send] ignores peerId and always writes to the connected
 * gateway; [broadcast] does the same. If no gateway is connected, sends
 * are silently dropped by [Esp32BleManager.send] (a no-op when
 * rxCharacteristic is null) — MeshManager's store-and-forward layer
 * (not yet implemented — see MESH_CORE_INCREMENT.md) is where a future
 * retry-when-reconnected behavior belongs, not here.
 */
class Esp32BleTransport(
    private val bleManager: Esp32BleManager
) : MeshTransport {

    override val transportType: TransportType = TransportType.BLE_GATEWAY

    override suspend fun start() {
        bleManager.start()
    }

    override suspend fun stop() {
        bleManager.stop()
    }

    override suspend fun send(peerId: String, packet: MeshPacket) {
        writePacket(packet)
    }

    override suspend fun broadcast(packet: MeshPacket) {
        writePacket(packet)
    }

    private fun writePacket(packet: MeshPacket) {
        val json = MeshPacketCodec.encode(packet)
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_BLE_PACKET_BYTES) {
            // BLE ATT payload is small (typically ~20 bytes default MTU,
            // more with MTU negotiation, but not unbounded). This is a
            // soft warning, not a hard block, since MTU negotiation isn't
            // implemented yet — flagged here rather than silently
            // truncating a packet mid-JSON, which would corrupt it.
            Log.w(TAG, "Packet ${packet.messageId} is $bytes.size bytes, may exceed negotiated BLE MTU")
        }
        bleManager.send(bytes)
    }

    override fun observeIncomingPackets(): Flow<MeshPacket> =
        bleManager.incomingPackets.mapNotNull { bytes ->
            val text = String(bytes, StandardCharsets.UTF_8)
            val packet = MeshPacketCodec.decodeOrNull(text)
            if (packet == null) {
                Log.w(TAG, "Dropped non-MeshPacket payload from gateway ($text)")
            }
            packet
        }

    override fun observeConnections(): Flow<List<TransportPeer>> =
        bleManager.gateway.map { gateway ->
            if (gateway != null && gateway.connected) {
                listOf(
                    TransportPeer(
                        peerId = gateway.gatewayId,
                        transportType = TransportType.BLE_GATEWAY,
                        displayName = gateway.displayName,
                        state = TransportConnectionState.CONNECTED,
                        lastSeenMillis = gateway.lastSeenMillis
                    )
                )
            } else {
                emptyList()
            }
        }

    companion object {
        private const val TAG = "Esp32BleTransport"
        private const val MAX_BLE_PACKET_BYTES = 512
    }
}

package com.resqmesh.app.mesh

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.nio.charset.StandardCharsets

/**
 * Adapts [BluetoothClassicGatewayManager] to [MeshTransport], replacing
 * [Esp32BleTransport] as the registered gateway transport now that the
 * physical gateway link is Bluetooth Classic SPP rather than BLE GATT
 * (per the actual ESP32 sketch in use). MeshManager is unaffected either
 * way — it only ever sees the MeshTransport interface.
 */
class BluetoothClassicTransport(
    private val gatewayManager: BluetoothClassicGatewayManager
) : MeshTransport {

    override val transportType: TransportType = TransportType.BLE_GATEWAY

    override suspend fun start() {
        gatewayManager.start()
    }

    override suspend fun stop() {
        gatewayManager.stop()
    }

    override suspend fun send(peerId: String, packet: MeshPacket) {
        writePacket(packet)
    }

    override suspend fun broadcast(packet: MeshPacket) {
        writePacket(packet)
    }

    private fun writePacket(packet: MeshPacket) {
        val json = MeshPacketCodec.encode(packet)
        gatewayManager.send(json.toByteArray(StandardCharsets.UTF_8))
    }

    override fun observeIncomingPackets(): Flow<MeshPacket> =
        gatewayManager.incomingPackets.mapNotNull { bytes ->
            val text = String(bytes, StandardCharsets.UTF_8)
            val packet = MeshPacketCodec.decodeOrNull(text)
            if (packet == null) {
                Log.w(TAG, "Dropped non-MeshPacket line from gateway ($text)")
            }
            packet
        }

    override fun observeConnections(): Flow<List<TransportPeer>> =
        gatewayManager.gateway.map { gateway ->
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
        private const val TAG = "BluetoothClassicTransport"
    }
}

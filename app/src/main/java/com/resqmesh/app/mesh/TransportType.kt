package com.resqmesh.app.mesh

/**
 * Identifies which physical transport a peer/packet came through.
 * MeshManager treats all three uniformly — it never branches on this
 * for routing decisions, only for display/diagnostics and for loop
 * prevention (not re-sending back down the transport a packet arrived on).
 */
enum class TransportType {
    NEARBY,
    BLE_GATEWAY,
    LORA_GATEWAY
}

enum class TransportConnectionState {
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * A peer reachable through some transport, as observed by MeshManager.
 * For NEARBY this is a phone; for BLE_GATEWAY this is an ESP32 gateway.
 */
data class TransportPeer(
    val peerId: String,
    val transportType: TransportType,
    val displayName: String,
    val state: TransportConnectionState,
    val lastSeenMillis: Long
)

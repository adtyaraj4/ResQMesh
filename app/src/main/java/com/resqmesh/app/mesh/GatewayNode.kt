package com.resqmesh.app.mesh

/**
 * Represents a discovered/connected ESP32 gateway. Per spec: "Do not
 * expose unnecessary hardware identifiers to the user interface" — the
 * raw Bluetooth MAC ([address]) is kept only for internal reconnect
 * logic; UI code should prefer [gatewayId]/[displayName].
 */
data class GatewayNode(
    val gatewayId: String,
    val displayName: String,
    val address: String?,
    val connected: Boolean,
    val lastSeenMillis: Long,
    val messagesSent: Int = 0,
    val messagesReceived: Int = 0
)

enum class GatewayConnectionState {
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    ERROR
}

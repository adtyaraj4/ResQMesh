package com.resqmesh.app.nearby

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * Thin, application-facing wrapper around the Google Nearby Connections API.
 *
 * IMPORTANT (per project spec, Section 4): this class is ONLY the raw
 * peer-to-peer transport. It advertises, discovers, connects, and moves
 * bytes between two directly-connected devices. It does NOT implement
 * mesh routing, TTL, deduplication, or store-and-forward — those are
 * application-layer concerns that get layered on top of this in later
 * phases (multi-hop routing engine).
 *
 * Strategy: P2P_CLUSTER is used (not P2P_STAR or P2P_POINT_TO_POINT)
 * because it allows a device to hold multiple simultaneous connections,
 * which is required for multi-hop mesh topology later. For this Phase 1
 * milestone we only ever connect two devices, but the strategy choice
 * matters for Phase 3 (three-phone multi-hop).
 */
class NearbyTransport(
    private val context: Context,
    private val localNodeId: String
) {
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

    // --- Observable state for the UI ---

    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val connectedEndpoints: StateFlow<Set<String>> = _connectedEndpoints.asStateFlow()

    private val _discoveredEndpoints = MutableStateFlow<Map<String, String>>(emptyMap()) // endpointId -> advertised name
    val discoveredEndpoints: StateFlow<Map<String, String>> = _discoveredEndpoints.asStateFlow()

    private val _incomingMessages = MutableStateFlow<List<ReceivedMessage>>(emptyList())
    val incomingMessages: StateFlow<List<ReceivedMessage>> = _incomingMessages.asStateFlow()

    // Additive event stream (does not replace incomingMessages above, which
    // the Phase 1 test screen still reads). Emits each received message
    // exactly once as it arrives, so higher layers (NearbyMeshTransport)
    // can consume it without diffing an accumulating list.
    private val _messageEvents = MutableSharedFlow<ReceivedMessage>(extraBufferCapacity = 64)
    val messageEvents: SharedFlow<ReceivedMessage> = _messageEvents.asSharedFlow()

    // Additive: emits the current connected-endpoint id set on every change,
    // paired with a human-readable name where known, so a MeshTransport
    // wrapper can build TransportPeer list without re-deriving it.
    private val _connectionEvents = MutableSharedFlow<Set<String>>(extraBufferCapacity = 16, replay = 1)
    val connectionEvents: SharedFlow<Set<String>> = _connectionEvents.asSharedFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    data class ReceivedMessage(
        val fromEndpointId: String,
        val text: String,
        val receivedAtMillis: Long
    )

    // --- Advertising (make this device discoverable) ---

    fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            localNodeId,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            _status.value = "Advertising as $localNodeId"
            Log.d(TAG, "Advertising started")
        }.addOnFailureListener { e ->
            _status.value = "Advertising failed: ${e.message}"
            Log.e(TAG, "Advertising failed", e)
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
    }

    // --- Discovery (find other advertising devices) ---

    fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            _status.value = "Discovering peers..."
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener { e ->
            _status.value = "Discovery failed: ${e.message}"
            Log.e(TAG, "Discovery failed", e)
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
    }

    fun stopAll() {
        connectionsClient.stopAllEndpoints()
        _connectedEndpoints.value = emptySet()
        _discoveredEndpoints.value = emptyMap()
        _status.value = "Stopped"
    }

    // --- Connecting ---

    private fun requestConnection(endpointId: String) {
        connectionsClient.requestConnection(
            localNodeId,
            endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            Log.e(TAG, "requestConnection failed for $endpointId", e)
        }
    }

    // --- Sending ---

    /**
     * Sends a raw UTF-8 text payload to a specific connected endpoint.
     * In Phase 1 this is a simple test packet. From Phase 4 onward this
     * will carry serialized EmergencyMessage JSON instead of plain text.
     */
    fun sendText(endpointId: String, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    /** Sends the same text payload to every currently connected endpoint. */
    fun broadcastText(text: String) {
        _connectedEndpoints.value.forEach { endpointId ->
            sendText(endpointId, text)
        }
    }

    // --- Callbacks ---

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept for the Phase 1 prototype. A real deployment could
            // show a confirmation UI, but emergency mesh nodes should connect
            // automatically without requiring user action.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            _status.value = "Connecting to ${info.endpointName}..."
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    _connectedEndpoints.value = _connectedEndpoints.value + endpointId
                    _connectionEvents.tryEmit(_connectedEndpoints.value)
                    _status.value = "Connected to $endpointId"
                    Log.d(TAG, "Connected to $endpointId")
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    _status.value = "Connection rejected by $endpointId"
                }
                else -> {
                    _status.value = "Connection failed with $endpointId"
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            _connectedEndpoints.value = _connectedEndpoints.value - endpointId
            _connectionEvents.tryEmit(_connectedEndpoints.value)
            _status.value = "Disconnected from $endpointId"
            Log.d(TAG, "Disconnected from $endpointId")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _discoveredEndpoints.value = _discoveredEndpoints.value + (endpointId to info.endpointName)
            // Auto-connect on discovery so two phones running the app find
            // each other with zero manual pairing steps — important for a
            // usable disaster-scenario UX.
            requestConnection(endpointId)
        }

        override fun onEndpointLost(endpointId: String) {
            _discoveredEndpoints.value = _discoveredEndpoints.value - endpointId
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val text = String(bytes, StandardCharsets.UTF_8)
                val message = ReceivedMessage(
                    fromEndpointId = endpointId,
                    text = text,
                    receivedAtMillis = System.currentTimeMillis()
                )
                _incomingMessages.value = _incomingMessages.value + message
                _messageEvents.tryEmit(message)
                Log.d(TAG, "Received from $endpointId: $text")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No-op for Phase 1. Later used to track delivery success/failure
            // for reliability and retry logic.
        }
    }

    companion object {
        private const val TAG = "NearbyTransport"

        // Identifies our app's traffic to Nearby Connections so it doesn't
        // try to connect to unrelated apps also using the API nearby.
        private const val SERVICE_ID = "com.resqmesh.app.SERVICE_ID"
    }
}

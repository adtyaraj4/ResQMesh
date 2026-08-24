package com.resqmesh.app.mesh

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resqmesh.app.data.NodeIdManager
import com.resqmesh.app.nearby.NearbyTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Per project spec: "The UI must communicate with MeshManager through a
 * ViewModel. Do not put networking code inside Composables." This class
 * is that seam. Still not wired into MainActivity/HomeScreen — the
 * civilian emergency UI (SOS buttons, gateway status card) is the next
 * increment; this class is what it will bind to.
 */
class MeshViewModel(application: Application) : AndroidViewModel(application) {

    private val nodeId = NodeIdManager(application).getOrCreateNodeId()

    private val nearbyTransport = NearbyTransport(application, nodeId)
    private val nearbyMeshTransport = NearbyMeshTransport(nearbyTransport)

    private val bleManager = Esp32BleManager(application)
    private val bleTransport = Esp32BleTransport(bleManager)

    private val meshManager = MeshManager(
        localNodeId = nodeId,
        transports = listOf(nearbyMeshTransport, bleTransport)
    )

    private val _connections = MutableStateFlow<List<TransportPeer>>(emptyList())
    val connections: StateFlow<List<TransportPeer>> = _connections.asStateFlow()

    private val _receivedPackets = MutableStateFlow<List<MeshPacket>>(emptyList())
    val receivedPackets: StateFlow<List<MeshPacket>> = _receivedPackets.asStateFlow()

    val gatewayState: StateFlow<GatewayConnectionState> = bleManager.state
    val gateway: StateFlow<GatewayNode?> = bleManager.gateway

    init {
        viewModelScope.launch {
            meshManager.start()
        }
        viewModelScope.launch {
            meshManager.allConnections.collect { _connections.value = it }
        }
        viewModelScope.launch {
            meshManager.deliveredPackets.collect { packet ->
                _receivedPackets.value = _receivedPackets.value + packet
            }
        }
    }

    /** Creates and originates an emergency packet from this node. */
    fun sendEmergency(
        type: MeshPacketType,
        latitude: Double?,
        longitude: Double?,
        locationAccuracy: Float?,
        locationTimestamp: Long?,
        batteryLevel: Int?
    ) {
        viewModelScope.launch {
            meshManager.originate(
                MeshPacket.emergency(
                    type = type,
                    sourceNodeId = nodeId,
                    latitude = latitude,
                    longitude = longitude,
                    locationAccuracy = locationAccuracy,
                    locationTimestamp = locationTimestamp,
                    batteryLevel = batteryLevel
                )
            )
        }
    }

    val myNodeId: String get() = nodeId

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { meshManager.stop() }
    }
}

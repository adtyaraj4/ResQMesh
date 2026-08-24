package com.resqmesh.app.mesh

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resqmesh.app.data.BatteryReader
import com.resqmesh.app.data.NodeIdManager
import com.resqmesh.app.location.LocationProvider
import com.resqmesh.app.nearby.NearbyTransport
import com.resqmesh.app.notifications.EmergencyNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The UI communicates with MeshManager only through this ViewModel —
 * no networking calls live in Composables. Wires GPS + battery into
 * outgoing emergency packets, and posts a system notification for every
 * incoming (not self-originated) emergency.
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

    private val locationProvider = LocationProvider(application)
    private val notificationManager = EmergencyNotificationManager(application)

    private val _connections = MutableStateFlow<List<TransportPeer>>(emptyList())
    val connections: StateFlow<List<TransportPeer>> = _connections.asStateFlow()

    val messageLog: StateFlow<List<TrackedMessage>> = meshManager.messageLog
    val networkStats: StateFlow<NetworkStats> = meshManager.networkStats
    val gatewayState: StateFlow<GatewayConnectionState> = bleManager.state
    val gateway: StateFlow<GatewayNode?> = bleManager.gateway

    private val _batteryPercent = MutableStateFlow<Int?>(null)
    val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()

    val myNodeId: String get() = nodeId

    init {
        viewModelScope.launch { meshManager.start() }

        viewModelScope.launch {
            meshManager.allConnections.collect { _connections.value = it }
        }

        // Notify on every newly RECEIVED (not self-originated) message.
        // messageLog is the full sorted log; we only want to fire a
        // notification the first time a given messageId shows up as
        // RECEIVED, so track which ids we've already notified for.
        val alreadyNotified = HashSet<String>()
        viewModelScope.launch {
            meshManager.messageLog.collect { log ->
                log.filter { it.direction == MessageDirection.INCOMING && it.status == MessageStatus.RECEIVED }
                    .forEach { tracked ->
                        if (alreadyNotified.add(tracked.packet.messageId)) {
                            notificationManager.notifyIncomingEmergency(tracked.packet)
                        }
                    }
            }
        }

        _batteryPercent.value = BatteryReader.currentBatteryPercent(application)
    }

    /**
     * Creates and originates an emergency packet from this node. Acquires
     * GPS internally with a bounded timeout — per spec, a missing/slow
     * location must NOT block the send; the packet goes out with
     * latitude/longitude = null if location isn't available in time.
     */
    fun sendEmergency(type: MeshPacketType) {
        viewModelScope.launch {
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val location = if (hasLocationPermission) locationProvider.getCurrentLocation() else null
            val battery = BatteryReader.currentBatteryPercent(getApplication())
            _batteryPercent.value = battery

            meshManager.originate(
                MeshPacket.emergency(
                    type = type,
                    sourceNodeId = nodeId,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    locationAccuracy = location?.accuracyMeters,
                    locationTimestamp = location?.timestampMillis,
                    batteryLevel = battery
                )
            )
        }
    }

    fun hasShownNotificationRationale(): Boolean = notificationManager.hasShownRationale()
    fun markNotificationRationaleShown() = notificationManager.markRationaleShown()

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { meshManager.stop() }
    }
}

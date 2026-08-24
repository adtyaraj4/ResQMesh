package com.resqteam.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resqteam.app.bluetooth.BluetoothGatewayManager
import com.resqteam.app.bluetooth.GatewayStats
import com.resqteam.app.bluetooth.GatewayState
import com.resqteam.app.data.AppDatabase
import com.resqteam.app.data.IncidentEntity
import com.resqteam.app.data.OperatorIdManager
import com.resqteam.app.notification.EmergencyNotificationManager
import com.resqteam.app.repository.IncidentRepository
import com.resqteam.app.repository.RawPacketEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    val gateway = BluetoothGatewayManager(application)
    private val notifier = EmergencyNotificationManager(application)
    private val operatorIdManager = OperatorIdManager(application)
    private val dao = AppDatabase.getInstance(application).incidentDao()
    private val repository = IncidentRepository(dao, gateway, notifier, operatorIdManager)

    val gatewayState: StateFlow<GatewayState> = gateway.state
    val gatewayStats: StateFlow<GatewayStats> = gateway.stats
    val lastPacketEvent: StateFlow<RawPacketEvent?> =
        repository.rawPacketEvents
            .map { it as RawPacketEvent? }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeIncidents: StateFlow<List<IncidentEntity>> =
        repository.activeIncidents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount: StateFlow<Int> =
        repository.activeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val history: StateFlow<List<IncidentEntity>> =
        repository.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val operatorId: String get() = operatorIdManager.getOperatorId()

    init {
        repository.start()
    }

    fun incidentById(messageId: String) = repository.incidentById(messageId)

    fun reconnect() = gateway.start()

    fun acknowledge(messageId: String) = viewModelScope.launch { repository.acknowledge(messageId) }
    fun markResponding(messageId: String) = viewModelScope.launch { repository.markResponding(messageId) }
    fun markRescued(messageId: String) = viewModelScope.launch { repository.markRescued(messageId) }
    fun markResolved(messageId: String) = viewModelScope.launch { repository.markResolved(messageId) }

    override fun onCleared() {
        super.onCleared()
        gateway.stop()
    }
}

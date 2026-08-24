package com.resqmesh.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * BLE central-role manager for a single ResQMesh ESP32 gateway (civilian
 * or rescue — this class doesn't care which; it connects to whichever
 * advertises [SERVICE_UUID]).
 *
 * Per spec: "Do not depend on the Bluetooth name alone for identifying a
 * gateway. Use the service UUID." — the scan filter matches ONLY on
 * service UUID. Device name is used solely for [GatewayNode.displayName].
 *
 * NOT YET VERIFIED against real hardware — I have no BLE-capable ESP32
 * in this environment. The GATT sequence (connect -> discoverServices ->
 * enable CCCD notify -> ready) follows the standard Android BLE central
 * pattern, but some ESP32 BLE stacks are timing-sensitive around
 * discoverServices() right after connect; see the README troubleshooting
 * section for what to try if the connection is flaky in practice.
 */
@SuppressLint("MissingPermission") // permission presence is checked before every entry point
class Esp32BleManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var reconnectJob: Job? = null
    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private var messagesSent = 0
    private var messagesReceived = 0
    private var stoppedByUser = false

    private val _state = MutableStateFlow(GatewayConnectionState.DISCONNECTED)
    val state: StateFlow<GatewayConnectionState> = _state.asStateFlow()

    private val _gateway = MutableStateFlow<GatewayNode?>(null)
    val gateway: StateFlow<GatewayNode?> = _gateway.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<ByteArray> = _incomingPackets.asSharedFlow()

    fun start() {
        stoppedByUser = false
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Missing BLUETOOTH_SCAN/CONNECT permission, cannot start")
            _state.value = GatewayConnectionState.ERROR
            return
        }
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter unavailable or disabled")
            _state.value = GatewayConnectionState.ERROR
            return
        }
        startScanning()
    }

    fun stop() {
        stoppedByUser = true
        reconnectJob?.cancel()
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "stopScan denied", e)
        }
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: SecurityException) {
            Log.w(TAG, "disconnect/close denied", e)
        }
        gatt = null
        _state.value = GatewayConnectionState.DISCONNECTED
    }

    fun send(bytes: ByteArray) {
        val g = gatt ?: return
        val rx = rxCharacteristic ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(rx, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                rx.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(rx)
            }
            messagesSent++
            publishGatewayState()
        } catch (e: SecurityException) {
            Log.w(TAG, "send denied", e)
        }
    }

    // --- Scanning ---

    private fun startScanning() {
        _state.value = GatewayConnectionState.SCANNING
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "No BLE scanner available")
            _state.value = GatewayConnectionState.ERROR
            return
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "startScan denied", e)
            _state.value = GatewayConnectionState.ERROR
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanner = adapter?.bluetoothLeScanner
            try {
                scanner?.stopScan(this)
            } catch (e: SecurityException) {
                Log.w(TAG, "stopScan denied", e)
            }
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed: $errorCode")
            _state.value = GatewayConnectionState.ERROR
            scheduleReconnect()
        }
    }

    // --- Connecting ---

    private fun connect(device: BluetoothDevice) {
        _state.value = GatewayConnectionState.CONNECTING
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.w(TAG, "connectGatt denied", e)
            _state.value = GatewayConnectionState.ERROR
            scheduleReconnect()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    try {
                        g.discoverServices()
                    } catch (e: SecurityException) {
                        Log.w(TAG, "discoverServices denied", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    rxCharacteristic = null
                    txCharacteristic = null
                    _gateway.value = _gateway.value?.copy(connected = false)
                    _state.value = GatewayConnectionState.DISCONNECTED
                    try {
                        g.close()
                    } catch (e: SecurityException) {
                        Log.w(TAG, "close denied", e)
                    }
                    if (!stoppedByUser) scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed: $status")
                try { g.disconnect() } catch (e: SecurityException) { }
                return
            }
            val service = g.getService(SERVICE_UUID)
            if (service == null) {
                Log.w(TAG, "Gateway did not expose $SERVICE_UUID")
                try { g.disconnect() } catch (e: SecurityException) { }
                return
            }
            rxCharacteristic = service.getCharacteristic(RX_CHARACTERISTIC_UUID)
            txCharacteristic = service.getCharacteristic(TX_CHARACTERISTIC_UUID)

            val tx = txCharacteristic
            if (tx == null) {
                Log.w(TAG, "Gateway missing TX characteristic")
                try { g.disconnect() } catch (e: SecurityException) { }
                return
            }

            try {
                g.setCharacteristicNotification(tx, true)
                val descriptor = tx.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(descriptor)
                    }
                } else {
                    Log.w(TAG, "TX characteristic has no CCCD — notifications may not arrive")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "notification setup denied", e)
            }

            val deviceAddress = try { g.device.address } catch (e: SecurityException) { null }
            val deviceName = try { g.device.name } catch (e: SecurityException) { null }
            _gateway.value = GatewayNode(
                gatewayId = deviceAddress ?: "UNKNOWN-GW",
                displayName = deviceName ?: "ResQMesh Gateway",
                address = deviceAddress,
                connected = true,
                lastSeenMillis = System.currentTimeMillis(),
                messagesSent = messagesSent,
                messagesReceived = messagesReceived
            )
            _state.value = GatewayConnectionState.CONNECTED
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == TX_CHARACTERISTIC_UUID) {
                messagesReceived++
                publishGatewayState()
                _incomingPackets.tryEmit(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == TX_CHARACTERISTIC_UUID
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                messagesReceived++
                publishGatewayState()
                _incomingPackets.tryEmit(value)
            }
        }
    }

    private fun publishGatewayState() {
        _gateway.value = _gateway.value?.copy(
            messagesSent = messagesSent,
            messagesReceived = messagesReceived,
            lastSeenMillis = System.currentTimeMillis()
        )
    }

    private fun scheduleReconnect() {
        if (stoppedByUser) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _state.value = GatewayConnectionState.RECONNECTING
            delay(RECONNECT_DELAY_MILLIS)
            if (!stoppedByUser) startScanning()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val scanGranted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
        val connectGranted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        return scanGranted && connectGranted
    }

    companion object {
        private const val TAG = "Esp32BleManager"
        private const val RECONNECT_DELAY_MILLIS = 3000L

        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

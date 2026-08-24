package com.resqteam.app.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

/** Expected paired device name for the receiving ESP32 (spec section 19). */
private const val GATEWAY_DEVICE_NAME = "ResQTeam-ESP32"

/** Standard Serial Port Profile UUID, required for RFCOMM to an SPP-mode ESP32. */
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

private const val RECONNECT_DELAY_MS = 3000L

sealed class GatewayState {
    data object Disconnected : GatewayState()
    data object BluetoothUnavailable : GatewayState()
    data object PermissionMissing : GatewayState()
    data object DeviceNotPaired : GatewayState()
    data object Connecting : GatewayState()
    data class Connected(val deviceName: String) : GatewayState()
}

data class GatewayStats(
    val packetsReceived: Int = 0,
    val duplicates: Int = 0,
    val invalid: Int = 0,
    val lastPacketAtMillis: Long? = null
)

/**
 * Owns the RFCOMM connection to the ESP32 bridge (spec section 19/20).
 * Transport only: reads newline-delimited lines and republishes them.
 * Never crashes on disconnect — always retries (spec section 20/34).
 */
class BluetoothGatewayManager(private val context: Context) {

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    }

    private val _state = MutableStateFlow<GatewayState>(GatewayState.Disconnected)
    val state: StateFlow<GatewayState> = _state

    private val _stats = MutableStateFlow(GatewayStats())
    val stats: StateFlow<GatewayStats> = _stats

    /** Raw newline-delimited lines from the ESP32, one per emitted packet. */
    private val _rawLines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val rawLines = _rawLines.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-S, BLUETOOTH/BLUETOOTH_ADMIN are normal perms granted at install.
        }
    }

    /** Starts the connect-read-retry loop. Safe to call repeatedly (e.g. from "Reconnect" button). */
    fun start() {
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch { connectionLoop() }
    }

    fun stop() {
        connectionJob?.cancel()
        closeSocketQuietly()
        _state.value = GatewayState.Disconnected
    }

    private suspend fun connectionLoop() {
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            _state.value = GatewayState.BluetoothUnavailable
            return
        }
        if (!hasBluetoothPermission()) {
            _state.value = GatewayState.PermissionMissing
            return
        }

        while (currentCoroutineIsActive()) {
            val device = findPairedGateway(bt)
            if (device == null) {
                _state.value = GatewayState.DeviceNotPaired
                delay(RECONNECT_DELAY_MS)
                continue
            }

            _state.value = GatewayState.Connecting
            val connected = tryConnectAndRead(device)
            if (!connected) {
                _state.value = GatewayState.Disconnected
                delay(RECONNECT_DELAY_MS)
            }
            // If tryConnectAndRead returns after a clean read loop exit (disconnect),
            // fall through and retry.
        }
    }

    @Suppress("SwallowedException", "MissingPermission")
    private fun findPairedGateway(bt: BluetoothAdapter): BluetoothDevice? {
        if (!hasBluetoothPermission()) return null
        return try {
            bt.bondedDevices?.firstOrNull { it.name == GATEWAY_DEVICE_NAME }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Returns true only if we actually reached a connected, then-cleanly-ended session. */
    @Suppress("MissingPermission")
    private suspend fun tryConnectAndRead(device: BluetoothDevice): Boolean {
        var localSocket: BluetoothSocket? = null
        return try {
            localSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bt_cancelDiscovery()
            localSocket.connect()
            socket = localSocket
            _state.value = GatewayState.Connected(device.name ?: GATEWAY_DEVICE_NAME)

            readLoop(localSocket)
            true
        } catch (e: SecurityException) {
            closeQuietly(localSocket)
            false
        } catch (e: IOException) {
            closeQuietly(localSocket)
            false
        } finally {
            if (socket === localSocket) socket = null
        }
    }

    @Suppress("MissingPermission")
    private fun bt_cancelDiscovery() {
        try {
            adapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            // Missing BLUETOOTH_SCAN — non-fatal, discovery cancel is a courtesy call.
        }
    }

    private suspend fun readLoop(activeSocket: BluetoothSocket) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(activeSocket.inputStream))
        try {
            while (isActive) {
                val line = reader.readLine() ?: break // null => remote closed the stream
                if (line.isNotBlank()) {
                    _rawLines.emit(line)
                }
            }
        } catch (e: IOException) {
            // Connection dropped mid-read; caller will retry.
        }
    }

    private fun closeSocketQuietly() {
        closeQuietly(socket)
        socket = null
    }

    private fun closeQuietly(s: BluetoothSocket?) {
        try {
            s?.close()
        } catch (e: IOException) {
            // ignore
        }
    }

    private fun currentCoroutineIsActive(): Boolean = connectionJob?.isActive ?: true

    fun recordPacketAccepted() {
        _stats.value = _stats.value.copy(
            packetsReceived = _stats.value.packetsReceived + 1,
            lastPacketAtMillis = System.currentTimeMillis()
        )
    }

    fun recordDuplicate() {
        _stats.value = _stats.value.copy(duplicates = _stats.value.duplicates + 1)
    }

    fun recordInvalid() {
        _stats.value = _stats.value.copy(invalid = _stats.value.invalid + 1)
    }
}

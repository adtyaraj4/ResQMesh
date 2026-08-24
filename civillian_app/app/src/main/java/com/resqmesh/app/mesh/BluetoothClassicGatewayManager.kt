package com.resqmesh.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic (SPP/RFCOMM) gateway manager — matches the ESP32
 * firmware using `BluetoothSerial` with `SerialBT.begin("ESP32_Phone_Receiver")`.
 *
 * IMPORTANT DIFFERENCE FROM BLE: Bluetooth Classic requires the device
 * to be PAIRED at the OS level first (Settings > Bluetooth > pair
 * "ESP32_Phone_Receiver") — there is no way for an app to bond a Classic
 * device without that system-level pairing step, unlike the BLE GATT
 * approach this replaces. Once paired, this class does the rest
 * automatically: it watches the phone's bonded-device list, connects
 * the moment "ESP32_Phone_Receiver" appears there, and auto-reconnects
 * if the link drops — no in-app "Connect" button.
 *
 * The wire format is newline-delimited text (matches the firmware's
 * `readStringUntil('\n')`), not raw framed bytes like the BLE version.
 */
@SuppressLint("MissingPermission") // permission presence checked before every entry point
class BluetoothClassicGatewayManager(private val context: Context) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var connectionJob: Job? = null
    private var socket: BluetoothSocket? = null
    private var stoppedByUser = false

    private var messagesSent = 0
    private var messagesReceived = 0

    private val _state = MutableStateFlow(GatewayConnectionState.DISCONNECTED)
    val state: StateFlow<GatewayConnectionState> = _state.asStateFlow()

    private val _gateway = MutableStateFlow<GatewayNode?>(null)
    val gateway: StateFlow<GatewayNode?> = _gateway.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<ByteArray> = _incomingPackets.asSharedFlow()

    fun start() {
        stoppedByUser = false
        connectionJob?.cancel()
        connectionJob = scope.launch { connectionLoop() }
    }

    fun stop() {
        stoppedByUser = true
        connectionJob?.cancel()
        closeSocketQuietly()
        _state.value = GatewayConnectionState.DISCONNECTED
    }

    fun send(bytes: ByteArray) {
        val currentSocket = socket ?: run {
            Log.w(TAG, "send() called with no active socket, dropping")
            return
        }
        scope.launch {
            try {
                // Newline-delimited, matching SerialBT.readStringUntil('\n') on the firmware.
                currentSocket.outputStream.write(bytes)
                currentSocket.outputStream.write('\n'.code)
                currentSocket.outputStream.flush()
                messagesSent++
                publishGatewayState()
            } catch (e: IOException) {
                Log.w(TAG, "Write failed, treating as disconnected", e)
                handleDisconnect()
            }
        }
    }

    private suspend fun connectionLoop() {
        while (!stoppedByUser) {
            if (!hasConnectPermission()) {
                _state.value = GatewayConnectionState.ERROR
                delay(RETRY_DELAY_MILLIS)
                continue
            }
            val device = findBondedGateway()
            if (device == null) {
                _state.value = GatewayConnectionState.SCANNING // "scanning" here means watching the bonded list
                delay(RETRY_DELAY_MILLIS)
                continue
            }

            _state.value = GatewayConnectionState.CONNECTING
            val connected = tryConnect(device)
            if (!connected) {
                _state.value = GatewayConnectionState.RECONNECTING
                delay(RETRY_DELAY_MILLIS)
                continue
            }

            _gateway.value = GatewayNode(
                gatewayId = device.address ?: "UNKNOWN-GW",
                displayName = device.name ?: EXPECTED_DEVICE_NAME,
                address = device.address,
                connected = true,
                lastSeenMillis = System.currentTimeMillis(),
                messagesSent = messagesSent,
                messagesReceived = messagesReceived
            )
            _state.value = GatewayConnectionState.CONNECTED

            readLoop() // suspends until disconnected/error
            if (!stoppedByUser) {
                _state.value = GatewayConnectionState.RECONNECTING
                delay(RETRY_DELAY_MILLIS)
            }
        }
    }

    private fun findBondedGateway(): BluetoothDevice? {
        val bondedDevices = try {
            adapter?.bondedDevices
        } catch (e: SecurityException) {
            null
        } ?: return null
        return bondedDevices.firstOrNull { device ->
            try {
                device.name == EXPECTED_DEVICE_NAME
            } catch (e: SecurityException) {
                false
            }
        }
    }

    private suspend fun tryConnect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter?.cancelDiscovery() // discovery slows down an active connect attempt
            newSocket.connect()
            socket = newSocket
            true
        } catch (e: IOException) {
            Log.w(TAG, "RFCOMM connect failed: ${e.message}")
            closeSocketQuietly()
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "RFCOMM connect denied", e)
            closeSocketQuietly()
            false
        }
    }

    private suspend fun readLoop() {
        val currentSocket = socket ?: return
        val buffer = ByteArray(1024)
        val lineBuffer = StringBuilder()
        try {
            while (true) {
                val bytesRead = currentSocket.inputStream.read(buffer)
                if (bytesRead == -1) break
                for (i in 0 until bytesRead) {
                    val c = buffer[i].toInt().toChar()
                    if (c == '\n') {
                        if (lineBuffer.isNotEmpty()) {
                            messagesReceived++
                            publishGatewayState()
                            _incomingPackets.tryEmit(lineBuffer.toString().toByteArray())
                            lineBuffer.clear()
                        }
                    } else {
                        lineBuffer.append(c)
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Read loop ended: ${e.message}")
        }
        handleDisconnect()
    }

    private fun handleDisconnect() {
        closeSocketQuietly()
        _gateway.value = _gateway.value?.copy(connected = false)
        if (!stoppedByUser) _state.value = GatewayConnectionState.DISCONNECTED
    }

    private fun closeSocketQuietly() {
        try {
            socket?.close()
        } catch (e: IOException) {
            // ignore
        }
        socket = null
    }

    private fun publishGatewayState() {
        _gateway.value = _gateway.value?.copy(
            messagesSent = messagesSent,
            messagesReceived = messagesReceived,
            lastSeenMillis = System.currentTimeMillis()
        )
    }

    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "BtClassicGatewayMgr"
        private const val RETRY_DELAY_MILLIS = 3000L

        // Must match SerialBT.begin("...") in the ESP32 sketch exactly.
        const val EXPECTED_DEVICE_NAME = "ESP32_Phone_Receiver"

        // Standard Serial Port Profile UUID — what Android's BluetoothSerial
        // library (and this firmware) uses by default.
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

package com.example.data.ble

import android.Manifest
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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

enum class BleConnectionStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    UNAVAILABLE
}

data class BleUiState(
    val isHardwareAvailable: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val hasPermissions: Boolean = false,
    val status: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val discoveredDevices: List<DiscoveredBleDevice> = emptyList(),
    val errorMessage: String? = null
)

class BleBmsManager(private val context: Context) {

    companion object {
        // JBD / Xiaoxiang Smart BMS
        private val JBD_SERVICE_UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        private val JBD_NOTIFY_CHAR_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        private val JBD_WRITE_CHAR_UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

        // JK BMS
        private val JK_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val JK_CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

        // Daly BMS
        private val DALY_SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val DALY_NOTIFY_CHAR_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        private val DALY_WRITE_CHAR_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _state = MutableStateFlow(BleUiState())
    val state: StateFlow<BleUiState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<Pair<String, ParsedBmsFrame>>(extraBufferCapacity = 16)
    val frames: SharedFlow<Pair<String, ParsedBmsFrame>> = _frames.asSharedFlow()

    private var activeGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var pollJob: Job? = null
    private var scanJob: Job? = null
    private val rxBuffer = ByteArrayOutputStream()
    private var cachedCells: List<Float> = emptyList()

    init {
        refreshEnvironmentState()
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun refreshEnvironmentState() {
        val hasBleFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val adapter = bluetoothAdapter
        val hwAvailable = hasBleFeature && adapter != null
        val enabled = adapter?.isEnabled == true
        val perms = hasRequiredPermissions()

        _state.update { current ->
            current.copy(
                isHardwareAvailable = hwAvailable,
                isBluetoothEnabled = enabled,
                hasPermissions = perms,
                status = if (!hwAvailable) BleConnectionStatus.UNAVAILABLE else current.status
            )
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = if (hasRequiredPermissions()) {
                device.name ?: result.scanRecord?.deviceName ?: "Smart BMS (${device.address.takeLast(5)})"
            } else {
                "Smart BMS"
            }
            val item = DiscoveredBleDevice(
                name = name,
                address = device.address,
                rssi = result.rssi
            )
            _state.update { current ->
                val updated = current.discoveredDevices
                    .filterNot { it.address == item.address }
                    .plus(item)
                    .sortedByDescending { it.rssi }
                current.copy(discoveredDevices = updated)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update {
                it.copy(
                    status = BleConnectionStatus.DISCONNECTED,
                    errorMessage = "BLE scan failed (code $errorCode)"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        refreshEnvironmentState()
        val adapter = bluetoothAdapter
        if (adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            _state.update {
                it.copy(
                    status = BleConnectionStatus.UNAVAILABLE,
                    errorMessage = "Bluetooth Low Energy hardware is not available on this device."
                )
            }
            return
        }
        if (!hasRequiredPermissions()) {
            _state.update { it.copy(errorMessage = "Bluetooth permission is required to scan.") }
            return
        }
        if (!adapter.isEnabled) {
            _state.update { it.copy(errorMessage = "Please turn on Bluetooth to scan for your BMS.") }
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _state.update { it.copy(errorMessage = "BLE scanner unavailable.") }
            return
        }

        _state.update {
            it.copy(
                status = BleConnectionStatus.SCANNING,
                discoveredDevices = emptyList(),
                errorMessage = null
            )
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)

        scanJob?.cancel()
        scanJob = scope.launch {
            delay(12_000L)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        if (hasRequiredPermissions()) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
        _state.update { current ->
            if (current.status == BleConnectionStatus.SCANNING) {
                current.copy(status = BleConnectionStatus.DISCONNECTED)
            } else {
                current
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, displayName: String) {
        stopScan()
        if (!hasRequiredPermissions()) {
            _state.update { it.copy(errorMessage = "Missing Bluetooth permission.") }
            return
        }
        val device: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (device == null) {
            _state.update { it.copy(errorMessage = "Invalid BLE device address.") }
            return
        }

        disconnect()
        _state.update {
            it.copy(
                status = BleConnectionStatus.CONNECTING,
                connectedDeviceName = displayName,
                connectedDeviceAddress = address,
                errorMessage = null
            )
        }

        activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        writeCharacteristic = null
        rxBuffer.reset()
        if (hasRequiredPermissions()) {
            activeGatt?.disconnect()
            activeGatt?.close()
        }
        activeGatt = null
        _state.update { current ->
            current.copy(
                status = if (current.isHardwareAvailable) BleConnectionStatus.DISCONNECTED else BleConnectionStatus.UNAVAILABLE,
                connectedDeviceName = null,
                connectedDeviceAddress = null
            )
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(status = BleConnectionStatus.CONNECTED, errorMessage = null) }
                if (hasRequiredPermissions()) {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pollJob?.cancel()
                if (hasRequiredPermissions()) {
                    gatt.close()
                }
                activeGatt = null
                _state.update {
                    it.copy(
                        status = BleConnectionStatus.DISCONNECTED,
                        connectedDeviceName = null,
                        connectedDeviceAddress = null,
                        errorMessage = if (status != BluetoothGatt.GATT_SUCCESS) "BLE disconnected (status $status)" else null
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(errorMessage = "GATT service discovery failed ($status)") }
                return
            }

            val jbdService = gatt.getService(JBD_SERVICE_UUID)
            val jkService = gatt.getService(JK_SERVICE_UUID)
            val dalyService = gatt.getService(DALY_SERVICE_UUID)

            val (notifyChar, writeChar) = when {
                jbdService != null -> Pair(
                    jbdService.getCharacteristic(JBD_NOTIFY_CHAR_UUID),
                    jbdService.getCharacteristic(JBD_WRITE_CHAR_UUID)
                )
                dalyService != null -> Pair(
                    dalyService.getCharacteristic(DALY_NOTIFY_CHAR_UUID),
                    dalyService.getCharacteristic(DALY_WRITE_CHAR_UUID)
                )
                jkService != null -> Pair(
                    jkService.getCharacteristic(JK_CHAR_UUID),
                    jkService.getCharacteristic(JK_CHAR_UUID)
                )
                else -> Pair(null, null)
            }

            if (notifyChar == null) {
                _state.update {
                    it.copy(errorMessage = "Unsupported GATT profile: Smart BMS service not found.")
                }
                return
            }

            writeCharacteristic = writeChar
            if (hasRequiredPermissions()) {
                gatt.setCharacteristicNotification(notifyChar, true)
                val cccd = notifyChar.getDescriptor(CCCD_UUID)
                if (cccd != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(cccd)
                    }
                }
            }

            startPolling(gatt)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleIncomingBytes(value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPolling(gatt: BluetoothGatt) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var tick = 0
            while (isActive) {
                val wc = writeCharacteristic
                if (wc != null && hasRequiredPermissions()) {
                    val cmd = if (tick % 3 == 0) {
                        BmsProtocolParser.JBD_CELL_VOLTAGES_CMD
                    } else {
                        BmsProtocolParser.JBD_BASIC_INFO_CMD
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(
                            wc,
                            cmd,
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        wc.value = cmd
                        @Suppress("DEPRECATION")
                        wc.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(wc)
                    }
                }
                tick++
                delay(2000L)
            }
        }
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return

        // Daly single 13-byte packet check
        if (bytes.size >= 13 && bytes[0] == 0xA5.toByte()) {
            BmsProtocolParser.parseDalySocFrame(bytes)?.let { parsed ->
                val name = _state.value.connectedDeviceName ?: "Daly Smart BMS"
                _frames.tryEmit(name to parsed)
            }
            return
        }

        // JBD multi-packet reassembly (starts with 0xDD, ends with 0x77)
        if (bytes[0] == 0xDD.toByte()) {
            rxBuffer.reset()
        }
        rxBuffer.write(bytes)
        val assembled = rxBuffer.toByteArray()
        if (assembled.isNotEmpty() && assembled.last() == 0x77.toByte()) {
            rxBuffer.reset()
            val cells = BmsProtocolParser.parseJbdCellVoltages(assembled)
            if (cells != null) {
                cachedCells = cells
                return
            }
            BmsProtocolParser.parseJbdBasicInfo(assembled, cachedCells)?.let { parsed ->
                val name = _state.value.connectedDeviceName ?: "Smart BMS"
                _frames.tryEmit(name to parsed)
            }
        }
    }
}

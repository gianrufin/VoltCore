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
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isLikelyBms: Boolean = false
)

enum class BleConnectionStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    UNAVAILABLE
}

private enum class ActiveProtocol {
    JBD,
    JK,
    DALY,
    AUTO_PROBE,
    STANDARD_BATTERY_ONLY
}

data class BleUiState(
    val isHardwareAvailable: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val hasPermissions: Boolean = false,
    val status: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val detectedProtocol: String? = null,
    val handshakeStage: String = "Idle",
    val discoveredServicesSummary: List<String> = emptyList(),
    val lastRawHex: String? = null,
    val packetsReceivedCount: Int = 0,
    val discoveredDevices: List<DiscoveredBleDevice> = emptyList(),
    val errorMessage: String? = null
)

class BleBmsManager(private val context: Context) {

    companion object {
        // JBD / Xiaoxiang Smart BMS
        private val JBD_SERVICE_UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        private val JBD_NOTIFY_CHAR_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        private val JBD_WRITE_CHAR_UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

        // JK BMS / HM-10 serial
        private val JK_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val JK_CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

        // Daly BMS
        private val DALY_SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val DALY_NOTIFY_CHAR_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        private val DALY_WRITE_CHAR_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

        // Nordic UART Service (NUS) used by many power stations & ESP32 BMS bridges
        private val NUS_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val NUS_WRITE_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val NUS_NOTIFY_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        // Standard Bluetooth SIG Battery Service
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        private val GENERIC_ACCESS_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        private val GENERIC_ATTR_UUID = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gattMutex = Mutex()
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _state = MutableStateFlow(BleUiState())
    val state: StateFlow<BleUiState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<Pair<String, ParsedBmsFrame>>(extraBufferCapacity = 32)
    val frames: SharedFlow<Pair<String, ParsedBmsFrame>> = _frames.asSharedFlow()

    private var activeGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var activeProtocol: ActiveProtocol = ActiveProtocol.AUTO_PROBE
    private var pollJob: Job? = null
    private var scanJob: Job? = null

    private var pendingOpDeferred: CompletableDeferred<Boolean>? = null
    private val rxBuffer = ByteArrayOutputStream()
    private var cachedCells: List<Float> = emptyList()
    private var lastDalyFrame: ParsedBmsFrame? = null

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
            val rawName = if (hasRequiredPermissions()) {
                device.name ?: result.scanRecord?.deviceName
            } else {
                null
            }
            val displayName = rawName?.takeIf { it.isNotBlank() }
                ?: "BLE Device (${device.address.takeLast(5)})"

            val advertisedUuids = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
            val hasKnownBmsUuid = advertisedUuids.any {
                it == JBD_SERVICE_UUID || it == JK_SERVICE_UUID ||
                    it == DALY_SERVICE_UUID || it == NUS_SERVICE_UUID ||
                    it == BATTERY_SERVICE_UUID
            }
            val lower = displayName.lowercase()
            val nameLooksPower = listOf(
                "bms", "jbd", "jk", "daly", "xiaoxiang", "power", "battery",
                "ecoflow", "bluetti", "jackery", "ank", "lfp", "lifepo4", "pack", "ef-"
            ).any { lower.contains(it) }

            val item = DiscoveredBleDevice(
                name = displayName,
                address = device.address,
                rssi = result.rssi,
                isLikelyBms = hasKnownBmsUuid || nameLooksPower
            )

            _state.update { current ->
                val updated = current.discoveredDevices
                    .filterNot { it.address == item.address }
                    .plus(item)
                    .sortedWith(
                        compareByDescending<DiscoveredBleDevice> { it.isLikelyBms }
                            .thenByDescending { it.name.startsWith("BLE Device").not() }
                            .thenByDescending { it.rssi }
                    )
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
            _state.update { it.copy(errorMessage = "Please turn on Bluetooth to scan for your power pack.") }
            return
        }

        val scanner = adapter.bluetoothLeScanner ?: run {
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
            delay(15_000L)
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
        lastDalyFrame = null
        cachedCells = emptyList()
        rxBuffer.reset()

        _state.update {
            it.copy(
                status = BleConnectionStatus.CONNECTING,
                connectedDeviceName = displayName,
                connectedDeviceAddress = address,
                detectedProtocol = null,
                handshakeStage = "Connecting GATT…",
                discoveredServicesSummary = emptyList(),
                lastRawHex = null,
                packetsReceivedCount = 0,
                errorMessage = null
            )
        }

        activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        pendingOpDeferred?.complete(false)
        pendingOpDeferred = null
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
                connectedDeviceAddress = null,
                detectedProtocol = null,
                handshakeStage = "Disconnected",
                discoveredServicesSummary = emptyList(),
                lastRawHex = null,
                packetsReceivedCount = 0
            )
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                _state.update {
                    it.copy(
                        status = BleConnectionStatus.CONNECTED,
                        handshakeStage = "Negotiating MTU & discovering services…",
                        errorMessage = null
                    )
                }
                if (hasRequiredPermissions()) {
                    val mtuRequested = gatt.requestMtu(247)
                    if (!mtuRequested) {
                        gatt.discoverServices()
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pollJob?.cancel()
                pendingOpDeferred?.complete(false)
                if (hasRequiredPermissions()) {
                    gatt.close()
                }
                activeGatt = null
                _state.update {
                    it.copy(
                        status = BleConnectionStatus.DISCONNECTED,
                        connectedDeviceName = null,
                        connectedDeviceAddress = null,
                        handshakeStage = "Disconnected",
                        errorMessage = if (status != BluetoothGatt.GATT_SUCCESS) {
                            "BLE link closed (GATT status $status)"
                        } else null
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (hasRequiredPermissions()) {
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update {
                    it.copy(
                        handshakeStage = "Service discovery failed ($status)",
                        errorMessage = "GATT service discovery failed ($status)"
                    )
                }
                return
            }

            val summary = gatt.services.map { svc ->
                val shortSvc = svc.uuid.toString().take(8)
                val chars = svc.characteristics.joinToString(", ") { ch ->
                    ch.uuid.toString().substring(4, 8).uppercase()
                }
                "0x$shortSvc [$chars]"
            }

            _state.update {
                it.copy(
                    discoveredServicesSummary = summary,
                    handshakeStage = "Subscribing to battery characteristics…"
                )
            }

            scope.launch {
                configureAndStartGattSession(gatt)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            pendingOpDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            pendingOpDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                characteristic.value?.let { handleIncomingBytes(characteristic.uuid, it) }
            }
            pendingOpDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleIncomingBytes(characteristic.uuid, value)
            }
            pendingOpDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleIncomingBytes(characteristic.uuid, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(characteristic.uuid, value)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun configureAndStartGattSession(gatt: BluetoothGatt) {
        val jbdService = gatt.getService(JBD_SERVICE_UUID)
        val jkService = gatt.getService(JK_SERVICE_UUID)
        val dalyService = gatt.getService(DALY_SERVICE_UUID)
        val nusService = gatt.getService(NUS_SERVICE_UUID)
        val batteryService = gatt.getService(BATTERY_SERVICE_UUID)

        val notifyChars = mutableListOf<BluetoothGattCharacteristic>()
        var chosenWrite: BluetoothGattCharacteristic? = null

        when {
            jbdService != null -> {
                activeProtocol = ActiveProtocol.JBD
                jbdService.getCharacteristic(JBD_NOTIFY_CHAR_UUID)?.let { notifyChars.add(it) }
                chosenWrite = jbdService.getCharacteristic(JBD_WRITE_CHAR_UUID)
                _state.update { it.copy(detectedProtocol = "JBD / Xiaoxiang BMS") }
            }
            dalyService != null -> {
                activeProtocol = ActiveProtocol.DALY
                dalyService.getCharacteristic(DALY_NOTIFY_CHAR_UUID)?.let { notifyChars.add(it) }
                chosenWrite = dalyService.getCharacteristic(DALY_WRITE_CHAR_UUID)
                    ?: dalyService.getCharacteristic(DALY_NOTIFY_CHAR_UUID)
                _state.update { it.copy(detectedProtocol = "Daly Smart BMS") }
            }
            jkService != null -> {
                activeProtocol = ActiveProtocol.JK
                jkService.getCharacteristic(JK_CHAR_UUID)?.let {
                    notifyChars.add(it)
                    chosenWrite = it
                }
                _state.update { it.copy(detectedProtocol = "JK Smart BMS") }
            }
            nusService != null -> {
                activeProtocol = ActiveProtocol.AUTO_PROBE
                nusService.getCharacteristic(NUS_NOTIFY_CHAR_UUID)?.let { notifyChars.add(it) }
                chosenWrite = nusService.getCharacteristic(NUS_WRITE_CHAR_UUID)
                _state.update { it.copy(detectedProtocol = "Nordic UART BMS Bridge") }
            }
            else -> {
                // Scan all vendor services (excluding 1800/1801) for notify/indicate & write characteristics
                val vendorServices = gatt.services.filter {
                    it.uuid != GENERIC_ACCESS_UUID && it.uuid != GENERIC_ATTR_UUID
                }
                vendorServices.forEach { svc ->
                    svc.characteristics.forEach { ch ->
                        val props = ch.properties
                        if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                            (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        ) {
                            notifyChars.add(ch)
                        }
                        if (chosenWrite == null &&
                            ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                        ) {
                            chosenWrite = ch
                        }
                    }
                }
                activeProtocol = if (chosenWrite != null) {
                    ActiveProtocol.AUTO_PROBE
                } else {
                    ActiveProtocol.STANDARD_BATTERY_ONLY
                }
                _state.update {
                    it.copy(
                        detectedProtocol = if (chosenWrite != null) {
                            "Custom GATT (Auto-Probing)"
                        } else if (batteryService != null) {
                            "Standard BLE Battery (0x180F)"
                        } else {
                            "Unknown Proprietary GATT"
                        }
                    )
                }
            }
        }

        // Always also include standard 0x2A19 Battery Level if present
        val batteryLevelChar = batteryService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
        if (batteryLevelChar != null && notifyChars.none { it.uuid == BATTERY_LEVEL_CHAR_UUID }) {
            notifyChars.add(batteryLevelChar)
        }

        writeCharacteristic = chosenWrite

        if (notifyChars.isEmpty() && chosenWrite == null && batteryLevelChar == null) {
            _state.update {
                it.copy(
                    handshakeStage = "No readable or notifiable battery characteristics found.",
                    errorMessage = "Device connected, but exposes no standard BLE Battery or Smart BMS UART characteristics."
                )
            }
            return
        }

        // Sequentially enable notifications/indications (max 6 characteristics)
        for (ch in notifyChars.take(6)) {
            enableNotificationsSerialized(gatt, ch)
        }

        // Immediately read standard Battery Level if available
        if (batteryLevelChar != null &&
            (batteryLevelChar.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
        ) {
            readCharacteristicSerialized(gatt, batteryLevelChar)
        }

        _state.update {
            it.copy(handshakeStage = "Streaming telemetry requests…")
        }

        // For JK BMS, send activation command once first
        if (activeProtocol == ActiveProtocol.JK && chosenWrite != null) {
            writeCharacteristicSerialized(gatt, chosenWrite!!, BmsProtocolParser.JK_DEVICE_INFO_CMD)
            delay(250L)
        }

        startPolling(gatt)
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableNotificationsSerialized(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) = gattMutex.withLock {
        if (!hasRequiredPermissions()) return@withLock
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return@withLock
        val props = characteristic.properties
        val value = if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        val deferred = CompletableDeferred<Boolean>()
        pendingOpDeferred = deferred
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, value) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
        if (started) {
            withTimeoutOrNull(2000L) { deferred.await() }
        }
        pendingOpDeferred = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun readCharacteristicSerialized(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) = gattMutex.withLock {
        if (!hasRequiredPermissions()) return@withLock
        val deferred = CompletableDeferred<Boolean>()
        pendingOpDeferred = deferred
        if (gatt.readCharacteristic(characteristic)) {
            withTimeoutOrNull(2000L) { deferred.await() }
        }
        pendingOpDeferred = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeCharacteristicSerialized(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ) = gattMutex.withLock {
        if (!hasRequiredPermissions()) return@withLock
        val props = characteristic.properties
        val writeType = if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val deferred = CompletableDeferred<Boolean>()
        pendingOpDeferred = deferred
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload, writeType) ==
                android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        if (started) {
            withTimeoutOrNull(1500L) { deferred.await() }
        }
        pendingOpDeferred = null
    }

    private fun startPolling(gatt: BluetoothGatt) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var tick = 0
            while (isActive) {
                val wc = writeCharacteristic
                if (wc != null && hasRequiredPermissions()) {
                    when (activeProtocol) {
                        ActiveProtocol.JBD -> {
                            val cmd = if (tick % 3 == 0) {
                                BmsProtocolParser.JBD_CELL_VOLTAGES_CMD
                            } else {
                                BmsProtocolParser.JBD_BASIC_INFO_CMD
                            }
                            writeCharacteristicSerialized(gatt, wc, cmd)
                        }
                        ActiveProtocol.DALY -> {
                            val cmds = listOf(
                                BmsProtocolParser.DALY_SOC_CMD,
                                BmsProtocolParser.DALY_MOS_CMD,
                                BmsProtocolParser.DALY_SOC_CMD,
                                BmsProtocolParser.DALY_TEMP_CMD,
                                BmsProtocolParser.DALY_SOC_CMD,
                                BmsProtocolParser.DALY_STATUS_CMD
                            )
                            writeCharacteristicSerialized(gatt, wc, cmds[tick % cmds.size])
                        }
                        ActiveProtocol.JK -> {
                            writeCharacteristicSerialized(gatt, wc, BmsProtocolParser.JK_CELL_INFO_CMD)
                        }
                        ActiveProtocol.AUTO_PROBE -> {
                            val probes = listOf(
                                BmsProtocolParser.JBD_BASIC_INFO_CMD,
                                BmsProtocolParser.DALY_SOC_CMD,
                                BmsProtocolParser.JK_CELL_INFO_CMD
                            )
                            writeCharacteristicSerialized(gatt, wc, probes[tick % probes.size])
                        }
                        ActiveProtocol.STANDARD_BATTERY_ONLY -> {
                            gatt.getService(BATTERY_SERVICE_UUID)
                                ?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                                ?.let { readCharacteristicSerialized(gatt, it) }
                        }
                    }
                }
                tick++
                delay(2000L)
            }
        }
    }

    private fun handleIncomingBytes(charUuid: UUID, bytes: ByteArray) {
        if (bytes.isEmpty()) return

        val hexPreview = bytes.take(24).joinToString(" ") { "%02X".format(it) }
        _state.update {
            it.copy(
                lastRawHex = hexPreview,
                packetsReceivedCount = it.packetsReceivedCount + 1
            )
        }

        val deviceName = _state.value.connectedDeviceName ?: "Smart BMS"

        // 1. Standard BLE Battery Level (0x2A19) - only emit when no Smart BMS UART protocol is active
        if (charUuid == BATTERY_LEVEL_CHAR_UUID) {
            if (activeProtocol != ActiveProtocol.STANDARD_BATTERY_ONLY) {
                return
            }
            val soc = (bytes[0].toInt() and 0xFF).coerceIn(0, 100)
            val fallbackFrame = ParsedBmsFrame(
                socPercent = soc,
                voltageVolts = 0f,
                currentAmps = 0f,
                temperatureCelsius = 25f,
                mosfetTempCelsius = 25f,
                cycleCount = 0,
                stateOfHealthPercent = 100,
                remainingCapacityAh = 0f,
                nominalCapacityAh = 0f,
                cellVoltages = emptyList(),
                chargeMosEnabled = true,
                dischargeMosEnabled = true,
                protocolName = "Standard BLE Battery (0x180F)"
            )
            _state.update {
                it.copy(
                    detectedProtocol = fallbackFrame.protocolName,
                    handshakeStage = "Receiving BLE Battery Level"
                )
            }
            _frames.tryEmit(deviceName to fallbackFrame)
            return
        }

        // 2. Daly 13-byte frame(s)
        if (bytes.size >= 13 && bytes[0] == 0xA5.toByte() && bytes[1] != 0x40.toByte()) {
            var offset = 0
            var parsedAny = false
            while (offset + 13 <= bytes.size && bytes[offset] == 0xA5.toByte()) {
                val slice = bytes.copyOfRange(offset, offset + 13)
                BmsProtocolParser.parseDalyFrame(slice, lastDalyFrame)?.let { parsed ->
                    lastDalyFrame = parsed
                    activeProtocol = ActiveProtocol.DALY
                    parsedAny = true
                }
                offset += 13
            }
            if (parsedAny) {
                val latestDaly = lastDalyFrame
                _state.update {
                    it.copy(
                        detectedProtocol = latestDaly?.protocolName ?: "Daly Smart BMS",
                        handshakeStage = "Streaming live telemetry"
                    )
                }
                if (latestDaly != null && latestDaly.voltageVolts > 0f) {
                    _frames.tryEmit(deviceName to latestDaly)
                }
                return
            }
        }

        // 3. Reassemble JBD (starts DD 03/04 00, length-delimited) or JK (starts 55 AA EB 90, 300B)
        val isJbdStart = bytes.size >= 3 &&
            bytes[0] == 0xDD.toByte() &&
            (bytes[1] == 0x03.toByte() || bytes[1] == 0x04.toByte()) &&
            bytes[2] == 0x00.toByte()
        val isJkStart = bytes.size >= 4 &&
            bytes[0] == 0x55.toByte() &&
            bytes[1] == 0xAA.toByte() &&
            bytes[2] == 0xEB.toByte() &&
            bytes[3] == 0x90.toByte()

        if (isJbdStart || isJkStart) {
            rxBuffer.reset()
        }
        rxBuffer.write(bytes)
        val assembled = rxBuffer.toByteArray()

        // Try JBD complete frame using exact payload length check (avoids premature trigger on 0x77 in payload)
        if (assembled.size >= 4 && assembled[0] == 0xDD.toByte()) {
            val dataLen = assembled[3].toInt() and 0xFF
            val expectedLen = dataLen + 7
            if (expectedLen in 7..256 && assembled.size >= expectedLen) {
                rxBuffer.reset()
                if (assembled[expectedLen - 1] == 0x77.toByte()) {
                    val frameBytes = assembled.copyOfRange(0, expectedLen)
                    val cells = BmsProtocolParser.parseJbdCellVoltages(frameBytes)
                    if (cells != null) {
                        cachedCells = cells
                        return
                    }
                    BmsProtocolParser.parseJbdBasicInfo(frameBytes, cachedCells)?.let { parsed ->
                        activeProtocol = ActiveProtocol.JBD
                        _state.update {
                            it.copy(
                                detectedProtocol = parsed.protocolName,
                                handshakeStage = "Streaming live telemetry"
                            )
                        }
                        _frames.tryEmit(deviceName to parsed)
                    }
                }
                return
            }
        }

        // Try JK complete frame (full 300-byte frame with CRC at index 299)
        if (assembled.size >= 300 &&
            assembled[0] == 0x55.toByte() &&
            assembled[1] == 0xAA.toByte() &&
            assembled[2] == 0xEB.toByte() &&
            assembled[3] == 0x90.toByte()
        ) {
            rxBuffer.reset()
            BmsProtocolParser.parseJkCellInfoFrame(assembled)?.let { parsed ->
                activeProtocol = ActiveProtocol.JK
                _state.update {
                    it.copy(
                        detectedProtocol = parsed.protocolName,
                        handshakeStage = "Streaming live telemetry"
                    )
                }
                _frames.tryEmit(deviceName to parsed)
            }
        } else if (assembled.size > 512) {
            rxBuffer.reset()
        }
    }
}

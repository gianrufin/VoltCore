package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ble.BleConnectionStatus
import com.example.data.ble.BleUiState
import com.example.data.ble.PassThroughFlow
import com.example.data.ble.PowerMath
import com.example.data.local.AlertEventEntity
import com.example.data.local.OutputPortType
import com.example.data.local.PluggedDeviceEntity
import com.example.data.local.SavedStationEntity
import com.example.data.local.TelemetrySnapshotEntity
import com.example.data.prefs.AppSettings
import com.example.data.prefs.DisplayDensity
import com.example.data.prefs.TemperatureUnit
import com.example.data.repository.BmsRepository
import com.example.data.repository.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ConnectionMode {
    DISCONNECTED,
    CONNECTING,
    LIVE_BLE
}

data class MonitorUiState(
    val snapshot: TelemetrySnapshotEntity? = null,
    val connectionMode: ConnectionMode = ConnectionMode.DISCONNECTED,
    val savedStations: List<SavedStationEntity> = emptyList(),
    val isDashboardOpen: Boolean = false,
    val hoursRemaining: Float? = null,
    val formattedEta: String = "--",
    val remainingWh: Float = 0f,
    val packCapacityWh: Float = 1024f,
    val pluggedDevices: List<PluggedDeviceEntity> = emptyList(),
    val activePluggedWatts: Float = 0f,
    val passThroughFlow: PassThroughFlow = PassThroughFlow(),
    val bleState: BleUiState = BleUiState(),
    val settings: AppSettings = AppSettings()
)

data class DiagnosticsUiState(
    val recentSnapshots: List<TelemetrySnapshotEntity> = emptyList(),
    val latestSnapshot: TelemetrySnapshotEntity? = null,
    val alerts: List<AlertEventEntity> = emptyList(),
    val isOfflineCached: Boolean = true,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val updateCheckResult: UpdateCheckResult = UpdateCheckResult.Idle,
    val hasNotificationPermission: Boolean = true,
    val cachedSampleCount: Int = 0
)

class VoltCoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BmsRepository(application)

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    private val _hasNotificationPermission =
        MutableStateFlow(repository.notifier.hasNotificationPermission())
    private val _isConnectionSheetOpen = MutableStateFlow(false)
    val isConnectionSheetOpen: StateFlow<Boolean> = _isConnectionSheetOpen.asStateFlow()

    private val _isDashboardOpen = MutableStateFlow(false)
    private var autoOpenedForCurrentSession = false

    init {
        viewModelScope.launch {
            combine(
                repository.bleStateFlow,
                repository.liveSnapshotFlow
            ) { ble, live -> ble.status to live }
                .collect { (status, live) ->
                    when (status) {
                        BleConnectionStatus.CONNECTED -> {
                            if (live != null && !autoOpenedForCurrentSession) {
                                autoOpenedForCurrentSession = true
                                _isDashboardOpen.value = true
                            }
                        }
                        BleConnectionStatus.CONNECTING -> {
                            autoOpenedForCurrentSession = false
                        }
                        BleConnectionStatus.DISCONNECTED,
                        BleConnectionStatus.UNAVAILABLE,
                        BleConnectionStatus.SCANNING -> {
                            if (status != BleConnectionStatus.SCANNING) {
                                autoOpenedForCurrentSession = false
                                _isDashboardOpen.value = false
                            }
                        }
                    }
                }
        }
    }

    private val stationHubFlow = combine(
        repository.savedStationsFlow,
        _isDashboardOpen
    ) { stations, dashboardOpen -> stations to dashboardOpen }

    val monitorUiState: StateFlow<MonitorUiState> = combine(
        repository.liveSnapshotFlow,
        repository.pluggedDevicesFlow,
        repository.bleStateFlow,
        repository.settingsFlow,
        stationHubFlow
    ) { liveSnapshot, devices, ble, settings, (stations, dashboardOpen) ->
        val isLiveConnected = ble.status == BleConnectionStatus.CONNECTED
        val activeSnapshot = if (isLiveConnected) liveSnapshot else null

        val mode = when {
            isLiveConnected -> ConnectionMode.LIVE_BLE
            ble.status == BleConnectionStatus.CONNECTING -> ConnectionMode.CONNECTING
            else -> ConnectionMode.DISCONNECTED
        }

        val hoursRemaining = activeSnapshot?.let {
            PowerMath.computeHoursRemaining(
                socPercent = it.exactSocPercent,
                powerWatts = it.powerWatts,
                packCapacityWh = settings.packCapacityWh,
                isCharging = it.isCharging
            )
        }

        val remainingWh = activeSnapshot?.let {
            (it.exactSocPercent / 100f) * settings.packCapacityWh
        } ?: 0f

        val activeDeviceWatts = devices.filter { it.isActive }
            .sumOf { it.estimatedWatts.toDouble() }
            .toFloat()

        val passThroughFlow = activeSnapshot?.let {
            PowerMath.computePassThroughFlow(
                netPowerWatts = it.powerWatts,
                activePluggedWatts = activeDeviceWatts,
                chargeMosEnabled = it.chargeMosEnabled
            )
        } ?: PassThroughFlow(loadOutWatts = activeDeviceWatts)

        MonitorUiState(
            snapshot = activeSnapshot,
            connectionMode = mode,
            savedStations = stations,
            isDashboardOpen = dashboardOpen && isLiveConnected && activeSnapshot != null,
            hoursRemaining = hoursRemaining,
            formattedEta = PowerMath.formatDurationHours(hoursRemaining),
            remainingWh = remainingWh,
            packCapacityWh = settings.packCapacityWh,
            pluggedDevices = devices,
            activePluggedWatts = activeDeviceWatts,
            passThroughFlow = passThroughFlow,
            bleState = ble,
            settings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonitorUiState()
    )

    val diagnosticsUiState: StateFlow<DiagnosticsUiState> = combine(
        repository.recentSnapshotsFlow,
        repository.latestSnapshotFlow,
        repository.alertEventsFlow,
        repository.bleStateFlow,
        repository.settingsFlow
    ) { snapshots, latest, alerts, ble, settings ->
        val offline = ble.status != BleConnectionStatus.CONNECTED
        DiagnosticsUiState(
            recentSnapshots = snapshots.reversed(),
            latestSnapshot = latest,
            alerts = alerts,
            isOfflineCached = offline,
            temperatureUnit = settings.temperatureUnit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiagnosticsUiState()
    )

    val settingsUiState: StateFlow<SettingsUiState> = combine(
        repository.settingsFlow,
        _updateCheckResult,
        _hasNotificationPermission,
        repository.recentSnapshotsFlow
    ) { settings, updateResult, notifPerm, snapshots ->
        SettingsUiState(
            settings = settings,
            updateCheckResult = updateResult,
            hasNotificationPermission = notifPerm,
            cachedSampleCount = snapshots.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun openConnectionSheet() {
        repository.bleManager.refreshEnvironmentState()
        _isConnectionSheetOpen.value = true
    }

    fun closeConnectionSheet() {
        repository.bleManager.stopScan()
        _isConnectionSheetOpen.value = false
    }

    fun openStationDashboard() {
        if (repository.bleManager.state.value.status == BleConnectionStatus.CONNECTED) {
            _isDashboardOpen.value = true
        }
    }

    fun closeStationDashboard() {
        _isDashboardOpen.value = false
    }

    fun connectSavedStation(station: SavedStationEntity) {
        autoOpenedForCurrentSession = false
        repository.connectBleDevice(station.address, station.name)
    }

    fun removeSavedStation(address: String) {
        viewModelScope.launch {
            repository.removeSavedStation(address)
        }
    }

    fun requiredBlePermissions(): Array<String> = repository.bleManager.requiredPermissions()

    fun refreshPermissions() {
        repository.bleManager.refreshEnvironmentState()
        _hasNotificationPermission.value = repository.notifier.hasNotificationPermission()
    }

    fun startBleScan() {
        repository.bleManager.startScan()
    }

    fun stopBleScan() {
        repository.bleManager.stopScan()
    }

    fun connectBleDevice(address: String, name: String) {
        autoOpenedForCurrentSession = false
        repository.connectBleDevice(address, name)
        _isConnectionSheetOpen.value = false
    }

    fun disconnectBle() {
        autoOpenedForCurrentSession = false
        _isDashboardOpen.value = false
        repository.disconnectBle()
    }

    fun savePluggedDevice(
        id: Long,
        name: String,
        portType: OutputPortType,
        watts: Float
    ) {
        viewModelScope.launch {
            repository.upsertPluggedDevice(
                PluggedDeviceEntity(
                    id = id,
                    name = name.trim(),
                    portType = portType.name,
                    estimatedWatts = watts.coerceAtLeast(1f),
                    isActive = true,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun togglePluggedDevice(device: PluggedDeviceEntity) {
        viewModelScope.launch {
            repository.togglePluggedDevice(device)
        }
    }

    fun deletePluggedDevice(id: Long) {
        viewModelScope.launch {
            repository.deletePluggedDevice(id)
        }
    }

    fun setAutoCalibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setAutoCalibrationEnabled(enabled)
        }
    }

    fun setDisplayDensity(density: DisplayDensity) {
        viewModelScope.launch {
            repository.preferences.setDisplayDensity(density)
        }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch {
            repository.preferences.setTemperatureUnit(unit)
        }
    }

    fun savePackCalibration(capacityWh: Float, nominalVoltage: Float) {
        viewModelScope.launch {
            repository.preferences.updatePackCalibration(capacityWh, nominalVoltage)
        }
    }

    fun updateChargeAlert(enabled: Boolean, targetPercent: Int) {
        viewModelScope.launch {
            repository.preferences.updateChargeAlert(enabled, targetPercent)
        }
    }

    fun updateLowBatteryAlert(enabled: Boolean, thresholdPercent: Int) {
        viewModelScope.launch {
            repository.preferences.updateLowBatteryAlert(enabled, thresholdPercent)
        }
    }

    fun updateHighTempAlert(enabled: Boolean, thresholdCelsius: Float) {
        viewModelScope.launch {
            repository.preferences.updateHighTempAlert(enabled, thresholdCelsius)
        }
    }

    fun updateColdChargeAlert(enabled: Boolean, thresholdCelsius: Float) {
        viewModelScope.launch {
            repository.preferences.updateColdChargeAlert(enabled, thresholdCelsius)
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            repository.notifier.sendTestAlert()
        }
    }

    fun checkForUpdates(repoSlug: String) {
        viewModelScope.launch {
            repository.preferences.updateGithubRepoSlug(repoSlug)
            _updateCheckResult.value = UpdateCheckResult.Checking
            _updateCheckResult.value = repository.checkGitHubRelease(repoSlug)
        }
    }

    fun clearOfflineHistory() {
        viewModelScope.launch {
            repository.clearOfflineHistory()
        }
    }
}

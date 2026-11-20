package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ble.BleConnectionStatus
import com.example.data.ble.BleUiState
import com.example.data.ble.PowerMath
import com.example.data.local.AlertEventEntity
import com.example.data.local.OutputPortType
import com.example.data.local.PluggedDeviceEntity
import com.example.data.local.TelemetrySnapshotEntity
import com.example.data.prefs.AppSettings
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
    AWAITING,
    CONNECTING,
    LIVE_BLE,
    OFFLINE_CACHED
}

data class MonitorUiState(
    val snapshot: TelemetrySnapshotEntity? = null,
    val connectionMode: ConnectionMode = ConnectionMode.AWAITING,
    val hoursRemaining: Float? = null,
    val formattedEta: String = "--",
    val remainingWh: Float = 0f,
    val packCapacityWh: Float = 1024f,
    val pluggedDevices: List<PluggedDeviceEntity> = emptyList(),
    val activePluggedWatts: Float = 0f,
    val bleState: BleUiState = BleUiState(),
    val settings: AppSettings = AppSettings()
)

data class DiagnosticsUiState(
    val recentSnapshots: List<TelemetrySnapshotEntity> = emptyList(),
    val latestSnapshot: TelemetrySnapshotEntity? = null,
    val alerts: List<AlertEventEntity> = emptyList(),
    val isOfflineCached: Boolean = true
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

    val monitorUiState: StateFlow<MonitorUiState> = combine(
        repository.latestSnapshotFlow,
        repository.pluggedDevicesFlow,
        repository.bleStateFlow,
        repository.settingsFlow
    ) { snapshot, devices, ble, settings ->
        val mode = when {
            ble.status == BleConnectionStatus.CONNECTED -> ConnectionMode.LIVE_BLE
            ble.status == BleConnectionStatus.CONNECTING -> ConnectionMode.CONNECTING
            snapshot != null -> ConnectionMode.OFFLINE_CACHED
            else -> ConnectionMode.AWAITING
        }

        val hoursRemaining = snapshot?.let {
            PowerMath.computeHoursRemaining(
                socPercent = it.socPercent,
                powerWatts = it.powerWatts,
                packCapacityWh = settings.packCapacityWh,
                isCharging = it.isCharging
            )
        }

        val remainingWh = snapshot?.let {
            (it.socPercent / 100f) * settings.packCapacityWh
        } ?: 0f

        val activeDeviceWatts = devices.filter { it.isActive }
            .sumOf { it.estimatedWatts.toDouble() }
            .toFloat()

        MonitorUiState(
            snapshot = snapshot,
            connectionMode = mode,
            hoursRemaining = hoursRemaining,
            formattedEta = PowerMath.formatDurationHours(hoursRemaining),
            remainingWh = remainingWh,
            packCapacityWh = settings.packCapacityWh,
            pluggedDevices = devices,
            activePluggedWatts = activeDeviceWatts,
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
        repository.bleStateFlow
    ) { snapshots, latest, alerts, ble ->
        val offline = ble.status != BleConnectionStatus.CONNECTED
        DiagnosticsUiState(
            recentSnapshots = snapshots.reversed(),
            latestSnapshot = latest,
            alerts = alerts,
            isOfflineCached = offline
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
        repository.connectBleDevice(address, name)
        _isConnectionSheetOpen.value = false
    }

    fun disconnectBle() {
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

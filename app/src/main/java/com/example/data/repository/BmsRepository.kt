package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.ble.BleBmsManager
import com.example.data.ble.BleConnectionStatus
import com.example.data.ble.BleUiState
import com.example.data.ble.BmsSimulator
import com.example.data.ble.ParsedBmsFrame
import com.example.data.ble.SimulatorMode
import com.example.data.ble.SimulatorState
import com.example.data.local.AlertEventEntity
import com.example.data.local.BmsDao
import com.example.data.local.PluggedDeviceEntity
import com.example.data.local.TelemetrySnapshotEntity
import com.example.data.local.VoltCoreDatabase
import com.example.data.network.GitHubReleasesClient
import com.example.data.prefs.AppPreferences
import com.example.data.prefs.AppSettings
import com.example.notifications.BatteryAlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult
    data object Checking : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseTitle: String,
        val releaseNotes: String,
        val apkDownloadUrl: String,
        val apkFileName: String?,
        val apkSizeMb: Float?
    ) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

class BmsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database = VoltCoreDatabase.getInstance(appContext)
    private val dao: BmsDao = database.bmsDao()
    val preferences = AppPreferences(appContext)
    val bleManager = BleBmsManager(appContext)
    val simulator = BmsSimulator()
    val notifier = BatteryAlertNotifier(appContext, dao)

    val latestSnapshotFlow: Flow<TelemetrySnapshotEntity?> = dao.observeLatestSnapshot()
    val recentSnapshotsFlow: Flow<List<TelemetrySnapshotEntity>> = dao.observeRecentSnapshots(60)
    val pluggedDevicesFlow: Flow<List<PluggedDeviceEntity>> = dao.observePluggedDevices()
    val alertEventsFlow: Flow<List<AlertEventEntity>> = dao.observeAlertEvents()
    val settingsFlow: Flow<AppSettings> = preferences.settingsFlow
    val bleStateFlow: Flow<BleUiState> = bleManager.state
    val simulatorStateFlow: Flow<SimulatorState> = simulator.state

    init {
        scope.launch {
            bleManager.frames.collect { (deviceName, frame) ->
                persistAndEvaluateFrame(
                    frame = frame,
                    sourceType = "BLE",
                    deviceName = deviceName
                )
            }
        }
    }

    fun startSimulatedPack(mode: SimulatorMode = SimulatorMode.DISCHARGING) {
        bleManager.disconnect()
        simulator.start(mode) { frame ->
            scope.launch {
                persistAndEvaluateFrame(
                    frame = frame,
                    sourceType = "SIMULATED",
                    deviceName = "VoltCore Test Pack"
                )
            }
        }
    }

    fun stopSimulatedPack() {
        simulator.stop()
    }

    fun connectBleDevice(address: String, name: String) {
        simulator.stop()
        scope.launch {
            preferences.saveLastConnectedDevice(address, name)
        }
        bleManager.connect(address, name)
    }

    fun disconnectActiveSource() {
        simulator.stop()
        bleManager.disconnect()
    }

    private suspend fun persistAndEvaluateFrame(
        frame: ParsedBmsFrame,
        sourceType: String,
        deviceName: String
    ) {
        val powerWatts = frame.voltageVolts * frame.currentAmps
        val isCharging = frame.currentAmps > 0.1f
        val snapshot = TelemetrySnapshotEntity(
            timestampMs = System.currentTimeMillis(),
            socPercent = frame.socPercent,
            voltageVolts = frame.voltageVolts,
            currentAmps = frame.currentAmps,
            powerWatts = powerWatts,
            temperatureCelsius = frame.temperatureCelsius,
            mosfetTempCelsius = frame.mosfetTempCelsius,
            cycleCount = frame.cycleCount,
            stateOfHealthPercent = frame.stateOfHealthPercent,
            remainingCapacityAh = frame.remainingCapacityAh,
            nominalCapacityAh = frame.nominalCapacityAh,
            cellVoltagesCsv = frame.cellVoltages.joinToString(",") { "%.3f".format(it) },
            isCharging = isCharging,
            chargeMosEnabled = frame.chargeMosEnabled,
            dischargeMosEnabled = frame.dischargeMosEnabled,
            sourceType = sourceType,
            deviceName = deviceName
        )
        dao.insertSnapshot(snapshot)
        dao.pruneOldSnapshots()

        val settings = preferences.settingsFlow.first()
        notifier.evaluateAndNotify(snapshot, settings)
    }

    suspend fun upsertPluggedDevice(device: PluggedDeviceEntity) {
        if (device.id == 0L) {
            dao.insertPluggedDevice(device)
        } else {
            dao.updatePluggedDevice(device)
        }
        syncSimulatorLoadFromPluggedDevices()
    }

    suspend fun togglePluggedDevice(device: PluggedDeviceEntity) {
        dao.updatePluggedDevice(
            device.copy(
                isActive = !device.isActive,
                updatedAtMs = System.currentTimeMillis()
            )
        )
        syncSimulatorLoadFromPluggedDevices()
    }

    suspend fun deletePluggedDevice(id: Long) {
        dao.deletePluggedDeviceById(id)
        syncSimulatorLoadFromPluggedDevices()
    }

    private suspend fun syncSimulatorLoadFromPluggedDevices() {
        val activeLoad = dao.observePluggedDevices().first()
            .filter { it.isActive }
            .sumOf { it.estimatedWatts.toDouble() }
            .toFloat()
        if (activeLoad > 0f) {
            simulator.setLoadWatts(activeLoad)
        }
    }

    suspend fun clearOfflineHistory() {
        dao.clearSnapshots()
        dao.clearAlerts()
    }

    suspend fun checkGitHubRelease(repoInput: String): UpdateCheckResult {
        val parsed = GitHubReleasesClient.parseOwnerAndRepo(repoInput)
            ?: return UpdateCheckResult.Error("Enter a valid GitHub repository as owner/repo.")

        return try {
            val release = GitHubReleasesClient.service.getLatestRelease(parsed.first, parsed.second)
            val currentVersion = BuildConfig.VERSION_NAME
            if (GitHubReleasesClient.isNewerVersion(release.tagName, currentVersion)) {
                val apkAsset = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                }
                val downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl
                val sizeMb = apkAsset?.sizeBytes?.let { it / (1024f * 1024f) }
                UpdateCheckResult.UpdateAvailable(
                    latestVersion = release.tagName,
                    releaseTitle = release.name ?: release.tagName,
                    releaseNotes = release.body?.take(500)?.ifBlank { "New release available on GitHub." }
                        ?: "New release available on GitHub.",
                    apkDownloadUrl = downloadUrl,
                    apkFileName = apkAsset?.name,
                    apkSizeMb = sizeMb
                )
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(
                e.localizedMessage ?: "Unable to fetch releases from GitHub."
            )
        }
    }
}

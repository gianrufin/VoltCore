package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voltCoreDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voltcore_preferences"
)

data class AppSettings(
    val autoCalibrationEnabled: Boolean = true,
    val packCapacityWh: Float = 1024f,
    val packNominalVoltage: Float = 12.8f,
    val packNominalAh: Float = 80f,
    val detectedCellCount: Int = 0,
    val githubRepoSlug: String = "gianrufin/VoltCore",
    val chargeCompleteAlertEnabled: Boolean = true,
    val chargeTargetPercent: Int = 100,
    val lowBatteryAlertEnabled: Boolean = true,
    val lowBatteryThresholdPercent: Int = 15,
    val highTempAlertEnabled: Boolean = true,
    val highTempThresholdCelsius: Float = 45f,
    val lastDeviceAddress: String = "",
    val lastDeviceName: String = ""
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val AUTO_CALIBRATION = booleanPreferencesKey("auto_calibration_enabled")
        val PACK_CAPACITY_WH = floatPreferencesKey("pack_capacity_wh")
        val PACK_NOMINAL_VOLTAGE = floatPreferencesKey("pack_nominal_voltage")
        val PACK_NOMINAL_AH = floatPreferencesKey("pack_nominal_ah")
        val DETECTED_CELL_COUNT = intPreferencesKey("detected_cell_count")
        val GITHUB_REPO_SLUG = stringPreferencesKey("github_repo_slug")
        val CHARGE_COMPLETE_ALERT = booleanPreferencesKey("charge_complete_alert")
        val CHARGE_TARGET_PERCENT = intPreferencesKey("charge_target_percent")
        val LOW_BATTERY_ALERT = booleanPreferencesKey("low_battery_alert")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val HIGH_TEMP_ALERT = booleanPreferencesKey("high_temp_alert")
        val HIGH_TEMP_THRESHOLD = floatPreferencesKey("high_temp_threshold")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
    }

    val settingsFlow: Flow<AppSettings> = context.voltCoreDataStore.data.map { prefs ->
        AppSettings(
            autoCalibrationEnabled = prefs[Keys.AUTO_CALIBRATION] ?: true,
            packCapacityWh = prefs[Keys.PACK_CAPACITY_WH] ?: 1024f,
            packNominalVoltage = prefs[Keys.PACK_NOMINAL_VOLTAGE] ?: 12.8f,
            packNominalAh = prefs[Keys.PACK_NOMINAL_AH] ?: 80f,
            detectedCellCount = prefs[Keys.DETECTED_CELL_COUNT] ?: 0,
            githubRepoSlug = prefs[Keys.GITHUB_REPO_SLUG] ?: "gianrufin/VoltCore",
            chargeCompleteAlertEnabled = prefs[Keys.CHARGE_COMPLETE_ALERT] ?: true,
            chargeTargetPercent = prefs[Keys.CHARGE_TARGET_PERCENT] ?: 100,
            lowBatteryAlertEnabled = prefs[Keys.LOW_BATTERY_ALERT] ?: true,
            lowBatteryThresholdPercent = prefs[Keys.LOW_BATTERY_THRESHOLD] ?: 15,
            highTempAlertEnabled = prefs[Keys.HIGH_TEMP_ALERT] ?: true,
            highTempThresholdCelsius = prefs[Keys.HIGH_TEMP_THRESHOLD] ?: 45f,
            lastDeviceAddress = prefs[Keys.LAST_DEVICE_ADDRESS] ?: "",
            lastDeviceName = prefs[Keys.LAST_DEVICE_NAME] ?: ""
        )
    }

    suspend fun setAutoCalibrationEnabled(enabled: Boolean) {
        context.voltCoreDataStore.edit { prefs ->
            prefs[Keys.AUTO_CALIBRATION] = enabled
        }
    }

    suspend fun updatePackCalibration(
        capacityWh: Float,
        nominalVoltage: Float,
        nominalAh: Float = capacityWh / nominalVoltage.coerceAtLeast(1f),
        cellCount: Int = 0
    ) {
        context.voltCoreDataStore.edit { prefs ->
            prefs[Keys.PACK_CAPACITY_WH] = capacityWh.coerceIn(20f, 100000f)
            prefs[Keys.PACK_NOMINAL_VOLTAGE] = nominalVoltage.coerceIn(3.2f, 400f)
            prefs[Keys.PACK_NOMINAL_AH] = nominalAh.coerceIn(1f, 5000f)
            if (cellCount > 0) {
                prefs[Keys.DETECTED_CELL_COUNT] = cellCount
            }
        }
    }

    suspend fun updateGithubRepoSlug(slug: String) {
        context.voltCoreDataStore.edit { prefs ->
            prefs[Keys.GITHUB_REPO_SLUG] = slug.trim()
        }
    }

    suspend fun updateChargeAlert(enabled: Boolean, targetPercent: Int) {
        context.voltCoreDataStore.edit { prefs ->
            prefs[Keys.CHARGE_COMPLETE_ALERT] = enabled
            prefs[Keys.CHARGE_TARGET_PERCENT] = targetPercent.coerceIn(50, 100)
        }
    }

    suspend fun updateLowBatteryAlert(enabled: Boolean, thresholdPercent: Int) {
        context.voltCoreDataStore.edit { prefs ->
            prefs[Keys.LOW_BATTERY_ALERT] = enabled
            prefs[Keys.LOW_BATTERY_THRESHOLD] = thresholdPercent.coerceIn(5, 50)
        }
    }

    suspend fun updateHighTempAlert(enabled: Boolean, thresholdCelsius: Float) {
        context.voltCoreDataStore.edit { prefs ->
            prefs[Keys.HIGH_TEMP_ALERT] = enabled
            prefs[Keys.HIGH_TEMP_THRESHOLD] = thresholdCelsius.coerceIn(30f, 80f)
        }
    }

    suspend fun saveLastConnectedDevice(address: String, name: String) {
        context.voltCoreDataStore.edit { prefs ->
            prefs[Keys.LAST_DEVICE_ADDRESS] = address
            prefs[Keys.LAST_DEVICE_NAME] = name
        }
    }
}

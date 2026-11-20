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
    val packCapacityWh: Float = 1024f,
    val packNominalVoltage: Float = 51.2f,
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
        val PACK_CAPACITY_WH = floatPreferencesKey("pack_capacity_wh")
        val PACK_NOMINAL_VOLTAGE = floatPreferencesKey("pack_nominal_voltage")
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
            packCapacityWh = prefs[Keys.PACK_CAPACITY_WH] ?: 1024f,
            packNominalVoltage = prefs[Keys.PACK_NOMINAL_VOLTAGE] ?: 51.2f,
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

    suspend fun updatePackCalibration(capacityWh: Float, nominalVoltage: Float) {
        context.voltCoreDataStore.edit { prefs ->
            prefs[Keys.PACK_CAPACITY_WH] = capacityWh.coerceIn(50f, 50000f)
            prefs[Keys.PACK_NOMINAL_VOLTAGE] = nominalVoltage.coerceIn(3.2f, 400f)
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

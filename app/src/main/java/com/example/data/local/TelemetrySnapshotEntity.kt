package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_snapshots")
data class TelemetrySnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val socPercent: Int,
    val exactSocPercent: Float = socPercent.toFloat(),
    val voltageVolts: Float,
    val currentAmps: Float,
    val powerWatts: Float,
    val temperatureCelsius: Float,
    val mosfetTempCelsius: Float,
    val cycleCount: Int,
    val stateOfHealthPercent: Int,
    val remainingCapacityAh: Float,
    val nominalCapacityAh: Float,
    val cellVoltagesCsv: String,
    val isCharging: Boolean,
    val chargeMosEnabled: Boolean,
    val dischargeMosEnabled: Boolean,
    val sourceType: String,
    val deviceName: String
) {
    fun cellVoltagesList(): List<Float> =
        cellVoltagesCsv.split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
}

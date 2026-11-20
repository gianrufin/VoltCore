package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BmsAlertType {
    CHARGE_COMPLETE,
    LOW_SOC,
    HIGH_TEMP,
    OVER_CURRENT
}

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertType: String,
    val title: String,
    val message: String,
    val metricValue: String,
    val timestampMs: Long = System.currentTimeMillis()
)

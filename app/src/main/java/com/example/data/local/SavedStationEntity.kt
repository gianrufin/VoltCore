package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_stations")
data class SavedStationEntity(
    @PrimaryKey val address: String,
    val name: String,
    val protocolName: String = "Smart BMS",
    val nominalVoltage: Float = 12.8f,
    val nominalAh: Float = 80f,
    val capacityWh: Float = 1024f,
    val cellCount: Int = 0,
    val lastConnectedMs: Long = System.currentTimeMillis()
)

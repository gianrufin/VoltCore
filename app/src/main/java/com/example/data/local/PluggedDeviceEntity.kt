package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OutputPortType(val label: String) {
    USB_C("USB-C PD"),
    AC_OUT("AC Inverter"),
    DC_12V("12V DC"),
    ANDERSON("Anderson")
}

@Entity(tableName = "plugged_devices")
data class PluggedDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val portType: String,
    val estimatedWatts: Float,
    val isActive: Boolean = true,
    val updatedAtMs: Long = System.currentTimeMillis()
)

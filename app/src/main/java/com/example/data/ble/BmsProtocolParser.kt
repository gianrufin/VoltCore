package com.example.data.ble

import kotlin.math.abs

data class ParsedBmsFrame(
    val socPercent: Int,
    val voltageVolts: Float,
    val currentAmps: Float,
    val temperatureCelsius: Float,
    val mosfetTempCelsius: Float,
    val cycleCount: Int,
    val stateOfHealthPercent: Int,
    val remainingCapacityAh: Float,
    val nominalCapacityAh: Float,
    val cellVoltages: List<Float>,
    val chargeMosEnabled: Boolean,
    val dischargeMosEnabled: Boolean
)

object PowerMath {
    /**
     * Calculates estimated hours remaining until 100% full (when charging)
     * or until 0% empty (when discharging). Returns null if standby/idle.
     */
    fun computeHoursRemaining(
        socPercent: Int,
        powerWatts: Float,
        packCapacityWh: Float,
        isCharging: Boolean
    ): Float? {
        val absWatts = abs(powerWatts)
        if (absWatts < 1.5f || packCapacityWh <= 0f) return null

        return if (isCharging) {
            val remainingPercent = (100 - socPercent.coerceIn(0, 100)) / 100f
            if (remainingPercent <= 0f) 0f else (remainingPercent * packCapacityWh) / absWatts
        } else {
            val availablePercent = socPercent.coerceIn(0, 100) / 100f
            if (availablePercent <= 0f) 0f else (availablePercent * packCapacityWh) / absWatts
        }
    }

    fun formatDurationHours(hours: Float?): String {
        if (hours == null) return "--"
        if (hours <= 0f) return "0m"
        val totalMinutes = (hours * 60f).toInt().coerceAtLeast(1)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h >= 99 -> "99h+"
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}

object BmsProtocolParser {
    // JBD / Xiaoxiang basic info request: DD A5 03 00 FF FD 77
    val JBD_BASIC_INFO_CMD = byteArrayOf(
        0xDD.toByte(), 0xA5.toByte(), 0x03, 0x00, 0xFF.toByte(), 0xFD.toByte(), 0x77
    )

    // JBD / Xiaoxiang cell voltages request: DD A5 04 00 FF FC 77
    val JBD_CELL_VOLTAGES_CMD = byteArrayOf(
        0xDD.toByte(), 0xA5.toByte(), 0x04, 0x00, 0xFF.toByte(), 0xFC.toByte(), 0x77
    )

    /**
     * Parses a JBD/Xiaoxiang 0x03 Basic Info response frame.
     * Frame layout: [0]=0xDD, [1]=0x03, [2]=status(0x00), [3]=len, [4..]=payload, [-3..-2]=checksum, [-1]=0x77
     */
    fun parseJbdBasicInfo(frame: ByteArray, existingCells: List<Float> = emptyList()): ParsedBmsFrame? {
        if (frame.size < 27) return null
        if (frame[0] != 0xDD.toByte() || frame[1] != 0x03.toByte() || frame[2] != 0x00.toByte()) {
            return null
        }
        val dataLen = frame[3].toInt() and 0xFF
        if (frame.size < dataLen + 7) return null

        val p = 4 // payload start offset
        val voltageRaw = u16(frame[p], frame[p + 1])
        val currentRaw = s16(frame[p + 2], frame[p + 3])
        val remainingCapRaw = u16(frame[p + 4], frame[p + 5])
        val nominalCapRaw = u16(frame[p + 6], frame[p + 7])
        val cycleCount = u16(frame[p + 8], frame[p + 9])
        // p+10..p+11 prod date, p+12..p+15 balance bits, p+16..p+17 protection, p+18 sw ver
        val socPercent = (frame[p + 19].toInt() and 0xFF).coerceIn(0, 100)
        val mosStatus = frame[p + 20].toInt() and 0xFF
        val chargeMos = (mosStatus and 0x01) != 0
        val dischargeMos = (mosStatus and 0x02) != 0

        val ntcCount = if (dataLen >= 23) (frame[p + 22].toInt() and 0xFF) else 0
        val temps = mutableListOf<Float>()
        for (i in 0 until ntcCount) {
            val tOffset = p + 23 + (i * 2)
            if (tOffset + 1 < p + dataLen) {
                val deciKelvin = u16(frame[tOffset], frame[tOffset + 1])
                temps.add((deciKelvin - 2731) / 10f)
            }
        }
        val packTemp = temps.firstOrNull() ?: 25.0f
        val mosTemp = temps.getOrNull(1) ?: packTemp

        val voltage = voltageRaw / 100f
        val current = currentRaw / 100f
        val remainingAh = remainingCapRaw / 100f
        val nominalAh = (nominalCapRaw / 100f).coerceAtLeast(1f)
        val soh = (100 - (cycleCount / 40)).coerceIn(70, 100)

        return ParsedBmsFrame(
            socPercent = socPercent,
            voltageVolts = voltage,
            currentAmps = current,
            temperatureCelsius = packTemp,
            mosfetTempCelsius = mosTemp,
            cycleCount = cycleCount,
            stateOfHealthPercent = soh,
            remainingCapacityAh = remainingAh,
            nominalCapacityAh = nominalAh,
            cellVoltages = existingCells,
            chargeMosEnabled = chargeMos,
            dischargeMosEnabled = dischargeMos
        )
    }

    /**
     * Parses a JBD/Xiaoxiang 0x04 Cell Voltages response frame.
     */
    fun parseJbdCellVoltages(frame: ByteArray): List<Float>? {
        if (frame.size < 7) return null
        if (frame[0] != 0xDD.toByte() || frame[1] != 0x04.toByte() || frame[2] != 0x00.toByte()) {
            return null
        }
        val dataLen = frame[3].toInt() and 0xFF
        if (frame.size < dataLen + 7) return null
        val cellCount = dataLen / 2
        val cells = ArrayList<Float>(cellCount)
        for (i in 0 until cellCount) {
            val offset = 4 + (i * 2)
            val mv = u16(frame[offset], frame[offset + 1])
            cells.add(mv / 1000f)
        }
        return cells
    }

    /**
     * Parses a Daly 13-byte status frame (0xA5 0x01 0x90 ...).
     */
    fun parseDalySocFrame(frame: ByteArray, existing: ParsedBmsFrame? = null): ParsedBmsFrame? {
        if (frame.size < 13) return null
        if (frame[0] != 0xA5.toByte() || frame[2] != 0x90.toByte()) return null
        val voltage = u16(frame[4], frame[5]) / 10f
        val current = (u16(frame[8], frame[9]) - 30000) / 10f
        val soc = (u16(frame[10], frame[11]) / 10f).toInt().coerceIn(0, 100)

        return (existing ?: ParsedBmsFrame(
            socPercent = soc,
            voltageVolts = voltage,
            currentAmps = current,
            temperatureCelsius = 25f,
            mosfetTempCelsius = 26f,
            cycleCount = 0,
            stateOfHealthPercent = 100,
            remainingCapacityAh = 20f,
            nominalCapacityAh = 20f,
            cellVoltages = emptyList(),
            chargeMosEnabled = true,
            dischargeMosEnabled = true
        )).copy(
            socPercent = soc,
            voltageVolts = voltage,
            currentAmps = current
        )
    }

    private fun u16(msb: Byte, lsb: Byte): Int {
        return ((msb.toInt() and 0xFF) shl 8) or (lsb.toInt() and 0xFF)
    }

    private fun s16(msb: Byte, lsb: Byte): Int {
        val u = u16(msb, lsb)
        return if (u and 0x8000 != 0) u - 0x10000 else u
    }
}

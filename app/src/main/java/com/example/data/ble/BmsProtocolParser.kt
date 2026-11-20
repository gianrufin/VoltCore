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
    val dischargeMosEnabled: Boolean,
    val protocolName: String = "Smart BMS"
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
    // JBD / Xiaoxiang commands
    val JBD_BASIC_INFO_CMD = byteArrayOf(
        0xDD.toByte(), 0xA5.toByte(), 0x03, 0x00, 0xFF.toByte(), 0xFD.toByte(), 0x77
    )
    val JBD_CELL_VOLTAGES_CMD = byteArrayOf(
        0xDD.toByte(), 0xA5.toByte(), 0x04, 0x00, 0xFF.toByte(), 0xFC.toByte(), 0x77
    )

    // Daly commands (0xA5 0x40 <cmd> 0x08 00..00 <sum8>)
    val DALY_SOC_CMD = buildDalyCommand(0x90)
    val DALY_TEMP_CMD = buildDalyCommand(0x92)
    val DALY_MOS_CMD = buildDalyCommand(0x93)
    val DALY_STATUS_CMD = buildDalyCommand(0x94)
    val DALY_CELLS_CMD = buildDalyCommand(0x95)

    // JK BMS 20-byte commands (AA 55 90 EB <cmd> 00 ... <sum8>)
    val JK_DEVICE_INFO_CMD = buildJkCommand(0x97)
    val JK_CELL_INFO_CMD = buildJkCommand(0x96)

    fun buildDalyCommand(cmdId: Int): ByteArray {
        val frame = ByteArray(13)
        frame[0] = 0xA5.toByte()
        frame[1] = 0x40
        frame[2] = cmdId.toByte()
        frame[3] = 0x08
        var sum = 0
        for (i in 0..11) sum += (frame[i].toInt() and 0xFF)
        frame[12] = (sum and 0xFF).toByte()
        return frame
    }

    fun buildJkCommand(cmdId: Int): ByteArray {
        val frame = ByteArray(20)
        frame[0] = 0xAA.toByte()
        frame[1] = 0x55
        frame[2] = 0x90.toByte()
        frame[3] = 0xEB.toByte()
        frame[4] = cmdId.toByte()
        var sum = 0
        for (i in 0..18) sum += (frame[i].toInt() and 0xFF)
        frame[19] = (sum and 0xFF).toByte()
        return frame
    }

    /**
     * Parses a JBD/Xiaoxiang 0x03 Basic Info response frame.
     */
    fun parseJbdBasicInfo(frame: ByteArray, existingCells: List<Float> = emptyList()): ParsedBmsFrame? {
        if (frame.size < 27) return null
        if (frame[0] != 0xDD.toByte() || frame[1] != 0x03.toByte() || frame[2] != 0x00.toByte()) {
            return null
        }
        val dataLen = frame[3].toInt() and 0xFF
        if (frame.size < dataLen + 7) return null

        val p = 4
        val voltageRaw = u16BE(frame[p], frame[p + 1])
        val currentRaw = s16BE(frame[p + 2], frame[p + 3])
        val remainingCapRaw = u16BE(frame[p + 4], frame[p + 5])
        val nominalCapRaw = u16BE(frame[p + 6], frame[p + 7])
        val cycleCount = u16BE(frame[p + 8], frame[p + 9])
        val socPercent = (frame[p + 19].toInt() and 0xFF).coerceIn(0, 100)
        val mosStatus = frame[p + 20].toInt() and 0xFF
        val chargeMos = (mosStatus and 0x01) != 0
        val dischargeMos = (mosStatus and 0x02) != 0

        val ntcCount = if (dataLen >= 23) (frame[p + 22].toInt() and 0xFF) else 0
        val temps = mutableListOf<Float>()
        for (i in 0 until ntcCount) {
            val tOffset = p + 23 + (i * 2)
            if (tOffset + 1 < p + dataLen) {
                val deciKelvin = u16BE(frame[tOffset], frame[tOffset + 1])
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
            dischargeMosEnabled = dischargeMos,
            protocolName = "JBD / Xiaoxiang BMS"
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
            val mv = u16BE(frame[offset], frame[offset + 1])
            cells.add(mv / 1000f)
        }
        return cells
    }

    /**
     * Parses a Daly 13-byte response frame (0xA5 0x01 <cmd> 0x08 ... <sum8>).
     */
    fun parseDalyFrame(frame: ByteArray, existing: ParsedBmsFrame? = null): ParsedBmsFrame? {
        if (frame.size < 13) return null
        if (frame[0] != 0xA5.toByte()) return null
        val expectedSum = (0..11).sumOf { frame[it].toInt() and 0xFF } and 0xFF
        if ((frame[12].toInt() and 0xFF) != expectedSum) return null

        val base = existing ?: ParsedBmsFrame(
            socPercent = 0,
            voltageVolts = 0f,
            currentAmps = 0f,
            temperatureCelsius = 25f,
            mosfetTempCelsius = 25f,
            cycleCount = 0,
            stateOfHealthPercent = 100,
            remainingCapacityAh = 0f,
            nominalCapacityAh = 20f,
            cellVoltages = emptyList(),
            chargeMosEnabled = true,
            dischargeMosEnabled = true,
            protocolName = "Daly Smart BMS"
        )

        return when (frame[2].toInt() and 0xFF) {
            0x90 -> {
                val voltage = u16BE(frame[4], frame[5]) / 10f
                val current = (u16BE(frame[8], frame[9]) - 30000) / 10f
                val soc = (u16BE(frame[10], frame[11]) / 10f).toInt().coerceIn(0, 100)
                base.copy(
                    socPercent = soc,
                    voltageVolts = voltage,
                    currentAmps = current,
                    protocolName = "Daly Smart BMS"
                )
            }
            0x92 -> {
                val maxTempC = (frame[4].toInt() and 0xFF) - 40f
                val minTempC = (frame[6].toInt() and 0xFF) - 40f
                base.copy(
                    temperatureCelsius = (maxTempC + minTempC) / 2f,
                    mosfetTempCelsius = maxTempC,
                    protocolName = "Daly Smart BMS"
                )
            }
            0x93 -> {
                val chargeMos = (frame[5].toInt() and 0xFF) != 0
                val dischargeMos = (frame[6].toInt() and 0xFF) != 0
                val cycles = frame[7].toInt() and 0xFF
                base.copy(
                    chargeMosEnabled = chargeMos,
                    dischargeMosEnabled = dischargeMos,
                    cycleCount = if (cycles > 0) cycles else base.cycleCount,
                    protocolName = "Daly Smart BMS"
                )
            }
            0x94 -> {
                val cycles = u16BE(frame[9], frame[10])
                base.copy(
                    cycleCount = cycles,
                    protocolName = "Daly Smart BMS"
                )
            }
            else -> null
        }
    }

    /**
     * Parses a JK BMS 0x02 Cell Info telemetry frame (header 55 AA EB 90 02 ...).
     */
    fun parseJkCellInfoFrame(frame: ByteArray): ParsedBmsFrame? {
        if (frame.size < 190) return null
        if (frame[0] != 0x55.toByte() ||
            frame[1] != 0xAA.toByte() ||
            frame[2] != 0xEB.toByte() ||
            frame[3] != 0x90.toByte() ||
            frame[4] != 0x02.toByte()
        ) {
            return null
        }

        val cells = mutableListOf<Float>()
        for (i in 0 until 32) {
            val off = 6 + (i * 2)
            val mv = u16LE(frame[off], frame[off + 1])
            if (mv in 1500..4600) {
                cells.add(mv / 1000f)
            }
        }

        val mosTemp = s16LE(frame[144], frame[145]) / 10f
        val voltage = u32LE(frame, 150) / 1000f
        val current = s32LE(frame, 158) / 1000f
        val temp1 = s16LE(frame[162], frame[163]) / 10f
        val soc = (frame[173].toInt() and 0xFF).coerceIn(0, 100)
        val remAh = u32LE(frame, 174) / 1000f
        val nomAh = (u32LE(frame, 178) / 1000f).coerceAtLeast(1f)
        val cycles = u32LE(frame, 182).toInt().coerceAtLeast(0)
        val soh = (100 - (cycles / 40)).coerceIn(70, 100)

        return ParsedBmsFrame(
            socPercent = soc,
            voltageVolts = voltage,
            currentAmps = current,
            temperatureCelsius = temp1,
            mosfetTempCelsius = mosTemp,
            cycleCount = cycles,
            stateOfHealthPercent = soh,
            remainingCapacityAh = remAh,
            nominalCapacityAh = nomAh,
            cellVoltages = cells,
            chargeMosEnabled = true,
            dischargeMosEnabled = true,
            protocolName = "JK Smart BMS"
        )
    }

    private fun u16BE(msb: Byte, lsb: Byte): Int =
        ((msb.toInt() and 0xFF) shl 8) or (lsb.toInt() and 0xFF)

    private fun s16BE(msb: Byte, lsb: Byte): Int {
        val u = u16BE(msb, lsb)
        return if (u and 0x8000 != 0) u - 0x10000 else u
    }

    private fun u16LE(lsb: Byte, msb: Byte): Int =
        ((msb.toInt() and 0xFF) shl 8) or (lsb.toInt() and 0xFF)

    private fun s16LE(lsb: Byte, msb: Byte): Int {
        val u = u16LE(lsb, msb)
        return if (u and 0x8000 != 0) u - 0x10000 else u
    }

    private fun u32LE(buf: ByteArray, off: Int): Long {
        return (buf[off].toLong() and 0xFF) or
            ((buf[off + 1].toLong() and 0xFF) shl 8) or
            ((buf[off + 2].toLong() and 0xFF) shl 16) or
            ((buf[off + 3].toLong() and 0xFF) shl 24)
    }

    private fun s32LE(buf: ByteArray, off: Int): Int {
        return (buf[off].toInt() and 0xFF) or
            ((buf[off + 1].toInt() and 0xFF) shl 8) or
            ((buf[off + 2].toInt() and 0xFF) shl 16) or
            ((buf[off + 3].toInt() and 0xFF) shl 24)
    }
}

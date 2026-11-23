package com.example.data.ble

import com.example.data.prefs.TemperatureUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class ParsedBmsFrame(
    val socPercent: Int,
    val exactSocPercent: Float = socPercent.toFloat(),
    val voltageVolts: Float,
    val currentAmps: Float,
    val temperatureCelsius: Float,
    val mosfetTempCelsius: Float,
    val cycleCount: Int,
    val stateOfHealthPercent: Int,
    val remainingCapacityAh: Float,
    val nominalCapacityAh: Float,
    val cellCount: Int = 0,
    val cellVoltages: List<Float> = emptyList(),
    val chargeMosEnabled: Boolean = true,
    val dischargeMosEnabled: Boolean = true,
    val protocolName: String = "Smart BMS"
)

data class PackAutoCalibration(
    val cellCount: Int,
    val nominalVoltage: Float,
    val nominalCapacityAh: Float,
    val capacityWh: Float
)

data class PassThroughFlow(
    val grossInWatts: Float = 0f,
    val netPackWatts: Float = 0f,
    val loadOutWatts: Float = 0f,
    val isCharging: Boolean = false,
    val isPassThroughActive: Boolean = false,
    val isFloatCarryActive: Boolean = false
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
    ): Float? = computeHoursRemaining(
        socPercent = socPercent.toFloat(),
        powerWatts = powerWatts,
        packCapacityWh = packCapacityWh,
        isCharging = isCharging
    )

    fun computeHoursRemaining(
        socPercent: Float,
        powerWatts: Float,
        packCapacityWh: Float,
        isCharging: Boolean
    ): Float? {
        val absWatts = abs(powerWatts)
        if (absWatts < 1.5f || packCapacityWh <= 0f) return null

        val clampedSoc = socPercent.coerceIn(0f, 100f)
        return if (isCharging) {
            val remainingPercent = (100f - clampedSoc) / 100f
            if (remainingPercent <= 0f) 0f else (remainingPercent * packCapacityWh) / absWatts
        } else {
            val availablePercent = clampedSoc / 100f
            if (availablePercent <= 0f) 0f else (availablePercent * packCapacityWh) / absWatts
        }
    }

    /**
     * Resolves 0.1%-precision SoC from Remaining Ah / Nominal Ah when it agrees within +-1.5%
     * of the BMS-reported integer SoC byte; otherwise falls back cleanly to reported integer SoC.
     */
    fun resolveExactSocPercent(
        reportedSoc: Int,
        remainingAh: Float,
        nominalAh: Float
    ): Float {
        val baseSoc = reportedSoc.coerceIn(0, 100).toFloat()
        if (nominalAh >= 1f && remainingAh >= 0f) {
            val derived = ((remainingAh / nominalAh) * 100f).coerceIn(0f, 100f)
            val tenths = (derived * 10f).roundToInt() / 10f
            if (abs(tenths - baseSoc) <= 1.5f) {
                return tenths
            }
        }
        return baseSoc
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

    /**
     * Automatically infers cell topology, nominal voltage (e.g., 12.8V for 4S LiFePO4),
     * nominal Ah (e.g., 280 Ah), and total pack Wh (e.g., 3584 Wh) from live BMS telemetry.
     */
    fun inferPackCalibration(frame: ParsedBmsFrame): PackAutoCalibration? {
        if (frame.voltageVolts < 5f) return null

        val detectedCells = when {
            frame.cellVoltages.isNotEmpty() -> frame.cellVoltages.size
            frame.cellCount in 1..32 -> frame.cellCount
            else -> inferCellCountFromVoltage(frame.voltageVolts)
        }

        val avgCellV = frame.voltageVolts / detectedCells.coerceAtLeast(1)
        val perCellNominal = if (avgCellV > 3.68f) 3.7f else 3.2f
        val nominalVoltage = ((detectedCells * perCellNominal) * 10f).roundToInt() / 10f

        val nominalAh = when {
            frame.nominalCapacityAh >= 2f -> frame.nominalCapacityAh
            frame.remainingCapacityAh >= 1f && frame.exactSocPercent >= 5f -> {
                val est = frame.remainingCapacityAh / (frame.exactSocPercent / 100f)
                // Snap close to integer Ah when within 1.5% (e.g. 279.6 -> 280.0 Ah)
                val rounded = est.roundToInt().toFloat()
                if (abs(est - rounded) <= rounded * 0.015f) rounded else est
            }
            else -> return null
        }

        val capacityWh = (nominalAh * nominalVoltage).roundToInt().toFloat()
        return PackAutoCalibration(
            cellCount = detectedCells,
            nominalVoltage = nominalVoltage,
            nominalCapacityAh = nominalAh,
            capacityWh = capacityWh
        )
    }

    private fun inferCellCountFromVoltage(voltageVolts: Float): Int {
        return when {
            voltageVolts <= 16.5f -> 4   // 12.8V 4S LiFePO4 (10.0V - 14.6V)
            voltageVolts <= 31.5f -> 8   // 25.6V 8S LiFePO4
            voltageVolts <= 43.5f -> 12  // 38.4V 12S
            else -> 16                   // 51.2V 16S LiFePO4
        }
    }

    /**
     * Computes Gross Charge In, Net Battery Flow, and Tracked Load Out for single-shunt Smart BMS units.
     * Supports net-positive simultaneous charging+discharging and float-carry (~0W net while alternator carries loads).
     */
    fun computePassThroughFlow(
        netPowerWatts: Float,
        activePluggedWatts: Float,
        chargeMosEnabled: Boolean
    ): PassThroughFlow {
        val loadOut = activePluggedWatts.coerceAtLeast(0f)
        val hasActiveLoad = loadOut > 0.5f

        return when {
            netPowerWatts > 1.5f -> PassThroughFlow(
                grossInWatts = netPowerWatts + loadOut,
                netPackWatts = netPowerWatts,
                loadOutWatts = loadOut,
                isCharging = true,
                isPassThroughActive = hasActiveLoad,
                isFloatCarryActive = false
            )
            abs(netPowerWatts) <= 1.5f && chargeMosEnabled && hasActiveLoad -> PassThroughFlow(
                grossInWatts = loadOut,
                netPackWatts = 0f,
                loadOutWatts = loadOut,
                isCharging = false,
                isPassThroughActive = true,
                isFloatCarryActive = true
            )
            else -> PassThroughFlow(
                grossInWatts = 0f,
                netPackWatts = netPowerWatts,
                loadOutWatts = loadOut,
                isCharging = false,
                isPassThroughActive = false,
                isFloatCarryActive = false
            )
        }
    }

    fun computeDeviceSoloRuntimeHours(remainingWh: Float, deviceWatts: Float): Float? {
        if (remainingWh <= 0f || deviceWatts < 1f) return null
        return remainingWh / deviceWatts
    }

    fun convertTemp(celsius: Float, unit: TemperatureUnit): Float = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> (celsius * 9f / 5f) + 32f
    }

    fun tempUnitSymbol(unit: TemperatureUnit): String = when (unit) {
        TemperatureUnit.CELSIUS -> "°C"
        TemperatureUnit.FAHRENHEIT -> "°F"
    }

    fun formatTempWithUnit(celsius: Float, unit: TemperatureUnit, decimals: Int = 1): String {
        val converted = convertTemp(celsius, unit)
        val num = "%.${decimals}f".format(converted)
        return "$num${tempUnitSymbol(unit)}"
    }

    /**
     * Formats instantaneous shunt wattage with exact 0.1 W resolution from Voltage x Current.
     */
    fun formatWatts(watts: Float, signed: Boolean = false): String {
        val absW = abs(watts)
        if (absW < 0.05f) return "0.0 W"
        return when {
            signed && watts > 0f -> "+%.1f W".format(absW)
            signed && watts < 0f -> "-%.1f W".format(absW)
            else -> "%.1f W".format(absW)
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

    fun computeJbdChecksum(frame: ByteArray, startIndex: Int, endExclusive: Int): Int {
        var sum = 0
        for (i in startIndex until endExclusive) {
            sum += (frame[i].toInt() and 0xFF)
        }
        return (0x10000 - sum) and 0xFFFF
    }

    private fun verifyJbdChecksum(frame: ByteArray, dataLen: Int): Boolean {
        val chkOffset = 4 + dataLen
        if (frame.size < chkOffset + 3) return false
        if (frame[chkOffset + 2] != 0x77.toByte()) return false
        val actual = u16BE(frame[chkOffset], frame[chkOffset + 1])
        val expectedFromStatus = computeJbdChecksum(frame, 2, chkOffset)
        val expectedFromLen = computeJbdChecksum(frame, 3, chkOffset)
        return actual == expectedFromStatus || actual == expectedFromLen
    }

    /**
     * Parses a JBD/Xiaoxiang 0x03 Basic Info response frame with strict length + checksum check.
     */
    fun parseJbdBasicInfo(frame: ByteArray, existingCells: List<Float> = emptyList()): ParsedBmsFrame? {
        if (frame.size < 27) return null
        if (frame[0] != 0xDD.toByte() || frame[1] != 0x03.toByte() || frame[2] != 0x00.toByte()) {
            return null
        }
        val dataLen = frame[3].toInt() and 0xFF
        if (!verifyJbdChecksum(frame, dataLen)) return null

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
        val cellCount = if (dataLen >= 22) (frame[p + 21].toInt() and 0xFF).coerceIn(0, 32) else existingCells.size

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
        val nominalAh = nominalCapRaw / 100f
        val exactSoc = PowerMath.resolveExactSocPercent(socPercent, remainingAh, nominalAh)
        val soh = (100 - (cycleCount / 40)).coerceIn(70, 100)

        return ParsedBmsFrame(
            socPercent = socPercent,
            exactSocPercent = exactSoc,
            voltageVolts = voltage,
            currentAmps = current,
            temperatureCelsius = packTemp,
            mosfetTempCelsius = mosTemp,
            cycleCount = cycleCount,
            stateOfHealthPercent = soh,
            remainingCapacityAh = remainingAh,
            nominalCapacityAh = nominalAh,
            cellCount = cellCount,
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
        if (!verifyJbdChecksum(frame, dataLen)) return null

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
     * Note: Byte 7 in 0x93 is BMS heartbeat/life counter (0..255), NOT cycle count.
     */
    fun parseDalyFrame(frame: ByteArray, existing: ParsedBmsFrame? = null): ParsedBmsFrame? {
        if (frame.size < 13) return null
        if (frame[0] != 0xA5.toByte()) return null
        // Ignore echoed host request frames (addr = 0x40)
        if (frame[1] == 0x40.toByte()) return null
        if (frame[3] != 0x08.toByte()) return null

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
            nominalCapacityAh = 0f,
            cellCount = 0,
            cellVoltages = emptyList(),
            chargeMosEnabled = true,
            dischargeMosEnabled = true,
            protocolName = "Daly Smart BMS"
        )

        return when (frame[2].toInt() and 0xFF) {
            0x90 -> {
                val voltage = u16BE(frame[4], frame[5]) / 10f
                val current = (u16BE(frame[8], frame[9]) - 30000) / 10f
                val rawTenths = (u16BE(frame[10], frame[11]) / 10f).coerceIn(0f, 100f)
                val exactSoc = (rawTenths * 10f).roundToInt() / 10f
                val soc = exactSoc.roundToInt().coerceIn(0, 100)
                val updated = base.copy(
                    socPercent = soc,
                    exactSocPercent = exactSoc,
                    voltageVolts = voltage,
                    currentAmps = current,
                    protocolName = "Daly Smart BMS"
                )
                inferNominalForDaly(updated)
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
                // Bytes 8..11 = remaining capacity in mAh (u32BE)
                val remainingMah = u32BE(frame, 8)
                val remainingAh = remainingMah / 1000f
                val updated = base.copy(
                    chargeMosEnabled = chargeMos,
                    dischargeMosEnabled = dischargeMos,
                    remainingCapacityAh = if (remainingAh > 0f) remainingAh else base.remainingCapacityAh,
                    protocolName = "Daly Smart BMS"
                )
                inferNominalForDaly(updated)
            }
            0x94 -> {
                val cellCount = (frame[4].toInt() and 0xFF).coerceIn(1, 32)
                // Some Daly firmwares encode cycle count in bytes 9..10; ignore invalid spikes > 10000
                val rawCycles = u16BE(frame[9], frame[10])
                val cycleCount = if (rawCycles in 1..10000) rawCycles else base.cycleCount
                base.copy(
                    cellCount = cellCount,
                    cycleCount = cycleCount,
                    stateOfHealthPercent = (100 - (cycleCount / 40)).coerceIn(70, 100),
                    protocolName = "Daly Smart BMS"
                )
            }
            else -> null
        }
    }

    private fun inferNominalForDaly(frame: ParsedBmsFrame): ParsedBmsFrame {
        if (frame.nominalCapacityAh >= 2f) return frame
        if (frame.remainingCapacityAh >= 1f && frame.exactSocPercent >= 5f) {
            val est = frame.remainingCapacityAh / (frame.exactSocPercent / 100f)
            val rounded = est.roundToInt().toFloat()
            val cleanNominal = if (abs(est - rounded) <= rounded * 0.015f) rounded else est
            return frame.copy(nominalCapacityAh = cleanNominal)
        }
        return frame
    }

    /**
     * Parses a JK BMS 0x02 Cell Info telemetry frame (header 55 AA EB 90 02 ...) with CRC validation.
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

        // Verify trailing 8-bit sum checksum if full 300-byte frame is present
        if (frame.size >= 300) {
            val expectedCrc = (0 until 299).sumOf { frame[it].toInt() and 0xFF } and 0xFF
            if ((frame[299].toInt() and 0xFF) != expectedCrc) return null
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
        val nomAh = u32LE(frame, 178) / 1000f
        val cycles = u32LE(frame, 182).toInt().coerceAtLeast(0)
        val exactSoc = PowerMath.resolveExactSocPercent(soc, remAh, nomAh)
        val soh = (100 - (cycles / 40)).coerceIn(70, 100)

        return ParsedBmsFrame(
            socPercent = soc,
            exactSocPercent = exactSoc,
            voltageVolts = voltage,
            currentAmps = current,
            temperatureCelsius = temp1,
            mosfetTempCelsius = mosTemp,
            cycleCount = cycles,
            stateOfHealthPercent = soh,
            remainingCapacityAh = remAh,
            nominalCapacityAh = nomAh,
            cellCount = cells.size,
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

    private fun u32BE(buf: ByteArray, off: Int): Long {
        return ((buf[off].toLong() and 0xFF) shl 24) or
            ((buf[off + 1].toLong() and 0xFF) shl 16) or
            ((buf[off + 2].toLong() and 0xFF) shl 8) or
            (buf[off + 3].toLong() and 0xFF)
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

package com.example

import com.example.data.ble.BmsProtocolParser
import com.example.data.ble.PowerMath
import com.example.data.network.GitHubReleasesClient
import com.example.data.prefs.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `power math calculates charge and discharge ETA accurately`() {
        val dischargeHours = PowerMath.computeHoursRemaining(
            socPercent = 50,
            powerWatts = -250f,
            packCapacityWh = 1000f,
            isCharging = false
        )
        assertEquals(2.0f, dischargeHours!!, 0.01f)
        assertEquals("2h", PowerMath.formatDurationHours(dischargeHours))

        val chargeHours = PowerMath.computeHoursRemaining(
            socPercent = 75,
            powerWatts = 500f,
            packCapacityWh = 1000f,
            isCharging = true
        )
        assertEquals(0.5f, chargeHours!!, 0.01f)
        assertEquals("30m", PowerMath.formatDurationHours(chargeHours))
    }

    @Test
    fun `jbd basic info parser decodes voltage current soc temp and cycles and rejects bad checksum`() {
        val frame = byteArrayOf(
            0xDD.toByte(), 0x03, 0x00, 0x19,
            0x14, 0x00,
            0x03, 0xE8.toByte(),
            0x06, 0x40,
            0x07, 0xD0.toByte(),
            0x00, 0x78,
            0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00,
            0x10,
            0x50,
            0x03,
            0x10,
            0x01,
            0x0B, 0xD7.toByte(),
            0x00, 0x00,
            0x77
        )
        val checksum = BmsProtocolParser.computeJbdChecksum(frame, 2, 29)
        frame[29] = ((checksum shr 8) and 0xFF).toByte()
        frame[30] = (checksum and 0xFF).toByte()

        val parsed = BmsProtocolParser.parseJbdBasicInfo(frame)
        assertNotNull(parsed)
        assertEquals(51.20f, parsed!!.voltageVolts, 0.01f)
        assertEquals(10.00f, parsed.currentAmps, 0.01f)
        assertEquals(80, parsed.socPercent)
        assertEquals(120, parsed.cycleCount)
        assertEquals(30.0f, parsed.temperatureCelsius, 0.1f)
        assertEquals(16, parsed.cellCount)
        assertTrue(parsed.chargeMosEnabled)
        assertTrue(parsed.dischargeMosEnabled)

        // Corrupt a payload byte and verify checksum rejects fragmented/garbled frames
        val corrupt = frame.copyOf()
        corrupt[13] = 0xFF.toByte()
        org.junit.Assert.assertNull(BmsProtocolParser.parseJbdBasicInfo(corrupt))
    }

    @Test
    fun `daly parser decodes 0x90 and 0x94 cycles while ignoring 0x93 heartbeat counter`() {
        // 0x90: 13.2V (132 = 0x0084), +0.0A (30000 = 0x7530), 50.0% SoC (500 = 0x01F4)
        val socFrame = byteArrayOf(
            0xA5.toByte(), 0x01, 0x90.toByte(), 0x08,
            0x00, 0x84.toByte(),
            0x00, 0x00,
            0x75, 0x30,
            0x01, 0xF4.toByte(),
            0x00
        )
        socFrame[12] = ((0..11).sumOf { socFrame[it].toInt() and 0xFF } and 0xFF).toByte()
        val afterSoc = BmsProtocolParser.parseDalyFrame(socFrame)
        assertNotNull(afterSoc)
        assertEquals(13.2f, afterSoc!!.voltageVolts, 0.1f)
        assertEquals(50, afterSoc.socPercent)

        // 0x94: 4 cells, cycleCount = 6 (bytes 9..10)
        val statusFrame = byteArrayOf(
            0xA5.toByte(), 0x01, 0x94.toByte(), 0x08,
            0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00
        )
        statusFrame[12] = ((0..11).sumOf { statusFrame[it].toInt() and 0xFF } and 0xFF).toByte()
        val afterStatus = BmsProtocolParser.parseDalyFrame(statusFrame, afterSoc)
        assertNotNull(afterStatus)
        assertEquals(6, afterStatus!!.cycleCount)
        assertEquals(4, afterStatus.cellCount)

        // 0x93: byte 7 is BMS heartbeat = 200 (0xC8), bytes 8..11 remaining = 140,000 mAh (0x000222E0)
        val mosFrame = byteArrayOf(
            0xA5.toByte(), 0x01, 0x93.toByte(), 0x08,
            0x01, 0x01, 0x01, 0xC8.toByte(),
            0x00, 0x02, 0x22, 0xE0.toByte(),
            0x00
        )
        mosFrame[12] = ((0..11).sumOf { mosFrame[it].toInt() and 0xFF } and 0xFF).toByte()
        val afterMos = BmsProtocolParser.parseDalyFrame(mosFrame, afterStatus)
        assertNotNull(afterMos)
        // Must remain 6 cycles (never jump to heartbeat 200) and infer 280 Ah from 140 Ah @ 50% SoC
        assertEquals(6, afterMos!!.cycleCount)
        assertEquals(140.0f, afterMos.remainingCapacityAh, 0.1f)
        assertEquals(280.0f, afterMos.nominalCapacityAh, 0.5f)

        val autoCal = PowerMath.inferPackCalibration(afterMos)
        assertNotNull(autoCal)
        assertEquals(4, autoCal!!.cellCount)
        assertEquals(12.8f, autoCal.nominalVoltage, 0.01f)
        assertEquals(280.0f, autoCal.nominalCapacityAh, 0.5f)
        assertEquals(3584f, autoCal.capacityWh, 10f)
    }

    @Test
    fun `jk bms cell info parser decodes 0x02 frame`() {
        val frame = ByteArray(200)
        frame[0] = 0x55.toByte()
        frame[1] = 0xAA.toByte()
        frame[2] = 0xEB.toByte()
        frame[3] = 0x90.toByte()
        frame[4] = 0x02
        // Cell 1 = 3300mV (0x0CE4 LE)
        frame[6] = 0xE4.toByte()
        frame[7] = 0x0C
        // MOS temp = 31.0C (310 = 0x0136 LE)
        frame[144] = 0x36
        frame[145] = 0x01
        // Voltage = 52800mV (0x0000CE40 LE)
        frame[150] = 0x40
        frame[151] = 0xCE.toByte()
        // Current = 12500mA (0x000030D4 LE)
        frame[158] = 0xD4.toByte()
        frame[159] = 0x30
        // Temp1 = 27.5C (275 = 0x0113 LE)
        frame[162] = 0x13
        frame[163] = 0x01
        // SoC = 92%
        frame[173] = 92
        // Cycles = 45
        frame[182] = 45

        val parsed = BmsProtocolParser.parseJkCellInfoFrame(frame)
        assertNotNull(parsed)
        assertEquals(52.80f, parsed!!.voltageVolts, 0.01f)
        assertEquals(12.50f, parsed.currentAmps, 0.01f)
        assertEquals(27.5f, parsed.temperatureCelsius, 0.1f)
        assertEquals(92, parsed.socPercent)
        assertEquals(45, parsed.cycleCount)
        assertEquals(1, parsed.cellVoltages.size)
        assertEquals(3.300f, parsed.cellVoltages.first(), 0.001f)
    }

    @Test
    fun `github release version comparison works for semver tags`() {
        assertTrue(GitHubReleasesClient.isNewerVersion("v1.2.0", "1.0.0"))
        assertTrue(GitHubReleasesClient.isNewerVersion("1.0.1", "1.0.0"))
        assertFalse(GitHubReleasesClient.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(GitHubReleasesClient.isNewerVersion("v0.9.5", "1.0.0"))

        val parsed = GitHubReleasesClient.parseOwnerAndRepo("https://github.com/gianrufin/VoltCore/")
        assertEquals("gianrufin" to "VoltCore", parsed)
    }

    @Test
    fun `pass through flow computes gross alternator input net pack flow and load out`() {
        // Scenario 1: Car alternator puts +400W total while fridge uses 60W -> BMS net = +340W
        val chargingWithLoad = PowerMath.computePassThroughFlow(
            netPowerWatts = 340f,
            activePluggedWatts = 60f,
            chargeMosEnabled = true
        )
        assertTrue(chargingWithLoad.isPassThroughActive)
        assertTrue(chargingWithLoad.isCharging)
        assertEquals(400f, chargingWithLoad.grossInWatts, 0.1f)
        assertEquals(340f, chargingWithLoad.netPackWatts, 0.1f)
        assertEquals(60f, chargingWithLoad.loadOutWatts, 0.1f)

        // Scenario 2: Float carry at 100% SoC -> BMS net ~0W while alternator powers 50W fridge
        val floatCarry = PowerMath.computePassThroughFlow(
            netPowerWatts = 0f,
            activePluggedWatts = 50f,
            chargeMosEnabled = true
        )
        assertTrue(floatCarry.isPassThroughActive)
        assertTrue(floatCarry.isFloatCarryActive)
        assertEquals(50f, floatCarry.grossInWatts, 0.1f)

        // Scenario 3: Solo runtime for 60W fridge on 3584 Wh pack (50% = 1792 Wh)
        val soloHours = PowerMath.computeDeviceSoloRuntimeHours(1792f, 60f)
        assertNotNull(soloHours)
        assertEquals(29.86f, soloHours!!, 0.05f)
    }

    @Test
    fun `temperature conversion formats celsius and fahrenheit accurately`() {
        assertEquals(32.0f, PowerMath.convertTemp(0f, TemperatureUnit.FAHRENHEIT), 0.01f)
        assertEquals("0.0°C", PowerMath.formatTempWithUnit(0f, TemperatureUnit.CELSIUS))
        assertEquals("32.0°F", PowerMath.formatTempWithUnit(0f, TemperatureUnit.FAHRENHEIT))
        assertEquals("77°F", PowerMath.formatTempWithUnit(25f, TemperatureUnit.FAHRENHEIT, decimals = 0))
    }

    @Test
    fun `daly 0x90 and jbd resolve exact one-decimal socPercent accurately`() {
        // Daly 0x90 encodes SoC in 0.1% increments: 803 = 80.3%, 906 = 90.6%
        val soc803 = byteArrayOf(
            0xA5.toByte(), 0x01, 0x90.toByte(), 0x08,
            0x00, 0x84.toByte(), // 13.2V
            0x00, 0x00,
            0x75, 0x30, // 0.0A
            0x03, 0x23, // 803 -> 80.3%
            0x00
        )
        soc803[12] = ((0..11).sumOf { soc803[it].toInt() and 0xFF } and 0xFF).toByte()
        val parsedDaly = BmsProtocolParser.parseDalyFrame(soc803)
        assertNotNull(parsedDaly)
        assertEquals(80.3f, parsedDaly!!.exactSocPercent, 0.01f)
        assertEquals(80, parsedDaly.socPercent)

        // JBD/JK exact SoC resolution from Ah ratio: 224.84 Ah / 280.0 Ah = 80.3%
        assertEquals(80.3f, PowerMath.resolveExactSocPercent(80, 224.84f, 280f), 0.01f)
        // Drift guard: if Ah/Nom deviates >1.5% from reported integer SoC, keep reported SoC
        assertEquals(90.0f, PowerMath.resolveExactSocPercent(90, 200f, 280f), 0.01f)

        // Fractional hours remaining respects 80.3% vs 80%
        val hours = PowerMath.computeHoursRemaining(
            socPercent = 80.3f,
            powerWatts = -100f,
            packCapacityWh = 1000f,
            isCharging = false
        )
        assertEquals(8.03f, hours!!, 0.01f)
    }
}

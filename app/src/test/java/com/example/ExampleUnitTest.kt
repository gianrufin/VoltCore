package com.example

import com.example.data.ble.BmsProtocolParser
import com.example.data.ble.PowerMath
import com.example.data.network.GitHubReleasesClient
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
    fun `jbd basic info parser decodes voltage current soc temp and cycles`() {
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
        val parsed = BmsProtocolParser.parseJbdBasicInfo(frame)
        assertNotNull(parsed)
        assertEquals(51.20f, parsed!!.voltageVolts, 0.01f)
        assertEquals(10.00f, parsed.currentAmps, 0.01f)
        assertEquals(80, parsed.socPercent)
        assertEquals(120, parsed.cycleCount)
        assertEquals(30.0f, parsed.temperatureCelsius, 0.1f)
        assertTrue(parsed.chargeMosEnabled)
        assertTrue(parsed.dischargeMosEnabled)
    }

    @Test
    fun `daly parser decodes 0x90 voltage current soc and checksum`() {
        // 0xA5 0x01 0x90 0x08 [51.2V -> 512 = 0x0200] [0,0] [30050 -> +5.0A = 0x7562] [85.0% -> 850 = 0x0352]
        val frame = byteArrayOf(
            0xA5.toByte(), 0x01, 0x90.toByte(), 0x08,
            0x02, 0x00,
            0x00, 0x00,
            0x75, 0x62,
            0x03, 0x52,
            0x00
        )
        val sum = (0..11).sumOf { frame[it].toInt() and 0xFF } and 0xFF
        frame[12] = sum.toByte()

        val parsed = BmsProtocolParser.parseDalyFrame(frame)
        assertNotNull(parsed)
        assertEquals(51.2f, parsed!!.voltageVolts, 0.1f)
        assertEquals(5.0f, parsed.currentAmps, 0.1f)
        assertEquals(85, parsed.socPercent)
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
}

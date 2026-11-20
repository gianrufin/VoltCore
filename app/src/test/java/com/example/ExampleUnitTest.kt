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
    fun `github release version comparison works for semver tags`() {
        assertTrue(GitHubReleasesClient.isNewerVersion("v1.2.0", "1.0.0"))
        assertTrue(GitHubReleasesClient.isNewerVersion("1.0.1", "1.0.0"))
        assertFalse(GitHubReleasesClient.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(GitHubReleasesClient.isNewerVersion("v0.9.5", "1.0.0"))

        val parsed = GitHubReleasesClient.parseOwnerAndRepo("https://github.com/gianrufin/VoltCore/")
        assertEquals("gianrufin" to "VoltCore", parsed)
    }
}

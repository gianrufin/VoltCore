package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ble.BmsProtocolParser
import com.example.data.ble.PowerMath
import com.example.data.network.GitHubReleasesClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VoltCore", appName)
    }

    @Test
    fun `power math calculates charge and discharge ETA accurately`() {
        // 50% of 1000Wh = 500Wh remaining; at 250W discharge -> 2.0 hours
        val dischargeHours = PowerMath.computeHoursRemaining(
            socPercent = 50,
            powerWatts = -250f,
            packCapacityWh = 1000f,
            isCharging = false
        )
        assertEquals(2.0f, dischargeHours!!, 0.01f)
        assertEquals("2h", PowerMath.formatDurationHours(dischargeHours))

        // 75% of 1000Wh -> 250Wh to full; at 500W charge -> 0.5 hours (30m)
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
        // Minimal valid JBD 0x03 frame: 51.20V (0x1400), +10.00A (0x03E8), 16.00Ah rem (0x0640),
        // 20.00Ah nom (0x07D0), 120 cycles (0x0078), 80% SoC, MOS=0x03, 1 NTC = 30.0C (3031 = 0x0BD7)
        val frame = byteArrayOf(
            0xDD.toByte(), 0x03, 0x00, 0x19,
            0x14, 0x00, // voltage 5120 -> 51.20V
            0x03, 0xE8.toByte(), // current +1000 -> +10.00A
            0x06, 0x40, // rem cap
            0x07, 0xD0.toByte(), // nom cap
            0x00, 0x78, // cycles = 120
            0x00, 0x00, // prod date
            0x00, 0x00, 0x00, 0x00, // balance
            0x00, 0x00, // protection
            0x10, // sw version
            0x50, // SoC = 80%
            0x03, // charge + discharge MOS enabled
            0x10, // 16 cells
            0x01, // 1 NTC sensor
            0x0B, 0xD7.toByte(), // 3031 -> 30.0 C
            0x00, 0x00, // checksum placeholder
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
        assertTrue(GitHubReleasesClient.isNewerVersion("v1.2.0", "1.0"))
        assertTrue(GitHubReleasesClient.isNewerVersion("1.0.1", "1.0"))
        assertFalse(GitHubReleasesClient.isNewerVersion("v1.0", "1.0"))
        assertFalse(GitHubReleasesClient.isNewerVersion("v0.9.5", "1.0"))

        val parsed = GitHubReleasesClient.parseOwnerAndRepo("https://github.com/gianrufin/voltcore-bms/")
        assertEquals("gianrufin" to "voltcore-bms", parsed)
    }
}

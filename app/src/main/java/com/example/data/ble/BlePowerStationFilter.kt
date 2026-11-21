package com.example.data.ble

import java.util.UUID

data class BleDeviceClassification(
    val isPowerStation: Boolean,
    val badgeLabel: String? = null
)

object BlePowerStationFilter {
    val JBD_SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
    val JK_SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val DALY_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val FFA0_SERVICE_UUID: UUID = UUID.fromString("0000ffa0-0000-1000-8000-00805f9b34fb")
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")

    private val STRICT_BMS_UUIDS = setOf(
        JBD_SERVICE_UUID,
        DALY_SERVICE_UUID,
        NUS_SERVICE_UUID,
        FFA0_SERVICE_UUID
    )

    private val BRAND_AND_BMS_KEYWORDS = listOf(
        // Smart BMS chips & apps
        "bms", "jbd", "xiaoxiang", "daly", "overkill", "seplos", "ant bms", "pace bms",
        // Major portable power station brands & lines
        "ecoflow", "bluetti", "jackery", "anker", "solix", "pecron", "zendure", "superbase",
        "oukitel", "fossibot", "allpowers", "apower", "goalzero", "goal zero", "yeti",
        "vtoman", "dabbsson", "cworth", "growatt", "voltcore", "river 2", "river2",
        "delta 2", "delta2", "delta pro", "delta max", "explorer ",
        // LiFePO4 / smart battery & solar brands
        "renogy", "litime", "redodo", "amperetime", "ampere time", "chins",
        "battleborn", "battle born", "victron", "smartshunt", "epever", "powerqueen",
        "power queen", "lifepo4", "power station", "powerstation", "power pack", "powerpack",
        "battery", "solar", "inverter"
    )

    private val PREFIX_PATTERNS = listOf(
        "ef-", "ef_", "jk-", "jk_", "dl-", "dl_", "ant-", "bmv-",
        "eb3a", "eb55", "eb70", "ac50", "ac70", "ac180", "ac200", "ac300", "ac500",
        "ep500", "ep800"
    )

    private val WORD_BOUNDARY_REGEX = Regex("""\b(ups|lfp|pack|bms|station|river|delta)\b""")

    private val CONSUMER_EXCLUSION_REGEX = Regex(
        """(airpods|bravia|webos|roku|firetv|chromecast|earbud|headphone|headset|soundbar|smartwatch|miband|fitbit|garmin|amazfit|airtag|smarttag|keyboard|trackpad|printer|macbook|iphone|ipad|dualsense|toothbrush)|\b(tv|buds|speaker|jbl|bose|sonos|watch|tile|mouse|pixel|xbox|controller|pencil|stylus|scale|heater|blanket|thermostat|light|bulb)\b|tv$"""
    )

    fun classify(rawName: String?, advertisedUuids: List<UUID>): BleDeviceClassification {
        val trimmedName = rawName?.trim().orEmpty()
        val lower = trimmedName.lowercase()

        val hasHardBmsUuid = advertisedUuids.any { it == JBD_SERVICE_UUID || it == DALY_SERVICE_UUID }
        val hasStrictBmsOrUartUuid = advertisedUuids.any { it in STRICT_BMS_UUIDS }
        val hasJkUuid = advertisedUuids.any { it == JK_SERVICE_UUID }

        val isExcludedConsumer = lower.isNotEmpty() && CONSUMER_EXCLUSION_REGEX.containsMatchIn(lower)
        if (isExcludedConsumer && !hasHardBmsUuid) {
            return BleDeviceClassification(isPowerStation = false)
        }

        val nameMatchesPowerStation = lower.isNotEmpty() && (
            BRAND_AND_BMS_KEYWORDS.any { lower.contains(it) } ||
                PREFIX_PATTERNS.any { lower.startsWith(it) || lower.contains(" $it") } ||
                WORD_BOUNDARY_REGEX.containsMatchIn(lower)
            )

        return when {
            hasHardBmsUuid -> BleDeviceClassification(isPowerStation = true, badgeLabel = "BMS")
            hasStrictBmsOrUartUuid && (lower.isNotEmpty() || advertisedUuids.any { it == NUS_SERVICE_UUID }) -> {
                BleDeviceClassification(
                    isPowerStation = true,
                    badgeLabel = if (nameMatchesPowerStation) "STATION" else "BMS"
                )
            }
            hasJkUuid && lower.isNotEmpty() -> {
                BleDeviceClassification(
                    isPowerStation = true,
                    badgeLabel = if (nameMatchesPowerStation) "BMS" else "BLE BMS"
                )
            }
            nameMatchesPowerStation -> {
                val badge = if (lower.contains("bms") || lower.contains("jbd") || lower.contains("daly") || lower.startsWith("jk")) {
                    "BMS"
                } else {
                    "STATION"
                }
                BleDeviceClassification(isPowerStation = true, badgeLabel = badge)
            }
            else -> BleDeviceClassification(isPowerStation = false)
        }
    }
}

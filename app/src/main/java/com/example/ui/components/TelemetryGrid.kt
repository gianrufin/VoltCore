package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ble.PowerMath
import com.example.data.local.TelemetrySnapshotEntity
import com.example.data.prefs.TemperatureUnit
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CriticalCoral
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted

@Composable
fun TelemetryGrid(
    snapshot: TelemetrySnapshotEntity,
    highTempThreshold: Float,
    coldChargeThreshold: Float = 0f,
    packNominalVoltage: Float = 12.8f,
    packNominalAh: Float = 80f,
    detectedCellCount: Int = 0,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    modifier: Modifier = Modifier
) {
    val cells = snapshot.cellVoltagesList()
    val cellDeltaMv = if (cells.size >= 2) {
        ((cells.max() - cells.min()) * 1000f).toInt()
    } else null

    val effectiveCells = if (cells.isNotEmpty()) cells.size else detectedCellCount
    val voltageSubtitle = buildString {
        if (effectiveCells > 0) append("${effectiveCells}S · ")
        if (cellDeltaMv != null) {
            append("Δ ${cellDeltaMv} mV")
        } else {
            append("Nom ${"%.1f".format(packNominalVoltage)} V")
        }
    }

    val nominalAh = if (snapshot.nominalCapacityAh > 0f) snapshot.nominalCapacityAh else packNominalAh
    val cycleSubtitle = if (snapshot.remainingCapacityAh > 0f) {
        "${snapshot.remainingCapacityAh.toInt()}/${nominalAh.toInt()} Ah · ${snapshot.stateOfHealthPercent}% SoH"
    } else {
        "${nominalAh.toInt()} Ah · ${snapshot.stateOfHealthPercent}% SoH"
    }

    val tempWarningColor = when {
        snapshot.temperatureCelsius >= highTempThreshold -> CriticalCoral
        snapshot.isCharging && snapshot.temperatureCelsius <= coldChargeThreshold -> CautionAmber
        snapshot.temperatureCelsius >= highTempThreshold - 5f -> CautionAmber
        else -> null
    }

    val displayPackTemp = PowerMath.convertTemp(snapshot.temperatureCelsius, temperatureUnit)
    val displayMosTemp = PowerMath.formatTempWithUnit(snapshot.mosfetTempCelsius, temperatureUnit)
    val tempSymbol = PowerMath.tempUnitSymbol(temperatureUnit)

    val currentUnitColor = when {
        snapshot.currentAmps > 0.1f -> ElectricEmerald
        snapshot.currentAmps < -0.1f -> PrecisionCyan
        else -> SlateMuted
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "Voltage",
                value = "%.2f".format(snapshot.voltageVolts),
                unit = "V",
                subtitle = voltageSubtitle,
                valueColor = PureWhite,
                unitColor = SlateMuted,
                testTag = "metric_card_voltage",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Current",
                value = "%+.2f".format(snapshot.currentAmps),
                unit = "A",
                subtitle = when {
                    snapshot.currentAmps > 0.1f -> "Charging flow"
                    snapshot.currentAmps < -0.1f -> "Load draw"
                    else -> "Idle"
                },
                valueColor = PureWhite,
                unitColor = currentUnitColor,
                testTag = "metric_card_current",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "Temperature",
                value = "%.1f".format(displayPackTemp),
                unit = tempSymbol,
                subtitle = "MOS $displayMosTemp",
                valueColor = tempWarningColor ?: PureWhite,
                unitColor = tempWarningColor ?: ElectricEmerald,
                testTag = "metric_card_temperature",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Cycle count",
                value = "${snapshot.cycleCount}",
                unit = if (snapshot.cycleCount == 1) "cycle" else "cycles",
                subtitle = cycleSubtitle,
                valueColor = PureWhite,
                unitColor = SlateMuted,
                testTag = "metric_card_cycles",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    valueColor: Color,
    unitColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MonoMetricLarge,
                    color = valueColor,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = unitColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

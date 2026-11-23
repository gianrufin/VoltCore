package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ble.PowerMath
import com.example.data.local.TelemetrySnapshotEntity
import com.example.data.prefs.DisplayDensity
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CoralDim
import com.example.ui.theme.CriticalCoral
import com.example.ui.theme.CyanDim
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SubtleText
import kotlin.math.abs

@Composable
fun PowerFlowSection(
    snapshot: TelemetrySnapshotEntity,
    protocolName: String,
    displayDensity: DisplayDensity = DisplayDensity.MINIMAL,
    modifier: Modifier = Modifier
) {
    val exactWatts = snapshot.voltageVolts * snapshot.currentAmps
    val isCharging = exactWatts > 0.1f
    val isDischarging = exactWatts < -0.1f

    val inputText = if (isCharging) PowerMath.formatWatts(abs(exactWatts), signed = false).let { "+$it" } else "0.0 W"
    val outputText = if (isDischarging) PowerMath.formatWatts(abs(exactWatts), signed = false) else "0.0 W"
    val netText = PowerMath.formatWatts(exactWatts, signed = true)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("power_flow_section"),
        shape = MaterialTheme.shapes.medium,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.section_power_flow),
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CarbonElevated,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(
                        text = stringResource(R.string.power_flow_auto_badge),
                        style = MonoLabelSmall,
                        color = PrecisionCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowStatTile(
                    label = "Input",
                    value = inputText,
                    active = isCharging,
                    isPositive = true,
                    icon = Icons.Rounded.ArrowDownward,
                    testTag = "power_flow_input",
                    modifier = Modifier.weight(1f)
                )
                FlowStatTile(
                    label = "Output",
                    value = outputText,
                    active = isDischarging,
                    isPositive = false,
                    icon = Icons.Rounded.ArrowUpward,
                    testTag = "power_flow_output",
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = CarbonElevated,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = when {
                                isCharging -> ElectricEmerald
                                isDischarging -> PrecisionCyan
                                else -> SlateMuted
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${"%.2f".format(snapshot.voltageVolts)} V × ${"%+.2f".format(snapshot.currentAmps)} A",
                            style = MonoLabelSmall,
                            color = SlateMuted
                        )
                    }
                    Text(
                        text = netText,
                        style = MonoMetricMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isCharging -> ElectricEmerald
                            isDischarging -> PrecisionCyan
                            else -> PureWhite
                        },
                        modifier = Modifier.testTag("power_flow_net_shunt")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MosfetRailChip(
                    label = "Charge MOS",
                    enabled = snapshot.chargeMosEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("power_flow_charge_mos")
                )
                MosfetRailChip(
                    label = "Discharge MOS",
                    enabled = snapshot.dischargeMosEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("power_flow_discharge_mos")
                )
            }

            if (displayDensity == DisplayDensity.DETAILED || isDischarging || isCharging) {
                Text(
                    text = "Measured automatically at the $protocolName main shunt. AC inverter, 12V DC, and USB ports share this bus unless station firmware broadcasts individual port telemetry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubtleText
                )
            }
        }
    }
}

@Composable
private fun FlowStatTile(
    label: String,
    value: String,
    active: Boolean,
    isPositive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val accent = when {
        !active -> SlateMuted
        isPositive -> ElectricEmerald
        else -> PrecisionCyan
    }
    val iconBg = when {
        !active -> CarbonSurface
        isPositive -> EmeraldDim
        else -> CyanDim
    }

    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        color = CarbonElevated,
        border = BorderStroke(
            1.dp,
            if (active) accent.copy(alpha = 0.45f) else HairlineBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = iconBg,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MonoMetricMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) accent else PureWhite,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun MosfetRailChip(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) EmeraldDim else CoralDim,
        border = BorderStroke(
            1.dp,
            if (enabled) ElectricEmerald.copy(alpha = 0.35f) else CriticalCoral.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MonoLabelSmall,
                color = SlateMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (enabled) "On" else "Off",
                style = MonoLabelSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) ElectricEmerald else CriticalCoral
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ble.SimulatorMode
import com.example.data.ble.SimulatorState
import com.example.data.local.TelemetrySnapshotEntity
import com.example.ui.ConnectionMode
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CoralDim
import com.example.ui.theme.CriticalCoral
import com.example.ui.theme.CyanDim
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HeroSocTextStyle
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted
import kotlin.math.abs

@Composable
fun HeroPowerCard(
    snapshot: TelemetrySnapshotEntity,
    connectionMode: ConnectionMode,
    formattedEta: String,
    remainingWh: Float,
    packCapacityWh: Float,
    simulatorState: SimulatorState,
    onSelectSimulatorMode: (SimulatorMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCharging = snapshot.isCharging
    val absWatts = abs(snapshot.powerWatts)
    val isStandby = absWatts < 1.5f

    val accentColor by animateColorAsState(
        targetValue = when {
            snapshot.socPercent <= 15 -> CriticalCoral
            snapshot.temperatureCelsius >= 45f -> CautionAmber
            isCharging -> ElectricEmerald
            isStandby -> SlateMuted
            else -> PrecisionCyan
        },
        label = "heroAccent"
    )

    val badgeContainerColor = when {
        snapshot.socPercent <= 15 -> CoralDim
        snapshot.temperatureCelsius >= 45f -> AmberDim
        isCharging -> EmeraldDim
        isStandby -> CarbonElevated
        else -> CyanDim
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (snapshot.socPercent / 100f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "socProgress"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_power_card"),
        shape = MaterialTheme.shapes.large,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status & Pack Name Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeContainerColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isCharging -> Icons.Rounded.BatteryChargingFull
                                isStandby -> Icons.Rounded.Power
                                else -> Icons.Rounded.Bolt
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isCharging -> stringResource(R.string.label_charging)
                                isStandby -> stringResource(R.string.label_standby)
                                else -> stringResource(R.string.label_discharging)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                }

                Text(
                    text = "${remainingWh.toInt()} / ${packCapacityWh.toInt()} Wh",
                    style = MonoLabelSmall,
                    color = SlateMuted
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // EcoFlow-inspired Oversized Bold SoC Ring
            Box(
                modifier = Modifier.size(216.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val pad = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(pad, pad)

                    // Subtle track arc (270-degree open instrument gauge)
                    drawArc(
                        color = CarbonElevated,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active SoC arc
                    drawArc(
                        color = accentColor,
                        startAngle = 135f,
                        sweepAngle = 270f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${snapshot.socPercent}",
                            style = HeroSocTextStyle,
                            color = PureWhite,
                            modifier = Modifier.testTag("hero_soc_value")
                        )
                        Text(
                            text = "%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp, start = 2.dp)
                        )
                    }
                    Text(
                        text = snapshot.deviceName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMuted,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dual KPI Bar: Live Wattage & Hours Remaining
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(CarbonElevated)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCharging) {
                            stringResource(R.string.label_input_power)
                        } else {
                            stringResource(R.string.label_output_power)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isStandby) {
                                "0 W"
                            } else if (isCharging) {
                                "+${absWatts.toInt()} W"
                            } else {
                                "${absWatts.toInt()} W"
                            },
                            style = MonoMetricMedium,
                            color = PureWhite,
                            modifier = Modifier.testTag("hero_wattage_value")
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(HairlineBorder)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = SlateMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isStandby -> stringResource(R.string.label_idle_eta).uppercase()
                                isCharging -> stringResource(R.string.label_time_to_full).uppercase()
                                else -> stringResource(R.string.label_time_remaining).uppercase()
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedEta,
                        style = MonoMetricMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("hero_eta_value")
                    )
                }
            }

            // Quick Bench Controls when in Simulated Test Pack mode
            if (connectionMode == ConnectionMode.SIMULATED && simulatorState.isActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        SimulatorMode.DISCHARGING to "Discharge",
                        SimulatorMode.CHARGING to "Charge",
                        SimulatorMode.THERMAL_SPIKE to "Heat Test"
                    )
                    modes.forEach { (mode, label) ->
                        val selected = simulatorState.mode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectSimulatorMode(mode) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                selectedContainerColor = EmeraldDim,
                                selectedLabelColor = ElectricEmerald,
                                labelColor = SlateMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = HairlineBorder,
                                selectedBorderColor = ElectricEmerald
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sim_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.data.ble.PassThroughFlow
import com.example.data.local.TelemetrySnapshotEntity
import com.example.data.prefs.DisplayDensity
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun HeroPowerCard(
    snapshot: TelemetrySnapshotEntity,
    formattedEta: String,
    remainingWh: Float,
    packCapacityWh: Float,
    packNominalVoltage: Float = 12.8f,
    packNominalAh: Float = 80f,
    detectedCellCount: Int = 0,
    passThroughFlow: PassThroughFlow = PassThroughFlow(),
    displayDensity: DisplayDensity = DisplayDensity.MINIMAL,
    modifier: Modifier = Modifier
) {
    val isCharging = snapshot.isCharging
    val absWatts = abs(snapshot.powerWatts)
    val isStandby = absWatts < 1.5f
    val isPassThrough = passThroughFlow.isPassThroughActive
    val isActivelyFlowing = !isStandby || isPassThrough

    val accentColor by animateColorAsState(
        targetValue = when {
            snapshot.socPercent <= 15 -> CriticalCoral
            snapshot.temperatureCelsius >= 45f -> CautionAmber
            isCharging || passThroughFlow.isFloatCarryActive -> ElectricEmerald
            isStandby -> SlateMuted
            else -> PrecisionCyan
        },
        label = "heroAccent"
    )

    val badgeContainerColor = when {
        snapshot.socPercent <= 15 -> CoralDim
        snapshot.temperatureCelsius >= 45f -> AmberDim
        isCharging || passThroughFlow.isFloatCarryActive -> EmeraldDim
        isStandby -> CarbonElevated
        else -> CyanDim
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (snapshot.socPercent / 100f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "socProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "heroFlow")
    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowPhase"
    )
    val dotPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotPulse"
    )

    val specSummary = buildString {
        if (detectedCellCount > 0) append("${detectedCellCount}S · ")
        val nomAh = if (snapshot.nominalCapacityAh > 0f) snapshot.nominalCapacityAh else packNominalAh
        append("${nomAh.toInt()}Ah · ${"%.1f".format(packNominalVoltage)}V")
    }

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
                                isPassThrough -> Icons.Rounded.SyncAlt
                                isCharging -> Icons.Rounded.BatteryChargingFull
                                isStandby -> Icons.Rounded.Power
                                else -> Icons.Rounded.Bolt
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier
                                .size(16.dp)
                                .alpha(if (isActivelyFlowing) dotPulseAlpha else 1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isPassThrough -> "PASS-THROUGH"
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

            Box(
                modifier = Modifier.size(216.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val pad = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(pad, pad)

                    drawArc(
                        color = CarbonElevated,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    val activeSweep = 270f * animatedProgress
                    drawArc(
                        color = accentColor,
                        startAngle = 135f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Glanceable directional flow highlight while charging or discharging
                    if (isActivelyFlowing && activeSweep > 12f) {
                        val pulseSpan = (activeSweep * 0.24f).coerceIn(12f, 34f)
                        val travelSpan = (activeSweep - pulseSpan).coerceAtLeast(1f)
                        val directionPhase = if (isCharging || passThroughFlow.isFloatCarryActive) {
                            flowPhase
                        } else {
                            1f - flowPhase
                        }
                        val pulseStart = 135f + (directionPhase * travelSpan)
                        val edgeFade = sin(flowPhase * PI).toFloat().coerceIn(0.22f, 1f)
                        drawArc(
                            color = PureWhite.copy(alpha = 0.72f * edgeFade),
                            startAngle = pulseStart,
                            sweepAngle = pulseSpan,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth * 0.62f, cap = StrokeCap.Round)
                        )
                    }
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
                        letterSpacing = 1.2.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = specSummary,
                        style = MonoLabelSmall,
                        color = accentColor,
                        maxLines = 1,
                        modifier = Modifier.testTag("hero_pack_specs")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val showPassThroughLine = isPassThrough || displayDensity == DisplayDensity.DETAILED
            AnimatedVisibility(visible = showPassThroughLine) {
                Column {
                    PassThroughFlowLine(
                        flow = passThroughFlow,
                        flowPhase = flowPhase,
                        chargeMosEnabled = snapshot.chargeMosEnabled,
                        dischargeMosEnabled = snapshot.dischargeMosEnabled,
                        showMosPills = displayDensity == DisplayDensity.DETAILED
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

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
                                .background(
                                    accentColor.copy(
                                        alpha = if (isActivelyFlowing) dotPulseAlpha else 0.5f
                                    )
                                )
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
        }
    }
}

@Composable
private fun PassThroughFlowLine(
    flow: PassThroughFlow,
    flowPhase: Float,
    chargeMosEnabled: Boolean,
    dischargeMosEnabled: Boolean,
    showMosPills: Boolean
) {
    val inActive = flow.grossInWatts > 1.5f
    val outActive = flow.loadOutWatts > 0.5f
    val netText = when {
        flow.netPackWatts > 1.5f -> "+${flow.netPackWatts.toInt()} W"
        flow.netPackWatts < -1.5f -> "-${abs(flow.netPackWatts).toInt()} W"
        else -> "0 W"
    }
    val netColor = when {
        flow.netPackWatts > 1.5f -> ElectricEmerald
        flow.netPackWatts < -1.5f -> PrecisionCyan
        else -> SlateMuted
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pass_through_flow_line"),
        shape = MaterialTheme.shapes.medium,
        color = CarbonElevated,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "GROSS IN",
                        style = MonoLabelSmall,
                        color = SlateMuted
                    )
                    Text(
                        text = if (inActive) "+${flow.grossInWatts.toInt()} W" else "0 W",
                        style = MonoMetricMedium,
                        color = if (inActive) ElectricEmerald else SlateMuted,
                        modifier = Modifier.testTag("passthrough_gross_in")
                    )
                }

                AnimatedFlowDots(
                    active = inActive,
                    color = ElectricEmerald,
                    phase = flowPhase
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PACK NET",
                        style = MonoLabelSmall,
                        color = SlateMuted
                    )
                    Text(
                        text = netText,
                        style = MonoMetricMedium,
                        color = netColor,
                        modifier = Modifier.testTag("passthrough_net")
                    )
                }

                AnimatedFlowDots(
                    active = outActive,
                    color = PrecisionCyan,
                    phase = flowPhase
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "LOAD OUT",
                        style = MonoLabelSmall,
                        color = SlateMuted
                    )
                    Text(
                        text = "${flow.loadOutWatts.toInt()} W",
                        style = MonoMetricMedium,
                        color = if (outActive) PrecisionCyan else SlateMuted,
                        modifier = Modifier.testTag("passthrough_load_out")
                    )
                }
            }

            if (showMosPills) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MosPill(
                        label = "CHG MOS",
                        enabled = chargeMosEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    MosPill(
                        label = "DSG MOS",
                        enabled = dischargeMosEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedFlowDots(
    active: Boolean,
    color: Color,
    phase: Float
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val activeIdx = (phase * 3f).toInt().coerceIn(0, 2)
        for (i in 0..2) {
            val dotAlpha = when {
                !active -> 0.25f
                i == activeIdx -> 1f
                else -> 0.38f
            }
            Box(
                modifier = Modifier
                    .size(if (active && i == activeIdx) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background((if (active) color else SlateMuted).copy(alpha = dotAlpha))
            )
        }
    }
}

@Composable
private fun MosPill(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (enabled) EmeraldDim else CoralDim
    ) {
        Text(
            text = "$label: ${if (enabled) "ON" else "OFF"}",
            style = MonoLabelSmall,
            color = if (enabled) ElectricEmerald else CriticalCoral,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

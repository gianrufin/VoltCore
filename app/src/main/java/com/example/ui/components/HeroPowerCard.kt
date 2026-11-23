package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ble.PassThroughFlow
import com.example.data.local.TelemetrySnapshotEntity
import com.example.data.prefs.DisplayDensity
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CoralDim
import com.example.ui.theme.CriticalCoral
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HeroSocTextStyle
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SubtleText
import kotlin.math.abs

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

    val hasWarning = snapshot.exactSocPercent <= 15f || snapshot.temperatureCelsius >= 45f
    val stateAccentColor by animateColorAsState(
        targetValue = when {
            snapshot.exactSocPercent <= 15f -> CriticalCoral
            snapshot.temperatureCelsius >= 45f -> CautionAmber
            isCharging || passThroughFlow.isFloatCarryActive -> ElectricEmerald
            isStandby -> SlateMuted
            else -> PrecisionCyan
        },
        label = "stateAccent"
    )

    val gaugeArcColor by animateColorAsState(
        targetValue = when {
            snapshot.exactSocPercent <= 15f -> CriticalCoral
            snapshot.temperatureCelsius >= 45f -> CautionAmber
            else -> PrecisionCyan
        },
        label = "gaugeArcColor"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = (snapshot.exactSocPercent / 100f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "socProgress"
    )

    val specSummary = buildString {
        if (detectedCellCount > 0) append("${detectedCellCount}S · ")
        val nomAh = if (snapshot.nominalCapacityAh > 0f) snapshot.nominalCapacityAh else packNominalAh
        append("${nomAh.toInt()} Ah · ${"%.1f".format(packNominalVoltage)} V")
    }

    val stateText = when {
        isPassThrough -> "Pass-through"
        isCharging -> "Charging"
        isStandby -> "Standby"
        else -> "Discharging"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_power_card"),
        shape = MaterialTheme.shapes.medium,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(stateAccentColor)
                    )
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (hasWarning) stateAccentColor else PureWhite
                    )
                }

                Text(
                    text = "${remainingWh.toInt()} / ${packCapacityWh.toInt()} Wh",
                    style = MonoLabelSmall,
                    color = PureWhite
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .size(184.dp)
                    .testTag("hero_liquid_battery_box"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("hero_liquid_battery_canvas")
                ) {
                    val strokeWidth = 12.dp.toPx()
                    val radius = (size.minDimension / 2f) - strokeWidth
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    val soc = snapshot.exactSocPercent.coerceIn(0f, 100f)
                    val progress = soc / 100f

                    // Green to Amber to Red gradient color interpolation
                    val juiceColor = when {
                        soc >= 50f -> {
                            val t = (soc - 50f) / 50f // 0 at 50%, 1 at 100%
                            Color(
                                red = 0.96f + (0.18f - 0.96f) * t,
                                green = 0.62f + (0.90f - 0.62f) * t,
                                blue = 0.04f + (0.60f - 0.04f) * t
                            )
                        }
                        else -> {
                            val t = soc / 50f // 0 at 0%, 1 at 50%
                            Color(
                                red = 0.94f + (0.96f - 0.94f) * t,
                                green = 0.27f + (0.62f - 0.27f) * t,
                                blue = 0.27f + (0.04f - 0.27f) * t
                            )
                        }
                    }

                    // Outer track ring
                    drawCircle(
                        color = CarbonElevated,
                        radius = radius + strokeWidth / 2f,
                        center = Offset(cx, cy),
                        style = Stroke(width = strokeWidth)
                    )

                    // Clipped liquid container
                    clipPath(
                        path = Path().apply {
                            addOval(androidx.compose.ui.geometry.Rect(cx - radius, cy - radius, cx + radius, cy + radius))
                        }
                    ) {
                        val liquidTopY = cy + radius - (radius * 2f * animatedProgress)
                        val wavePath = Path().apply {
                            moveTo(cx - radius - 20f, cy + radius + 20f)
                            lineTo(cx - radius - 20f, liquidTopY)
                            // Sine wave with dynamic tilt
                            val waveWidth = radius * 2.5f
                            val steps = 20
                            for (i in 0..steps) {
                                val x = (cx - radius) + (i * (waveWidth / steps))
                                val angle = (i.toFloat() / steps) * (Math.PI.toFloat() * 2f) + (System.currentTimeMillis() * 0.003f)
                                val y = liquidTopY + kotlin.math.sin(angle) * 6f
                                lineTo(x, y)
                            }
                            lineTo(cx + radius + 20f, cy + radius + 20f)
                            close()
                        }

                        drawPath(
                            path = wavePath,
                            color = juiceColor.copy(alpha = 0.85f)
                        )
                    }

                    // Outer active progress stroke arc
                    drawArc(
                        color = juiceColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "%.1f".format(snapshot.exactSocPercent),
                            style = HeroSocTextStyle,
                            color = PureWhite,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("hero_soc_value")
                        )
                        Text(
                            text = "%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateMuted,
                            modifier = Modifier.padding(bottom = 7.dp, start = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = snapshot.deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = specSummary,
                        style = MonoLabelSmall,
                        color = SubtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        chargeMosEnabled = snapshot.chargeMosEnabled,
                        dischargeMosEnabled = snapshot.dischargeMosEnabled,
                        showMosPills = displayDensity == DisplayDensity.DETAILED
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCharging) "Input" else "Output",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isStandby) {
                                "0 W"
                            } else if (isCharging) {
                                "+${absWatts.toInt()} W"
                            } else {
                                "${absWatts.toInt()} W"
                            },
                            style = MonoMetricMedium,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isStandby -> PureWhite
                                isCharging -> ElectricEmerald
                                else -> PrecisionCyan
                            },
                            modifier = Modifier.testTag("hero_wattage_value")
                        )
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
                        Text(
                            text = when {
                                isStandby -> stringResource(R.string.label_idle_eta)
                                isCharging -> "To full"
                                else -> "Est. remaining"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedEta,
                            style = MonoMetricMedium,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            modifier = Modifier.testTag("hero_eta_value")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassThroughFlowLine(
    flow: PassThroughFlow,
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
        shape = RoundedCornerShape(10.dp),
        color = CarbonElevated,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "In",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted
                    )
                    Text(
                        text = if (inActive) "+${flow.grossInWatts.toInt()} W" else "0 W",
                        style = MonoMetricMedium,
                        color = if (inActive) ElectricEmerald else SlateMuted,
                        modifier = Modifier.testTag("passthrough_gross_in")
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Pack net",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted
                    )
                    Text(
                        text = netText,
                        style = MonoMetricMedium,
                        color = netColor,
                        modifier = Modifier.testTag("passthrough_net")
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Load",
                        style = MaterialTheme.typography.bodyMedium,
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
                    MosStatusTag(
                        label = "Charge MOS",
                        enabled = chargeMosEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    MosStatusTag(
                        label = "Discharge MOS",
                        enabled = dischargeMosEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MosStatusTag(
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
        Text(
            text = "$label: ${if (enabled) "On" else "Off"}",
            style = MonoLabelSmall,
            color = if (enabled) ElectricEmerald else CriticalCoral,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ble.PassThroughFlow
import com.example.data.ble.PowerMath
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
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tan

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
    val context = LocalContext.current
    val isCharging = snapshot.isCharging
    val exactWatts = snapshot.voltageVolts * snapshot.currentAmps
    val absWatts = abs(exactWatts)
    val isStandby = absWatts < 0.2f
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

    val animatedProgress by animateFloatAsState(
        targetValue = (snapshot.exactSocPercent / 100f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "socProgress"
    )

    // Continuous 60fps wave phase loop
    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    wavePhase = (wavePhase + dt * 2.9f) % (2f * PI.toFloat())
                }
                lastNanos = frameNanos
            }
        }
    }

    // Gyro / gravity / accelerometer fluid horizon tilt with low-pass smoothing
    var tiltRadians by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || sensor == null) {
            onDispose {}
        } else {
            var filteredX = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    filteredX += 0.14f * (event.values[0] - filteredX)
                    tiltRadians = (-filteredX / 9.81f * 0.44f).coerceIn(-0.36f, 0.36f)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

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

    val wattageDisplay = when {
        isStandby -> "0.0 W"
        isCharging -> "+${PowerMath.formatWatts(absWatts)}"
        else -> PowerMath.formatWatts(absWatts)
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
                    .size(212.dp)
                    .testTag("hero_liquid_battery_box"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("hero_liquid_battery_canvas")
                ) {
                    val strokeWidth = 11.dp.toPx()
                    val outerRadius = (size.minDimension / 2f) - strokeWidth
                    val liquidRadius = outerRadius - (strokeWidth / 2f) - 2.dp.toPx()
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    val soc = (animatedProgress * 100f).coerceIn(0f, 100f)

                    // Smooth Green (100%) -> Amber (50%) -> Red (0%) interpolation
                    val juiceColor = when {
                        soc >= 50f -> {
                            val t = (soc - 50f) / 50f
                            Color(
                                red = 0.96f + (0.18f - 0.96f) * t,
                                green = 0.62f + (0.90f - 0.62f) * t,
                                blue = 0.04f + (0.60f - 0.04f) * t
                            )
                        }
                        else -> {
                            val t = soc / 50f
                            Color(
                                red = 0.94f + (0.96f - 0.94f) * t,
                                green = 0.27f + (0.62f - 0.27f) * t,
                                blue = 0.27f + (0.04f - 0.27f) * t
                            )
                        }
                    }

                    // Outer dark track ring
                    drawCircle(
                        color = CarbonElevated,
                        radius = outerRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = strokeWidth)
                    )

                    // Liquid interior cavity background
                    drawCircle(
                        color = OledBlack,
                        radius = liquidRadius,
                        center = Offset(cx, cy)
                    )

                    // Clipped 60fps fluid wave with gyroscopic tilt
                    clipPath(
                        path = Path().apply {
                            addOval(
                                Rect(
                                    cx - liquidRadius,
                                    cy - liquidRadius,
                                    cx + liquidRadius,
                                    cy + liquidRadius
                                )
                            )
                        }
                    ) {
                        val baseLevelY = cy + liquidRadius - (liquidRadius * 2f * animatedProgress)
                        val waveAmplitude = when {
                            animatedProgress <= 0.02f || animatedProgress >= 0.985f -> 2.dp.toPx()
                            else -> 5.5.dp.toPx()
                        }
                        val tiltSlope = tan(tiltRadians)
                        val stepCount = 36
                        val startX = cx - liquidRadius - 12f
                        val spanW = (liquidRadius * 2f) + 24f

                        // Secondary back wave for subtle depth
                        val backWave = Path().apply {
                            moveTo(startX, cy + liquidRadius + 24f)
                            for (i in 0..stepCount) {
                                val x = startX + (i.toFloat() / stepCount) * spanW
                                val dx = x - cx
                                val angle = (i.toFloat() / stepCount) * (2f * PI.toFloat()) - (wavePhase * 0.78f)
                                val y = baseLevelY + (dx * tiltSlope) + sin(angle) * (waveAmplitude * 0.8f)
                                lineTo(x, y)
                            }
                            lineTo(startX + spanW, cy + liquidRadius + 24f)
                            close()
                        }
                        drawPath(path = backWave, color = juiceColor.copy(alpha = 0.42f))

                        // Primary front wave
                        val frontWave = Path().apply {
                            moveTo(startX, cy + liquidRadius + 24f)
                            for (i in 0..stepCount) {
                                val x = startX + (i.toFloat() / stepCount) * spanW
                                val dx = x - cx
                                val angle = (i.toFloat() / stepCount) * (2.2f * PI.toFloat()) + wavePhase
                                val y = baseLevelY + (dx * tiltSlope) + sin(angle) * waveAmplitude
                                lineTo(x, y)
                            }
                            lineTo(startX + spanW, cy + liquidRadius + 24f)
                            close()
                        }
                        drawPath(path = frontWave, color = juiceColor.copy(alpha = 0.92f))
                    }

                    // Outer active progress arc
                    drawArc(
                        color = juiceColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(cx - outerRadius, cy - outerRadius),
                        size = Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // High-contrast dark center backdrop disc behind bold SoC readout
                Surface(
                    modifier = Modifier
                        .size(142.dp)
                        .testTag("hero_center_contrast_disc"),
                    shape = CircleShape,
                    color = OledBlack,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
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
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = snapshot.deviceName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateMuted,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = specSummary,
                            style = MonoLabelSmall,
                            color = SlateMuted,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("hero_pack_specs")
                        )
                    }
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
                            text = wattageDisplay,
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
    val inActive = flow.grossInWatts > 0.2f
    val outActive = flow.loadOutWatts > 0.2f
    val netText = PowerMath.formatWatts(flow.netPackWatts, signed = true)
    val netColor = when {
        flow.netPackWatts > 0.2f -> ElectricEmerald
        flow.netPackWatts < -0.2f -> PrecisionCyan
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
                        text = if (inActive) "+${PowerMath.formatWatts(flow.grossInWatts)}" else "0.0 W",
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
                        text = PowerMath.formatWatts(flow.loadOutWatts),
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

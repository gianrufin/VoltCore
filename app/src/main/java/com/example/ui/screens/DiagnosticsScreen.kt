package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ble.PowerMath
import com.example.data.local.AlertEventEntity
import com.example.ui.DiagnosticsUiState
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CriticalCoral
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    uiState: DiagnosticsUiState,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateTimeFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    val timeRangeContext = remember(uiState.recentSnapshots) {
        val list = uiState.recentSnapshots
        when {
            list.isEmpty() -> null
            list.size == 1 -> "Recorded ${dateTimeFormatter.format(Date(list.first().timestampMs))}"
            else -> {
                val firstMs = list.first().timestampMs
                val lastMs = list.last().timestampMs
                val spanHours = (lastMs - firstMs) / 3_600_000f
                val startStr = if (spanHours >= 20f) {
                    dateTimeFormatter.format(Date(firstMs))
                } else {
                    timeFormatter.format(Date(firstMs))
                }
                val endStr = if (spanHours >= 20f) {
                    dateTimeFormatter.format(Date(lastMs))
                } else {
                    timeFormatter.format(Date(lastMs))
                }
                "$startStr – $endStr"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = PureWhite
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (uiState.isOfflineCached) CarbonElevated else EmeraldDim,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.isOfflineCached) HairlineBorder else ElectricEmerald.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = if (uiState.isOfflineCached) {
                                "Cached · ${uiState.recentSnapshots.size}"
                            } else {
                                "Live · ${uiState.recentSnapshots.size}"
                            },
                            style = MonoLabelSmall,
                            color = if (uiState.isOfflineCached) SlateMuted else ElectricEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trends",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite
                    )
                    if (timeRangeContext != null) {
                        Text(
                            text = timeRangeContext,
                            style = MonoLabelSmall,
                            color = SlateMuted
                        )
                    }
                }
            }

            if (uiState.recentSnapshots.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_diagnostics_history"),
                        shape = MaterialTheme.shapes.medium,
                        color = CarbonSurface,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "No telemetry history yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite
                            )
                            Text(
                                text = stringResource(R.string.empty_diagnostics_history),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateMuted
                            )
                        }
                    }
                }
            } else {
                item {
                    TelemetryTrendCard(
                        title = "Voltage",
                        latestFormatted = "%.2f V".format(uiState.recentSnapshots.last().voltageVolts),
                        values = uiState.recentSnapshots.map { it.voltageVolts },
                        unit = "V",
                        valueDecimals = 2,
                        timeRangeLabel = timeRangeContext.orEmpty(),
                        lineColor = PrecisionCyan,
                        testTag = "chart_voltage"
                    )
                }

                item {
                    TelemetryTrendCard(
                        title = "Current",
                        latestFormatted = "%+.2f A".format(uiState.recentSnapshots.last().currentAmps),
                        values = uiState.recentSnapshots.map { it.currentAmps },
                        unit = "A",
                        valueDecimals = 2,
                        timeRangeLabel = timeRangeContext.orEmpty(),
                        lineColor = PrecisionCyan,
                        testTag = "chart_current"
                    )
                }

                item {
                    val tempUnit = uiState.temperatureUnit
                    val tempSymbol = PowerMath.tempUnitSymbol(tempUnit)
                    val latestTempC = uiState.recentSnapshots.last().temperatureCelsius
                    TelemetryTrendCard(
                        title = "Temperature",
                        latestFormatted = "${"%.1f".format(PowerMath.convertTemp(latestTempC, tempUnit))} $tempSymbol",
                        values = uiState.recentSnapshots.map { PowerMath.convertTemp(it.temperatureCelsius, tempUnit) },
                        unit = tempSymbol,
                        valueDecimals = 1,
                        timeRangeLabel = timeRangeContext.orEmpty(),
                        lineColor = ElectricEmerald,
                        testTag = "chart_temperature"
                    )
                }
            }

            val cells = uiState.latestSnapshot?.cellVoltagesList().orEmpty()
            if (cells.isNotEmpty()) {
                item {
                    CellBalanceCard(cells = cells)
                }
            }

            item {
                Text(
                    text = "Alert log",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
            }

            if (uiState.alerts.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_alert_log"),
                        shape = MaterialTheme.shapes.medium,
                        color = CarbonSurface,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.empty_alert_log),
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite
                            )
                            Text(
                                text = "Charge completion and battery safety alerts will appear here when triggered.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateMuted
                            )
                        }
                    }
                }
            } else {
                items(uiState.alerts, key = { it.id }) { alert ->
                    AlertLogRow(alert = alert)
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun TelemetryTrendCard(
    title: String,
    latestFormatted: String,
    values: List<Float>,
    unit: String,
    valueDecimals: Int,
    timeRangeLabel: String,
    lineColor: Color,
    testTag: String
) {
    val minVal = values.minOrNull() ?: 0f
    val maxVal = values.maxOrNull() ?: 0f
    val hasEnoughForTrend = values.size >= 2

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                Text(
                    text = latestFormatted,
                    style = MonoMetricMedium,
                    color = PureWhite
                )
            }

            if (!hasEnoughForTrend) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = CarbonElevated,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(
                        text = "Not enough samples for a trend yet (${values.size} recorded). At least 2 readings are required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                ) {
                    val topPad = 6.dp.toPx()
                    val bottomPad = 6.dp.toPx()
                    val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    // Subtle horizontal reference gridlines
                    val gridColor = HairlineBorder.copy(alpha = 0.65f)
                    listOf(topPad, topPad + plotHeight / 2f, topPad + plotHeight).forEach { yLine ->
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, yLine),
                            end = Offset(size.width, yLine),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val span = maxVal - minVal
                    val stepX = size.width / (values.size - 1)
                    val linePath = Path()
                    val points = ArrayList<Offset>(values.size)

                    values.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val normY = if (span > 0.001f) {
                            ((v - minVal) / span).coerceIn(0f, 1f)
                        } else {
                            0.5f
                        }
                        val y = (topPad + plotHeight) - (normY * plotHeight)
                        points.add(Offset(x, y))
                        if (idx == 0) {
                            linePath.moveTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    if (points.size <= 16) {
                        points.forEach { pt ->
                            drawCircle(
                                color = lineColor,
                                radius = 2.5.dp.toPx(),
                                center = pt
                            )
                        }
                    } else {
                        drawCircle(
                            color = lineColor,
                            radius = 3.dp.toPx(),
                            center = points.last()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Min %.${valueDecimals}f %s".format(minVal, unit),
                        style = MonoLabelSmall,
                        color = SlateMuted
                    )
                    Text(
                        text = timeRangeLabel,
                        style = MonoLabelSmall,
                        color = SlateMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Max %.${valueDecimals}f %s".format(maxVal, unit),
                        style = MonoLabelSmall,
                        color = SlateMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CellBalanceCard(cells: List<Float>) {
    val minCell = cells.min()
    val maxCell = cells.max()
    val deltaMv = ((maxCell - minCell) * 1000f).toInt()
    val isBalanced = deltaMv <= 50

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cell_balance_card"),
        shape = MaterialTheme.shapes.medium,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cell balance",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                Text(
                    text = "Δ $deltaMv mV",
                    style = MonoLabelSmall,
                    color = if (isBalanced) ElectricEmerald else CautionAmber
                )
            }

            cells.chunked(2).forEachIndexed { rowIdx, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEachIndexed { colIdx, voltage ->
                        val cellNum = rowIdx * 2 + colIdx + 1
                        val progress = ((voltage - 2.7f) / (3.65f - 2.7f)).coerceIn(0f, 1f)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "C%02d".format(cellNum),
                                    style = MonoLabelSmall,
                                    color = SlateMuted
                                )
                                Text(
                                    text = "%.3f V".format(voltage),
                                    style = MonoLabelSmall,
                                    color = PureWhite
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (isBalanced) ElectricEmerald else PrecisionCyan,
                                trackColor = CarbonElevated
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertLogRow(alert: AlertEventEntity) {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    val timeText = formatter.format(Date(alert.timestampMs))
    val isWarning = alert.alertType == "HIGH_TEMP" ||
        alert.alertType == "LOW_SOC" ||
        alert.alertType == "COLD_CHARGE"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isWarning) Icons.Rounded.WarningAmber else Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = if (isWarning) CriticalCoral else ElectricEmerald,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeText,
                style = MonoLabelSmall,
                color = SlateMuted
            )
        }
    }
}

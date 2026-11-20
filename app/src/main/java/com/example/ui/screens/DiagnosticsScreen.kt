package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ble.PowerMath
import com.example.data.local.AlertEventEntity
import com.example.ui.DiagnosticsUiState
import com.example.ui.theme.AmberDim
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        shape = RoundedCornerShape(50),
                        color = if (uiState.isOfflineCached) AmberDim else EmeraldDim
                    ) {
                        Text(
                            text = if (uiState.isOfflineCached) {
                                "ROOM CACHE · ${uiState.recentSnapshots.size}"
                            } else {
                                "LIVE STREAM · ${uiState.recentSnapshots.size}"
                            },
                            style = MonoLabelSmall,
                            color = if (uiState.isOfflineCached) CautionAmber else ElectricEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.section_trend_history),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
            }

            if (uiState.recentSnapshots.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = CarbonSurface,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timeline,
                                contentDescription = null,
                                tint = SlateMuted
                            )
                            Spacer(modifier = Modifier.width(12.dp))
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
                    TelemetrySparklineCard(
                        title = "PACK VOLTAGE",
                        latestFormatted = "%.2f V".format(uiState.recentSnapshots.last().voltageVolts),
                        values = uiState.recentSnapshots.map { it.voltageVolts },
                        unit = "V",
                        lineColor = PureWhite,
                        testTag = "chart_voltage"
                    )
                }

                item {
                    TelemetrySparklineCard(
                        title = "CURRENT FLOW",
                        latestFormatted = "%+.2f A".format(uiState.recentSnapshots.last().currentAmps),
                        values = uiState.recentSnapshots.map { it.currentAmps },
                        unit = "A",
                        lineColor = PrecisionCyan,
                        testTag = "chart_current"
                    )
                }

                item {
                    val tempUnit = uiState.temperatureUnit
                    val tempSymbol = PowerMath.tempUnitSymbol(tempUnit)
                    val latestTempC = uiState.recentSnapshots.last().temperatureCelsius
                    TelemetrySparklineCard(
                        title = "PACK TEMPERATURE",
                        latestFormatted = "${"%.1f".format(PowerMath.convertTemp(latestTempC, tempUnit))} $tempSymbol",
                        values = uiState.recentSnapshots.map { PowerMath.convertTemp(it.temperatureCelsius, tempUnit) },
                        unit = tempSymbol,
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
                    text = stringResource(R.string.section_alert_log),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
            }

            if (uiState.alerts.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = CarbonSurface,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = SlateMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.empty_alert_log),
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
private fun TelemetrySparklineCard(
    title: String,
    latestFormatted: String,
    values: List<Float>,
    unit: String,
    lineColor: Color,
    testTag: String
) {
    val minVal = values.minOrNull() ?: 0f
    val maxVal = values.maxOrNull() ?: 0f

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
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
                Text(
                    text = latestFormatted,
                    style = MonoMetricMedium,
                    color = lineColor
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
            ) {
                val range = (maxVal - minVal).takeIf { it > 0.01f } ?: 1f
                val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width

                val linePath = Path()
                val fillPath = Path()

                values.forEachIndexed { idx, v ->
                    val x = if (values.size == 1) size.width else idx * stepX
                    val normY = ((v - minVal) / range).coerceIn(0f, 1f)
                    val y = size.height - (normY * (size.height - 8.dp.toPx())) - 4.dp.toPx()

                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (idx == values.lastIndex) {
                        fillPath.lineTo(x, size.height)
                        fillPath.close()
                    }
                }

                if (values.size > 1) {
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    val y = size.height / 2f
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MIN ${"%.1f".format(minVal)} $unit",
                    style = MonoLabelSmall,
                    color = SlateMuted
                )
                Text(
                    text = "MAX ${"%.1f".format(maxVal)} $unit",
                    style = MonoLabelSmall,
                    color = SlateMuted
                )
            }
        }
    }
}

@Composable
private fun CellBalanceCard(cells: List<Float>) {
    val minCell = cells.min()
    val maxCell = cells.max()
    val deltaMv = ((maxCell - minCell) * 1000f).toInt()

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
                    text = stringResource(R.string.section_cell_balance),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
                Text(
                    text = "Δ $deltaMv mV",
                    style = MonoLabelSmall,
                    color = if (deltaMv > 50) CautionAmber else ElectricEmerald
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
                                    text = "%.3fV".format(voltage),
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
                                color = ElectricEmerald,
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

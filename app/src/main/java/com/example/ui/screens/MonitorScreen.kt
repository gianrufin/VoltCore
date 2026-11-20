package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ble.SimulatorMode
import com.example.data.local.OutputPortType
import com.example.data.local.PluggedDeviceEntity
import com.example.ui.ConnectionMode
import com.example.ui.MonitorUiState
import com.example.ui.components.HeroPowerCard
import com.example.ui.components.PluggedDevicesSection
import com.example.ui.components.TelemetryGrid
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted
import kotlin.math.abs

@Composable
fun MonitorScreen(
    uiState: MonitorUiState,
    onOpenConnectionSheet: () -> Unit,
    onStartSimulator: () -> Unit,
    onSelectSimulatorMode: (SimulatorMode) -> Unit,
    onSavePluggedDevice: (Long, String, OutputPortType, Float) -> Unit,
    onTogglePluggedDevice: (PluggedDeviceEntity) -> Unit,
    onDeletePluggedDevice: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        val isWide = maxWidth >= 720.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                MonitorTopHeader(
                    uiState = uiState,
                    onOpenConnectionSheet = onOpenConnectionSheet
                )
            }

            if (uiState.snapshot == null) {
                item {
                    EmptyAwaitingPackCard(
                        onOpenConnectionSheet = onOpenConnectionSheet,
                        onStartSimulator = onStartSimulator
                    )
                }
            } else if (isWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        HeroPowerCard(
                            snapshot = uiState.snapshot,
                            connectionMode = uiState.connectionMode,
                            formattedEta = uiState.formattedEta,
                            remainingWh = uiState.remainingWh,
                            packCapacityWh = uiState.packCapacityWh,
                            simulatorState = uiState.simulatorState,
                            onSelectSimulatorMode = onSelectSimulatorMode,
                            modifier = Modifier.weight(1f)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            TelemetryGrid(
                                snapshot = uiState.snapshot,
                                highTempThreshold = uiState.settings.highTempThresholdCelsius
                            )
                            PluggedDevicesSection(
                                devices = uiState.pluggedDevices,
                                activePluggedWatts = uiState.activePluggedWatts,
                                packOutputWatts = if (!uiState.snapshot.isCharging) {
                                    abs(uiState.snapshot.powerWatts)
                                } else {
                                    0f
                                },
                                onSaveDevice = onSavePluggedDevice,
                                onToggleDevice = onTogglePluggedDevice,
                                onDeleteDevice = onDeletePluggedDevice
                            )
                        }
                    }
                }
            } else {
                item {
                    HeroPowerCard(
                        snapshot = uiState.snapshot,
                        connectionMode = uiState.connectionMode,
                        formattedEta = uiState.formattedEta,
                        remainingWh = uiState.remainingWh,
                        packCapacityWh = uiState.packCapacityWh,
                        simulatorState = uiState.simulatorState,
                        onSelectSimulatorMode = onSelectSimulatorMode
                    )
                }

                item {
                    TelemetryGrid(
                        snapshot = uiState.snapshot,
                        highTempThreshold = uiState.settings.highTempThresholdCelsius
                    )
                }

                item {
                    PluggedDevicesSection(
                        devices = uiState.pluggedDevices,
                        activePluggedWatts = uiState.activePluggedWatts,
                        packOutputWatts = if (!uiState.snapshot.isCharging) {
                            abs(uiState.snapshot.powerWatts)
                        } else {
                            0f
                        },
                        onSaveDevice = onSavePluggedDevice,
                        onToggleDevice = onTogglePluggedDevice,
                        onDeleteDevice = onDeletePluggedDevice
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun MonitorTopHeader(
    uiState: MonitorUiState,
    onOpenConnectionSheet: () -> Unit
) {
    val (pillBg, pillFg, pillText) = when (uiState.connectionMode) {
        ConnectionMode.LIVE_BLE -> Triple(
            EmeraldDim,
            ElectricEmerald,
            stringResource(R.string.status_live_ble)
        )
        ConnectionMode.SIMULATED -> Triple(
            EmeraldDim,
            ElectricEmerald,
            stringResource(R.string.status_simulated)
        )
        ConnectionMode.OFFLINE_CACHED -> {
            val ageMinutes = uiState.snapshot?.let {
                ((System.currentTimeMillis() - it.timestampMs) / 60_000L).coerceAtLeast(0L)
            } ?: 0L
            val ageLabel = if (ageMinutes == 0L) "just now" else "${ageMinutes}m ago"
            Triple(
                AmberDim,
                CautionAmber,
                "${stringResource(R.string.status_offline_cached)} · $ageLabel"
            )
        }
        ConnectionMode.AWAITING -> Triple(
            CarbonElevated,
            SlateMuted,
            stringResource(R.string.status_awaiting_connection)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = PureWhite
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = pillBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(pillFg)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pillText,
                        style = MonoLabelSmall,
                        color = pillFg,
                        modifier = Modifier.testTag("connection_status_pill")
                    )
                }
            }
        }

        Surface(
            onClick = onOpenConnectionSheet,
            shape = RoundedCornerShape(50),
            color = CarbonElevated,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.testTag("connect_bms_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (uiState.connectionMode) {
                        ConnectionMode.LIVE_BLE -> Icons.Rounded.BluetoothConnected
                        ConnectionMode.SIMULATED -> Icons.Rounded.Science
                        ConnectionMode.OFFLINE_CACHED -> Icons.Rounded.CloudOff
                        ConnectionMode.AWAITING -> Icons.Rounded.Bluetooth
                    },
                    contentDescription = stringResource(R.string.action_connect_bms),
                    tint = if (uiState.connectionMode == ConnectionMode.LIVE_BLE ||
                        uiState.connectionMode == ConnectionMode.SIMULATED
                    ) {
                        ElectricEmerald
                    } else {
                        PrecisionCyan
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.connectionMode == ConnectionMode.LIVE_BLE ||
                        uiState.connectionMode == ConnectionMode.SIMULATED
                    ) {
                        "Source"
                    } else {
                        stringResource(R.string.action_connect_bms)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = PureWhite
                )
            }
        }
    }
}

@Composable
private fun EmptyAwaitingPackCard(
    onOpenConnectionSheet: () -> Unit,
    onStartSimulator: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_awaiting_pack_card"),
        shape = MaterialTheme.shapes.large,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = EmeraldDim,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BatterySaver,
                    contentDescription = null,
                    tint = ElectricEmerald,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Text(
                text = stringResource(R.string.empty_monitor_title),
                style = MaterialTheme.typography.headlineMedium,
                color = PureWhite
            )

            Text(
                text = stringResource(R.string.empty_monitor_body),
                style = MaterialTheme.typography.bodyLarge,
                color = SlateMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onOpenConnectionSheet,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricEmerald,
                        contentColor = OledBlack
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_connect_ble_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_connect_bms))
                }

                OutlinedButton(
                    onClick = onStartSimulator,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_launch_simulator_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Science,
                        contentDescription = null,
                        tint = PrecisionCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_start_simulator),
                        color = PureWhite
                    )
                }
            }
        }
    }
}

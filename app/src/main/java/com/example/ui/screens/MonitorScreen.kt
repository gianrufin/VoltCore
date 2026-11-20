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
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.ble.BleConnectionStatus
import com.example.data.ble.BleUiState
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
import com.example.ui.theme.CoralDim
import com.example.ui.theme.CyanDim
import com.example.ui.theme.CriticalCoral
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
    onDisconnectBle: () -> Unit,
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

            // Show live BLE handshake / GATT inspector banner whenever connecting or connected without decoded frame yet
            if (uiState.bleState.status == BleConnectionStatus.CONNECTING ||
                (uiState.bleState.status == BleConnectionStatus.CONNECTED && uiState.snapshot == null) ||
                uiState.bleState.errorMessage != null
            ) {
                item {
                    LiveGattHandshakeCard(
                        bleState = uiState.bleState,
                        onOpenConnectionSheet = onOpenConnectionSheet,
                        onDisconnectBle = onDisconnectBle
                    )
                }
            }

            if (uiState.snapshot == null) {
                if (uiState.bleState.status != BleConnectionStatus.CONNECTING &&
                    uiState.bleState.status != BleConnectionStatus.CONNECTED
                ) {
                    item {
                        EmptyAwaitingPackCard(onOpenConnectionSheet = onOpenConnectionSheet)
                    }
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
                            formattedEta = uiState.formattedEta,
                            remainingWh = uiState.remainingWh,
                            packCapacityWh = uiState.packCapacityWh,
                            packNominalVoltage = uiState.settings.packNominalVoltage,
                            packNominalAh = uiState.settings.packNominalAh,
                            detectedCellCount = uiState.settings.detectedCellCount,
                            modifier = Modifier.weight(1f)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            TelemetryGrid(
                                snapshot = uiState.snapshot,
                                highTempThreshold = uiState.settings.highTempThresholdCelsius,
                                packNominalVoltage = uiState.settings.packNominalVoltage,
                                packNominalAh = uiState.settings.packNominalAh,
                                detectedCellCount = uiState.settings.detectedCellCount
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
                        formattedEta = uiState.formattedEta,
                        remainingWh = uiState.remainingWh,
                        packCapacityWh = uiState.packCapacityWh,
                        packNominalVoltage = uiState.settings.packNominalVoltage,
                        packNominalAh = uiState.settings.packNominalAh,
                        detectedCellCount = uiState.settings.detectedCellCount
                    )
                }

                item {
                    TelemetryGrid(
                        snapshot = uiState.snapshot,
                        highTempThreshold = uiState.settings.highTempThresholdCelsius,
                        packNominalVoltage = uiState.settings.packNominalVoltage,
                        packNominalAh = uiState.settings.packNominalAh,
                        detectedCellCount = uiState.settings.detectedCellCount
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
            uiState.bleState.detectedProtocol?.uppercase() ?: stringResource(R.string.status_live_ble)
        )
        ConnectionMode.CONNECTING -> Triple(
            CyanDim,
            PrecisionCyan,
            stringResource(R.string.status_connecting)
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
                        ConnectionMode.CONNECTING -> Icons.Rounded.BluetoothSearching
                        ConnectionMode.OFFLINE_CACHED -> Icons.Rounded.CloudOff
                        ConnectionMode.AWAITING -> Icons.Rounded.Bluetooth
                    },
                    contentDescription = stringResource(R.string.action_connect_bms),
                    tint = if (uiState.connectionMode == ConnectionMode.LIVE_BLE) {
                        ElectricEmerald
                    } else {
                        PrecisionCyan
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.connectionMode == ConnectionMode.LIVE_BLE) {
                        "Connected"
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
private fun LiveGattHandshakeCard(
    bleState: BleUiState,
    onOpenConnectionSheet: () -> Unit,
    onDisconnectBle: () -> Unit
) {
    val hasError = bleState.errorMessage != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_gatt_handshake_card"),
        shape = MaterialTheme.shapes.medium,
        color = if (hasError) CoralDim else CarbonSurface,
        border = BorderStroke(1.dp, if (hasError) CriticalCoral else PrecisionCyan)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (bleState.status == BleConnectionStatus.CONNECTING ||
                        (bleState.status == BleConnectionStatus.CONNECTED && !hasError)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = PrecisionCyan
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = bleState.connectedDeviceName ?: "Bluetooth Power Pack",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite
                        )
                        Text(
                            text = bleState.errorMessage ?: bleState.handshakeStage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasError) CriticalCoral else PrecisionCyan
                        )
                    }
                }
            }

            if (bleState.discoveredServicesSummary.isNotEmpty()) {
                Text(
                    text = "GATT SERVICES (${bleState.discoveredServicesSummary.size}) · PACKETS: ${bleState.packetsReceivedCount}",
                    style = MonoLabelSmall,
                    color = SlateMuted
                )
                Text(
                    text = bleState.discoveredServicesSummary.joinToString("\n"),
                    style = MonoLabelSmall,
                    color = PureWhite
                )
            }

            bleState.lastRawHex?.let { hex ->
                Text(
                    text = "LAST FRAME HEX: $hex",
                    style = MonoLabelSmall,
                    color = ElectricEmerald
                )
            }

            if (bleState.packetsReceivedCount == 0 && bleState.discoveredServicesSummary.isNotEmpty()) {
                Text(
                    text = "Connected to power station GATT. If your unit uses an encrypted/proprietary brand protocol (e.g., newer EcoFlow/Bluetti BLE), standard Smart BMS UART frames may not be broadcast without brand pairing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenConnectionSheet,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Switch Device", color = PureWhite)
                }
                if (bleState.status == BleConnectionStatus.CONNECTED ||
                    bleState.status == BleConnectionStatus.CONNECTING
                ) {
                    OutlinedButton(
                        onClick = onDisconnectBle,
                        border = BorderStroke(1.dp, CriticalCoral),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_disconnect), color = CriticalCoral)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAwaitingPackCard(
    onOpenConnectionSheet: () -> Unit
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

            Button(
                onClick = onOpenConnectionSheet,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricEmerald,
                    contentColor = OledBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_connect_ble_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_connect_bms))
            }
        }
    }
}

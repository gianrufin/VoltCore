package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ble.BleConnectionStatus
import com.example.data.ble.BleUiState
import com.example.data.local.OutputPortType
import com.example.data.local.PluggedDeviceEntity
import com.example.data.local.SavedStationEntity
import com.example.data.local.TelemetrySnapshotEntity
import com.example.ui.ConnectionMode
import com.example.ui.MonitorUiState
import com.example.ui.components.HeroPowerCard
import com.example.ui.components.PluggedDevicesSection
import com.example.ui.components.TelemetryGrid
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
    onOpenDashboard: () -> Unit,
    onCloseDashboard: () -> Unit,
    onConnectSavedStation: (SavedStationEntity) -> Unit,
    onRemoveSavedStation: (String) -> Unit,
    onSavePluggedDevice: (Long, String, OutputPortType, Float) -> Unit,
    onTogglePluggedDevice: (PluggedDeviceEntity) -> Unit,
    onDeletePluggedDevice: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val liveSnapshot = if (uiState.bleState.status == BleConnectionStatus.CONNECTED) {
        uiState.snapshot
    } else {
        null
    }
    val showDashboard = uiState.isDashboardOpen && liveSnapshot != null

    BackHandler(enabled = showDashboard) {
        onCloseDashboard()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        val isWide = maxWidth >= 720.dp

        if (!showDashboard &&
            uiState.savedStations.isEmpty() &&
            uiState.bleState.status != BleConnectionStatus.CONNECTING &&
            uiState.bleState.status != BleConnectionStatus.CONNECTED &&
            uiState.bleState.errorMessage == null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                MonitorTopHeader(
                    uiState = uiState,
                    showDashboard = false,
                    onCloseDashboard = onCloseDashboard,
                    onOpenConnectionSheet = onOpenConnectionSheet
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    FirstRunPlusLanding(
                        onOpenConnectionSheet = onOpenConnectionSheet
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    MonitorTopHeader(
                        uiState = uiState,
                        showDashboard = showDashboard,
                        onCloseDashboard = onCloseDashboard,
                        onOpenConnectionSheet = onOpenConnectionSheet
                    )
                }

                if (!showDashboard) {
                    if (uiState.bleState.status == BleConnectionStatus.CONNECTING ||
                        (uiState.bleState.status == BleConnectionStatus.CONNECTED && liveSnapshot == null) ||
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

                    if (uiState.savedStations.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.section_saved_stations),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateMuted
                                )
                                Text(
                                    text = "${uiState.savedStations.size} UNIT${if (uiState.savedStations.size == 1) "" else "S"}",
                                    style = MonoLabelSmall,
                                    color = SlateMuted
                                )
                            }
                        }

                        items(uiState.savedStations, key = { it.address }) { station ->
                            val isThisConnected = uiState.bleState.status == BleConnectionStatus.CONNECTED &&
                                uiState.bleState.connectedDeviceAddress == station.address
                            val isThisConnecting = uiState.bleState.status == BleConnectionStatus.CONNECTING &&
                                uiState.bleState.connectedDeviceAddress == station.address
                            SavedStationCard(
                                station = station,
                                isConnected = isThisConnected,
                                isConnecting = isThisConnecting,
                                liveSnapshot = if (isThisConnected) liveSnapshot else null,
                                formattedEta = uiState.formattedEta,
                                onOpenDashboard = onOpenDashboard,
                                onConnect = { onConnectSavedStation(station) },
                                onDisconnect = onDisconnectBle,
                                onRemove = { onRemoveSavedStation(station.address) }
                            )
                        }

                        item {
                            AddPowerStationCard(onClick = onOpenConnectionSheet)
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
                                snapshot = liveSnapshot,
                                formattedEta = uiState.formattedEta,
                                remainingWh = uiState.remainingWh,
                                packCapacityWh = uiState.packCapacityWh,
                                packNominalVoltage = uiState.settings.packNominalVoltage,
                                packNominalAh = uiState.settings.packNominalAh,
                                detectedCellCount = uiState.settings.detectedCellCount,
                                passThroughFlow = uiState.passThroughFlow,
                                displayDensity = uiState.settings.displayDensity,
                                modifier = Modifier.weight(1f)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                TelemetryGrid(
                                    snapshot = liveSnapshot,
                                    highTempThreshold = uiState.settings.highTempThresholdCelsius,
                                    coldChargeThreshold = uiState.settings.coldChargeThresholdCelsius,
                                    packNominalVoltage = uiState.settings.packNominalVoltage,
                                    packNominalAh = uiState.settings.packNominalAh,
                                    detectedCellCount = uiState.settings.detectedCellCount,
                                    temperatureUnit = uiState.settings.temperatureUnit
                                )
                                PluggedDevicesSection(
                                    devices = uiState.pluggedDevices,
                                    activePluggedWatts = uiState.activePluggedWatts,
                                    packOutputWatts = if (!liveSnapshot.isCharging) {
                                        abs(liveSnapshot.powerWatts)
                                    } else {
                                        0f
                                    },
                                    remainingWh = uiState.remainingWh,
                                    displayDensity = uiState.settings.displayDensity,
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
                            snapshot = liveSnapshot,
                            formattedEta = uiState.formattedEta,
                            remainingWh = uiState.remainingWh,
                            packCapacityWh = uiState.packCapacityWh,
                            packNominalVoltage = uiState.settings.packNominalVoltage,
                            packNominalAh = uiState.settings.packNominalAh,
                            detectedCellCount = uiState.settings.detectedCellCount,
                            passThroughFlow = uiState.passThroughFlow,
                            displayDensity = uiState.settings.displayDensity
                        )
                    }

                    item {
                        TelemetryGrid(
                            snapshot = liveSnapshot,
                            highTempThreshold = uiState.settings.highTempThresholdCelsius,
                            coldChargeThreshold = uiState.settings.coldChargeThresholdCelsius,
                            packNominalVoltage = uiState.settings.packNominalVoltage,
                            packNominalAh = uiState.settings.packNominalAh,
                            detectedCellCount = uiState.settings.detectedCellCount,
                            temperatureUnit = uiState.settings.temperatureUnit
                        )
                    }

                    item {
                        PluggedDevicesSection(
                            devices = uiState.pluggedDevices,
                            activePluggedWatts = uiState.activePluggedWatts,
                            packOutputWatts = if (!liveSnapshot.isCharging) {
                                abs(liveSnapshot.powerWatts)
                            } else {
                                0f
                            },
                            remainingWh = uiState.remainingWh,
                            displayDensity = uiState.settings.displayDensity,
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
}

@Composable
private fun MonitorTopHeader(
    uiState: MonitorUiState,
    showDashboard: Boolean,
    onCloseDashboard: () -> Unit,
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
        ConnectionMode.DISCONNECTED -> Triple(
            CarbonElevated,
            SlateMuted,
            stringResource(R.string.status_disconnected)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showDashboard) {
                Surface(
                    onClick = onCloseDashboard,
                    shape = RoundedCornerShape(50),
                    color = CarbonElevated,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.testTag("back_to_stations_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back_to_stations),
                            tint = PureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_back_to_stations),
                            style = MaterialTheme.typography.labelLarge,
                            color = PureWhite
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureWhite
                )
            }

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
                        ConnectionMode.DISCONNECTED -> Icons.Rounded.Bluetooth
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
private fun FirstRunPlusLanding(
    onOpenConnectionSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("first_run_landing"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = EmeraldDim.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.25f)),
                modifier = Modifier.size(118.dp)
            ) {}

            Surface(
                onClick = onOpenConnectionSheet,
                shape = CircleShape,
                color = ElectricEmerald,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(84.dp)
                    .testTag("empty_connect_ble_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.landing_connect_title),
                        tint = OledBlack,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.landing_connect_title),
                style = MaterialTheme.typography.headlineMedium,
                color = PureWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.landing_connect_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = SlateMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = CarbonSurface,
            border = BorderStroke(1.dp, HairlineBorder)
        ) {
            Text(
                text = "JBD · JK · DALY · BLE GATT",
                style = MonoLabelSmall,
                color = PrecisionCyan,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun SavedStationCard(
    station: SavedStationEntity,
    isConnected: Boolean,
    isConnecting: Boolean,
    liveSnapshot: TelemetrySnapshotEntity?,
    formattedEta: String,
    onOpenDashboard: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit
) {
    val specLine = buildString {
        if (station.cellCount > 0) append("${station.cellCount}S · ")
        append("${station.nominalAh.toInt()} Ah · ${"%.1f".format(station.nominalVoltage)} V")
    }

    Surface(
        onClick = { if (isConnected && liveSnapshot != null) onOpenDashboard() else onConnect() },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_station_card_${station.address}"),
        shape = MaterialTheme.shapes.large,
        color = CarbonSurface,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isConnected -> ElectricEmerald.copy(alpha = 0.65f)
                isConnecting -> PrecisionCyan.copy(alpha = 0.65f)
                else -> HairlineBorder
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isConnected -> EmeraldDim
                            isConnecting -> CyanDim
                            else -> CarbonElevated
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = PrecisionCyan
                                )
                            } else {
                                Icon(
                                    imageVector = when {
                                        isConnected -> Icons.Rounded.BatteryChargingFull
                                        else -> Icons.Rounded.BluetoothDisabled
                                    },
                                    contentDescription = null,
                                    tint = if (isConnected) ElectricEmerald else SlateMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$specLine · ${station.protocolName}",
                            style = MonoLabelSmall,
                            color = SlateMuted,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = when {
                        isConnected -> EmeraldDim
                        isConnecting -> CyanDim
                        else -> CarbonElevated
                    }
                ) {
                    Text(
                        text = when {
                            isConnected -> "LIVE"
                            isConnecting -> "PAIRING"
                            else -> stringResource(R.string.status_disconnected_label)
                        },
                        style = MonoLabelSmall,
                        color = when {
                            isConnected -> ElectricEmerald
                            isConnecting -> PrecisionCyan
                            else -> SlateMuted
                        },
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("saved_station_status")
                    )
                }
            }

            // Show live SoC & wattage ONLY when actively connected with decoded live telemetry
            if (isConnected && liveSnapshot != null) {
                val absWatts = abs(liveSnapshot.powerWatts)
                val wattText = when {
                    absWatts < 1.5f -> "0 W"
                    liveSnapshot.isCharging -> "+${absWatts.toInt()} W"
                    else -> "${absWatts.toInt()} W"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(CarbonElevated)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SOC",
                            style = MonoLabelSmall,
                            color = SlateMuted
                        )
                        Text(
                            text = "%.1f%%".format(liveSnapshot.exactSocPercent),
                            style = MonoMetricMedium,
                            color = ElectricEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("overview_station_soc")
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (liveSnapshot.isCharging) "CHARGE" else "DRAW",
                            style = MonoLabelSmall,
                            color = SlateMuted
                        )
                        Text(
                            text = wattText,
                            style = MonoMetricMedium,
                            color = if (liveSnapshot.isCharging) ElectricEmerald else PrecisionCyan
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ETA",
                            style = MonoLabelSmall,
                            color = SlateMuted
                        )
                        Text(
                            text = formattedEta,
                            style = MonoMetricMedium,
                            color = PureWhite
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConnected && liveSnapshot != null) {
                    Button(
                        onClick = onOpenDashboard,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricEmerald,
                            contentColor = OledBlack
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_station_dashboard_button")
                    ) {
                        Text(stringResource(R.string.action_open_dashboard))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        border = BorderStroke(1.dp, CriticalCoral.copy(alpha = 0.6f))
                    ) {
                        Text(stringResource(R.string.action_disconnect), color = CriticalCoral)
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = !isConnecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CarbonElevated,
                            contentColor = PureWhite
                        ),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("connect_station_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            tint = PrecisionCyan,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnecting) "Connecting…" else stringResource(R.string.action_connect_station)
                        )
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_station_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = SlateMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPowerStationCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_power_station_button"),
        shape = MaterialTheme.shapes.large,
        color = CarbonSurface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = EmeraldDim,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = ElectricEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.action_add_power_station),
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite,
                letterSpacing = 0.2.sp
            )
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
                            text = bleState.connectedDeviceName ?: "Bluetooth Power Station",
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
                    text = "Connected to power station GATT. If your unit uses an encrypted/proprietary brand protocol, standard Smart BMS UART frames may not be broadcast without brand pairing.",
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

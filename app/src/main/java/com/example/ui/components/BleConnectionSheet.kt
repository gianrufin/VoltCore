package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ble.BleConnectionStatus
import com.example.data.ble.BleUiState
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CriticalCoral
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleConnectionSheet(
    bleState: BleUiState,
    onRequestBlePermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (address: String, name: String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CarbonSurface,
        contentColor = PureWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.ble_sheet_title),
                style = MaterialTheme.typography.headlineMedium,
                color = PureWhite
            )

            Text(
                text = "Supports JBD / Xiaoxiang, JK BMS, Daly BMS, Nordic UART, and Standard BLE Battery Service (0x180F).",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted
            )

            when {
                !bleState.isHardwareAvailable -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = CarbonElevated,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Text(
                            text = stringResource(R.string.ble_no_hardware),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                !bleState.hasPermissions -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = CarbonElevated,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ble_permission_needed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite
                            )
                            Button(
                                onClick = onRequestBlePermissions,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricEmerald,
                                    contentColor = OledBlack
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_ble_permission_button")
                            ) {
                                Text(stringResource(R.string.ble_grant_permission))
                            }
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isScanning = bleState.status == BleConnectionStatus.SCANNING
                        Button(
                            onClick = { if (isScanning) onStopScan() else onStartScan() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) CarbonElevated else ElectricEmerald,
                                contentColor = if (isScanning) PureWhite else OledBlack
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ble_scan_button")
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricEmerald
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ble_stop_scan))
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ble_scan_again))
                            }
                        }

                        if (bleState.status == BleConnectionStatus.CONNECTED ||
                            bleState.status == BleConnectionStatus.CONNECTING
                        ) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                border = BorderStroke(1.dp, CriticalCoral),
                                modifier = Modifier.testTag("ble_disconnect_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.action_disconnect),
                                    color = CriticalCoral
                                )
                            }
                        }
                    }

                    bleState.errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CriticalCoral
                        )
                    }

                    if (bleState.discoveredDevices.isEmpty() &&
                        bleState.status != BleConnectionStatus.SCANNING
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = CarbonElevated,
                            border = BorderStroke(1.dp, HairlineBorder)
                        ) {
                            Text(
                                text = "Tap Scan Nearby BMS while your power pack is powered on and within Bluetooth range.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateMuted,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else if (bleState.discoveredDevices.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(bleState.discoveredDevices, key = { it.address }) { device ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onConnectDevice(device.address, device.name) }
                                        .testTag("ble_device_${device.address}"),
                                    shape = MaterialTheme.shapes.medium,
                                    color = CarbonElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (device.isLikelyBms) ElectricEmerald else HairlineBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (device.isLikelyBms) {
                                                Icons.Rounded.BatteryChargingFull
                                            } else {
                                                Icons.Rounded.Bluetooth
                                            },
                                            contentDescription = null,
                                            tint = if (device.isLikelyBms) ElectricEmerald else PrecisionCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = device.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = PureWhite
                                                )
                                                if (device.isLikelyBms) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = EmeraldDim
                                                    ) {
                                                        Text(
                                                            text = "BMS",
                                                            style = MonoLabelSmall,
                                                            color = ElectricEmerald,
                                                            modifier = Modifier.padding(
                                                                horizontal = 5.dp,
                                                                vertical = 2.dp
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = device.address,
                                                style = MonoLabelSmall,
                                                color = SlateMuted
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.SignalCellularAlt,
                                            contentDescription = null,
                                            tint = SlateMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${device.rssi} dBm",
                                            style = MonoLabelSmall,
                                            color = SlateMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

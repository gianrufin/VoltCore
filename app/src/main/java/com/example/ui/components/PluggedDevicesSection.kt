package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.OutputPortType
import com.example.data.local.PluggedDeviceEntity
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
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

@Composable
fun PluggedDevicesSection(
    devices: List<PluggedDeviceEntity>,
    activePluggedWatts: Float,
    packOutputWatts: Float,
    onSaveDevice: (Long, String, OutputPortType, Float) -> Unit,
    onToggleDevice: (PluggedDeviceEntity) -> Unit,
    onDeleteDevice: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingDevice by remember { mutableStateOf<PluggedDeviceEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.section_plugged_devices),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
                if (devices.isNotEmpty()) {
                    Text(
                        text = "${activePluggedWatts.toInt()} W tracked • ${packOutputWatts.toInt()} W pack draw",
                        style = MonoLabelSmall,
                        color = PrecisionCyan
                    )
                }
            }

            Surface(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(50),
                color = CarbonElevated,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.testTag("add_plugged_device_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.action_add_device),
                        tint = ElectricEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_add_device),
                        style = MaterialTheme.typography.labelLarge,
                        color = PureWhite
                    )
                }
            }
        }

        if (devices.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = CarbonSurface,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Devices,
                        contentDescription = null,
                        tint = SlateMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.empty_plugged_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted
                    )
                }
            }
        } else {
            // Output load utilization bar
            val denominator = packOutputWatts.coerceAtLeast(activePluggedWatts).coerceAtLeast(1f)
            val ratio = (activePluggedWatts / denominator).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = PrecisionCyan,
                trackColor = CarbonElevated
            )

            devices.forEach { device ->
                PluggedDeviceRow(
                    device = device,
                    onClick = { editingDevice = device },
                    onToggle = { onToggleDevice(device) },
                    onDelete = { onDeleteDevice(device.id) }
                )
            }
        }
    }

    if (showAddDialog) {
        PluggedDeviceDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, port, watts ->
                onSaveDevice(0L, name, port, watts)
                showAddDialog = false
            }
        )
    }

    editingDevice?.let { current ->
        PluggedDeviceDialog(
            existing = current,
            onDismiss = { editingDevice = null },
            onSave = { name, port, watts ->
                onSaveDevice(current.id, name, port, watts)
                editingDevice = null
            }
        )
    }
}

@Composable
private fun PluggedDeviceRow(
    device: PluggedDeviceEntity,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val port = runCatching { OutputPortType.valueOf(device.portType) }
        .getOrDefault(OutputPortType.USB_C)
    val portIcon = when (port) {
        OutputPortType.USB_C -> Icons.Rounded.Usb
        OutputPortType.AC_OUT -> Icons.Rounded.ElectricalServices
        OutputPortType.DC_12V -> Icons.Rounded.Power
        OutputPortType.ANDERSON -> Icons.Rounded.Power
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("plugged_device_row_${device.id}"),
        shape = MaterialTheme.shapes.medium,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (device.isActive) CyanDim else CarbonElevated,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = portIcon,
                    contentDescription = port.label,
                    tint = if (device.isActive) PrecisionCyan else SlateMuted,
                    modifier = Modifier.padding(9.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (device.isActive) PureWhite else SlateMuted
                )
                Text(
                    text = port.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateMuted
                )
            }

            Text(
                text = if (device.isActive) "${device.estimatedWatts.toInt()} W" else "OFF",
                style = MonoMetricMedium,
                color = if (device.isActive) PureWhite else SlateMuted,
                modifier = Modifier.padding(end = 8.dp)
            )

            Switch(
                checked = device.isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OledBlack,
                    checkedTrackColor = ElectricEmerald,
                    uncheckedThumbColor = SlateMuted,
                    uncheckedTrackColor = CarbonElevated
                ),
                modifier = Modifier.testTag("toggle_device_${device.id}")
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_device_${device.id}")
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

@Composable
private fun PluggedDeviceDialog(
    existing: PluggedDeviceEntity?,
    onDismiss: () -> Unit,
    onSave: (String, OutputPortType, Float) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var wattsText by remember {
        mutableStateOf(existing?.estimatedWatts?.toInt()?.toString() ?: "65")
    }
    var selectedPort by remember {
        mutableStateOf(
            existing?.let {
                runCatching { OutputPortType.valueOf(it.portType) }.getOrNull()
            } ?: OutputPortType.USB_C
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ElectricEmerald,
        unfocusedBorderColor = HairlineBorder,
        focusedTextColor = PureWhite,
        unfocusedTextColor = PureWhite,
        focusedLabelColor = ElectricEmerald,
        unfocusedLabelColor = SlateMuted
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = PureWhite,
        title = {
            Text(
                text = stringResource(
                    if (existing == null) R.string.dialog_add_device_title
                    else R.string.dialog_edit_device_title
                ),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_device_name)) },
                    placeholder = { Text("e.g. MacBook Pro / Fridge") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_name_input")
                )

                OutlinedTextField(
                    value = wattsText,
                    onValueChange = { wattsText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(stringResource(R.string.label_device_watts)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_watts_input")
                )

                Text(
                    text = stringResource(R.string.label_device_port),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutputPortType.entries.take(2).forEach { port ->
                        PortChip(
                            port = port,
                            selected = selectedPort == port,
                            onClick = { selectedPort = port },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutputPortType.entries.drop(2).forEach { port ->
                        PortChip(
                            port = port,
                            selected = selectedPort == port,
                            onClick = { selectedPort = port },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            val watts = wattsText.toFloatOrNull() ?: 0f
            Button(
                onClick = { onSave(name.ifBlank { selectedPort.label }, selectedPort, watts) },
                enabled = watts > 0f,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricEmerald,
                    contentColor = OledBlack
                ),
                modifier = Modifier.testTag("save_device_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = SlateMuted)
            }
        }
    )
}

@Composable
private fun PortChip(
    port: OutputPortType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(port.label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
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
        modifier = modifier
    )
}

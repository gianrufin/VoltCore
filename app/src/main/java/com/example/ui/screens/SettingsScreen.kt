package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.repository.UpdateCheckResult
import com.example.ui.SettingsUiState
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CriticalCoral
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSaveCalibration: (Float, Float) -> Unit,
    onUpdateChargeAlert: (Boolean, Int) -> Unit,
    onUpdateLowBatteryAlert: (Boolean, Int) -> Unit,
    onUpdateHighTempAlert: (Boolean, Float) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSendTestAlert: () -> Unit,
    onCheckForUpdates: (String) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var capacityText by remember { mutableStateOf(uiState.settings.packCapacityWh.toInt().toString()) }
    var nominalVText by remember { mutableStateOf(uiState.settings.packNominalVoltage.toString()) }
    var githubRepoText by remember { mutableStateOf(uiState.settings.githubRepoSlug) }
    var calibrationSavedMsg by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.settings) {
        if (githubRepoText.isBlank() && uiState.settings.githubRepoSlug.isNotBlank()) {
            githubRepoText = uiState.settings.githubRepoSlug
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ElectricEmerald,
        unfocusedBorderColor = HairlineBorder,
        focusedTextColor = PureWhite,
        unfocusedTextColor = PureWhite,
        focusedLabelColor = ElectricEmerald,
        unfocusedLabelColor = SlateMuted
    )

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
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureWhite
                )
            }

            // Section 1: In-App GitHub Releases APK Updater
            item {
                Text(
                    text = stringResource(R.string.section_github_updater),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = CarbonSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    tint = ElectricEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "In-App APK Release Check",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PureWhite
                                )
                            }
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MonoLabelSmall,
                                color = PrecisionCyan
                            )
                        }

                        OutlinedTextField(
                            value = githubRepoText,
                            onValueChange = { githubRepoText = it },
                            label = { Text(stringResource(R.string.label_github_repo)) },
                            placeholder = { Text(stringResource(R.string.hint_github_repo)) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_repo_input")
                        )

                        Button(
                            onClick = { onCheckForUpdates(githubRepoText) },
                            enabled = uiState.updateCheckResult !is UpdateCheckResult.Checking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricEmerald,
                                contentColor = OledBlack
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("check_updates_button")
                        ) {
                            if (uiState.updateCheckResult is UpdateCheckResult.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = OledBlack
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking GitHub Releases…")
                            } else {
                                Text(stringResource(R.string.action_check_updates))
                            }
                        }

                        when (val result = uiState.updateCheckResult) {
                            is UpdateCheckResult.UpToDate -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = ElectricEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "You are on the latest release (v${result.currentVersion}).",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ElectricEmerald
                                    )
                                }
                            }
                            is UpdateCheckResult.UpdateAvailable -> {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = EmeraldDim,
                                    border = BorderStroke(1.dp, ElectricEmerald)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Update Available: ${result.releaseTitle} (${result.latestVersion})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = PureWhite
                                        )
                                        Text(
                                            text = result.releaseNotes,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SlateMuted
                                        )
                                        if (result.apkFileName != null) {
                                            val sizeLabel = result.apkSizeMb?.let { " (%.1f MB)".format(it) } ?: ""
                                            Text(
                                                text = "Asset: ${result.apkFileName}$sizeLabel",
                                                style = MonoLabelSmall,
                                                color = ElectricEmerald
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(result.apkDownloadUrl)
                                                )
                                                context.startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ElectricEmerald,
                                                contentColor = OledBlack
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("download_apk_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.action_download_apk))
                                        }
                                    }
                                }
                            }
                            is UpdateCheckResult.Error -> {
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CriticalCoral
                                )
                            }
                            else -> Unit
                        }
                    }
                }
            }

            // Section 2: Health & Charge Push Alerts
            item {
                Text(
                    text = stringResource(R.string.section_alerts),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = CarbonSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!uiState.hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = AmberDim
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Notification permission is off. Enable it to receive battery health and charge completion alerts.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CautionAmber
                                    )
                                    Button(
                                        onClick = onRequestNotificationPermission,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CautionAmber,
                                            contentColor = OledBlack
                                        ),
                                        modifier = Modifier.testTag("enable_notifications_button")
                                    ) {
                                        Text(stringResource(R.string.action_enable_notifications))
                                    }
                                }
                            }
                        }

                        ThresholdSliderRow(
                            title = stringResource(R.string.label_charge_complete_alert),
                            valueLabel = "${uiState.settings.chargeTargetPercent}%",
                            checked = uiState.settings.chargeCompleteAlertEnabled,
                            value = uiState.settings.chargeTargetPercent.toFloat(),
                            valueRange = 80f..100f,
                            onCheckedChange = { enabled ->
                                onUpdateChargeAlert(enabled, uiState.settings.chargeTargetPercent)
                            },
                            onValueChange = { target ->
                                onUpdateChargeAlert(uiState.settings.chargeCompleteAlertEnabled, target.toInt())
                            },
                            testTag = "switch_charge_alert"
                        )

                        HorizontalDivider(color = HairlineBorder)

                        ThresholdSliderRow(
                            title = stringResource(R.string.label_low_soc_alert),
                            valueLabel = "${uiState.settings.lowBatteryThresholdPercent}%",
                            checked = uiState.settings.lowBatteryAlertEnabled,
                            value = uiState.settings.lowBatteryThresholdPercent.toFloat(),
                            valueRange = 5f..35f,
                            onCheckedChange = { enabled ->
                                onUpdateLowBatteryAlert(enabled, uiState.settings.lowBatteryThresholdPercent)
                            },
                            onValueChange = { threshold ->
                                onUpdateLowBatteryAlert(uiState.settings.lowBatteryAlertEnabled, threshold.toInt())
                            },
                            testTag = "switch_low_soc_alert"
                        )

                        HorizontalDivider(color = HairlineBorder)

                        ThresholdSliderRow(
                            title = stringResource(R.string.label_high_temp_alert),
                            valueLabel = "${uiState.settings.highTempThresholdCelsius.toInt()} °C",
                            checked = uiState.settings.highTempAlertEnabled,
                            value = uiState.settings.highTempThresholdCelsius,
                            valueRange = 35f..65f,
                            onCheckedChange = { enabled ->
                                onUpdateHighTempAlert(enabled, uiState.settings.highTempThresholdCelsius)
                            },
                            onValueChange = { threshold ->
                                onUpdateHighTempAlert(uiState.settings.highTempAlertEnabled, threshold)
                            },
                            testTag = "switch_high_temp_alert"
                        )

                        OutlinedButton(
                            onClick = onSendTestAlert,
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_test_alert_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = ElectricEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_send_test_alert), color = PureWhite)
                        }
                    }
                }
            }

            // Section 3: Pack Capacity Calibration
            item {
                Text(
                    text = stringResource(R.string.section_pack_calibration),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = CarbonSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = capacityText,
                                onValueChange = {
                                    calibrationSavedMsg = false
                                    capacityText = it.filter { c -> c.isDigit() || c == '.' }
                                },
                                label = { Text(stringResource(R.string.label_pack_capacity_wh)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pack_capacity_input")
                            )
                            OutlinedTextField(
                                value = nominalVText,
                                onValueChange = {
                                    calibrationSavedMsg = false
                                    nominalVText = it.filter { c -> c.isDigit() || c == '.' }
                                },
                                label = { Text(stringResource(R.string.label_pack_nominal_v)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pack_voltage_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (calibrationSavedMsg) {
                                Text(
                                    text = "Calibration saved",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ElectricEmerald
                                )
                            } else {
                                Text(
                                    text = "Used for hours remaining math",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateMuted
                                )
                            }

                            Button(
                                onClick = {
                                    val wh = capacityText.toFloatOrNull() ?: 1024f
                                    val v = nominalVText.toFloatOrNull() ?: 51.2f
                                    onSaveCalibration(wh, v)
                                    calibrationSavedMsg = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CarbonElevated,
                                    contentColor = PureWhite
                                ),
                                modifier = Modifier.testTag("save_calibration_button")
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
            }

            // Section 4: Offline Room Storage
            item {
                Text(
                    text = stringResource(R.string.section_offline_storage),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
            }

            item {
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Offline Telemetry Cache",
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite
                            )
                            Text(
                                text = "${uiState.cachedSampleCount} samples stored locally",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateMuted
                            )
                        }
                        OutlinedButton(
                            onClick = onClearCache,
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.testTag("clear_cache_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = CriticalCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_clear_cache), color = PureWhite)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ThresholdSliderRow(
    title: String,
    valueLabel: String,
    checked: Boolean,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                Text(
                    text = "Trigger at $valueLabel",
                    style = MonoLabelSmall,
                    color = if (checked) ElectricEmerald else SlateMuted
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OledBlack,
                    checkedTrackColor = ElectricEmerald,
                    uncheckedThumbColor = SlateMuted,
                    uncheckedTrackColor = CarbonElevated
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
        if (checked) {
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricEmerald,
                    activeTrackColor = ElectricEmerald,
                    inactiveTrackColor = CarbonElevated
                )
            )
        }
    }
}

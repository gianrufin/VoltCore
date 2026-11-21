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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.ble.PowerMath
import com.example.data.network.ApkInstallState
import com.example.data.prefs.DisplayDensity
import com.example.data.prefs.TemperatureUnit
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
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSelectDisplayDensity: (DisplayDensity) -> Unit,
    onSelectTemperatureUnit: (TemperatureUnit) -> Unit,
    onToggleAutoCalibration: (Boolean) -> Unit,
    onSaveCalibration: (Float, Float) -> Unit,
    onUpdateChargeAlert: (Boolean, Int) -> Unit,
    onUpdateLowBatteryAlert: (Boolean, Int) -> Unit,
    onUpdateHighTempAlert: (Boolean, Float) -> Unit,
    onUpdateColdChargeAlert: (Boolean, Float) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSendTestAlert: () -> Unit,
    onCheckForUpdates: (String) -> Unit,
    onDownloadAndInstallApk: (String) -> Unit,
    onRetryPendingApkInstall: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onUninstallForCleanInstall: () -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var capacityText by remember { mutableStateOf(uiState.settings.packCapacityWh.toInt().toString()) }
    var nominalVText by remember { mutableStateOf("%.1f".format(uiState.settings.packNominalVoltage)) }
    var githubRepoText by remember { mutableStateOf(uiState.settings.githubRepoSlug) }
    var calibrationSavedMsg by remember { mutableStateOf(false) }

    LaunchedEffect(
        uiState.settings.packCapacityWh,
        uiState.settings.packNominalVoltage,
        uiState.settings.autoCalibrationEnabled,
        uiState.settings.githubRepoSlug
    ) {
        if (githubRepoText.isBlank() && uiState.settings.githubRepoSlug.isNotBlank()) {
            githubRepoText = uiState.settings.githubRepoSlug
        }
        if (uiState.settings.autoCalibrationEnabled || capacityText.isBlank()) {
            capacityText = uiState.settings.packCapacityWh.toInt().toString()
        }
        if (uiState.settings.autoCalibrationEnabled || nominalVText.isBlank()) {
            nominalVText = "%.1f".format(uiState.settings.packNominalVoltage)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureWhite
                )
            }

            // Group 1: Display & Units
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
                        Text(
                            text = "Display & units",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite
                        )

                        Text(
                            text = stringResource(R.string.label_display_density),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsOptionButton(
                                label = stringResource(R.string.density_minimal),
                                selected = uiState.settings.displayDensity == DisplayDensity.MINIMAL,
                                onClick = { onSelectDisplayDensity(DisplayDensity.MINIMAL) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("density_minimal_button")
                            )
                            SettingsOptionButton(
                                label = stringResource(R.string.density_detailed),
                                selected = uiState.settings.displayDensity == DisplayDensity.DETAILED,
                                onClick = { onSelectDisplayDensity(DisplayDensity.DETAILED) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("density_detailed_button")
                            )
                        }

                        HorizontalDivider(color = HairlineBorder)

                        Text(
                            text = stringResource(R.string.label_temperature_unit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsOptionButton(
                                label = "Celsius (°C)",
                                selected = uiState.settings.temperatureUnit == TemperatureUnit.CELSIUS,
                                onClick = { onSelectTemperatureUnit(TemperatureUnit.CELSIUS) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("temp_unit_celsius_button")
                            )
                            SettingsOptionButton(
                                label = "Fahrenheit (°F)",
                                selected = uiState.settings.temperatureUnit == TemperatureUnit.FAHRENHEIT,
                                onClick = { onSelectTemperatureUnit(TemperatureUnit.FAHRENHEIT) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("temp_unit_fahrenheit_button")
                            )
                        }
                    }
                }
            }

            // Group 2: Charge & Health Alerts
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
                        Text(
                            text = "Charge & health alerts",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite
                        )

                        if (!uiState.hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AmberDim,
                                border = BorderStroke(1.dp, CautionAmber.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Notifications are disabled. Allow alerts to receive battery health and charge warnings.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CautionAmber
                                    )
                                    Button(
                                        onClick = onRequestNotificationPermission,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CautionAmber,
                                            contentColor = OledBlack
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("enable_notifications_button")
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
                            step = 1f,
                            onCheckedChange = { enabled ->
                                onUpdateChargeAlert(enabled, uiState.settings.chargeTargetPercent)
                            },
                            onValueChange = { target ->
                                onUpdateChargeAlert(
                                    uiState.settings.chargeCompleteAlertEnabled,
                                    target.roundToInt()
                                )
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
                            step = 1f,
                            onCheckedChange = { enabled ->
                                onUpdateLowBatteryAlert(enabled, uiState.settings.lowBatteryThresholdPercent)
                            },
                            onValueChange = { threshold ->
                                onUpdateLowBatteryAlert(
                                    uiState.settings.lowBatteryAlertEnabled,
                                    threshold.roundToInt()
                                )
                            },
                            testTag = "switch_low_soc_alert"
                        )

                        HorizontalDivider(color = HairlineBorder)

                        ThresholdSliderRow(
                            title = stringResource(R.string.label_high_temp_alert),
                            valueLabel = PowerMath.formatTempWithUnit(
                                uiState.settings.highTempThresholdCelsius,
                                uiState.settings.temperatureUnit,
                                decimals = 0
                            ),
                            checked = uiState.settings.highTempAlertEnabled,
                            value = uiState.settings.highTempThresholdCelsius,
                            valueRange = 35f..65f,
                            step = 1f,
                            onCheckedChange = { enabled ->
                                onUpdateHighTempAlert(enabled, uiState.settings.highTempThresholdCelsius)
                            },
                            onValueChange = { threshold ->
                                onUpdateHighTempAlert(
                                    uiState.settings.highTempAlertEnabled,
                                    threshold.roundToInt().toFloat()
                                )
                            },
                            testTag = "switch_high_temp_alert"
                        )

                        HorizontalDivider(color = HairlineBorder)

                        ThresholdSliderRow(
                            title = stringResource(R.string.label_cold_charge_alert),
                            valueLabel = PowerMath.formatTempWithUnit(
                                uiState.settings.coldChargeThresholdCelsius,
                                uiState.settings.temperatureUnit,
                                decimals = 0
                            ),
                            checked = uiState.settings.coldChargeAlertEnabled,
                            value = uiState.settings.coldChargeThresholdCelsius,
                            valueRange = -10f..10f,
                            step = 1f,
                            onCheckedChange = { enabled ->
                                onUpdateColdChargeAlert(enabled, uiState.settings.coldChargeThresholdCelsius)
                            },
                            onValueChange = { threshold ->
                                onUpdateColdChargeAlert(
                                    uiState.settings.coldChargeAlertEnabled,
                                    threshold.roundToInt().toFloat()
                                )
                            },
                            testTag = "switch_cold_charge_alert"
                        )

                        OutlinedButton(
                            onClick = onSendTestAlert,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
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

            // Group 3: Pack Calibration
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
                        Text(
                            text = "Pack calibration",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .toggleable(
                                    value = uiState.settings.autoCalibrationEnabled,
                                    role = Role.Switch,
                                    onValueChange = { enabled ->
                                        calibrationSavedMsg = false
                                        onToggleAutoCalibration(enabled)
                                    }
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.label_auto_calibration),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PureWhite
                                )
                                Text(
                                    text = stringResource(R.string.desc_auto_calibration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateMuted
                                )
                            }
                            Switch(
                                checked = uiState.settings.autoCalibrationEnabled,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OledBlack,
                                    checkedTrackColor = ElectricEmerald,
                                    uncheckedThumbColor = SlateMuted,
                                    uncheckedTrackColor = CarbonElevated
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("switch_auto_calibration")
                            )
                        }

                        val detectedText = buildString {
                            if (uiState.settings.detectedCellCount > 0) {
                                append("${uiState.settings.detectedCellCount}S · ")
                            }
                            append("${uiState.settings.packNominalAh.toInt()} Ah · ")
                            append("${"%.1f".format(uiState.settings.packNominalVoltage)} V · ")
                            append("${uiState.settings.packCapacityWh.toInt()} Wh")
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CarbonElevated,
                            border = BorderStroke(1.dp, HairlineBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (uiState.settings.autoCalibrationEnabled) {
                                        "Active profile"
                                    } else {
                                        "Manual profile"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateMuted
                                )
                                Text(
                                    text = detectedText,
                                    style = MonoLabelSmall,
                                    color = ElectricEmerald,
                                    modifier = Modifier.testTag("detected_pack_summary")
                                )
                            }
                        }

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
                                enabled = !uiState.settings.autoCalibrationEnabled,
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
                                enabled = !uiState.settings.autoCalibrationEnabled,
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
                            Text(
                                text = when {
                                    uiState.settings.autoCalibrationEnabled -> "Synced from BMS telemetry"
                                    calibrationSavedMsg -> "Manual calibration saved"
                                    else -> "Used to estimate remaining time"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (calibrationSavedMsg || uiState.settings.autoCalibrationEnabled) {
                                    ElectricEmerald
                                } else {
                                    SlateMuted
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp)
                            )

                            if (!uiState.settings.autoCalibrationEnabled) {
                                Button(
                                    onClick = {
                                        val wh = capacityText.toFloatOrNull() ?: uiState.settings.packCapacityWh
                                        val v = nominalVText.toFloatOrNull() ?: uiState.settings.packNominalVoltage
                                        onSaveCalibration(wh, v)
                                        calibrationSavedMsg = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricEmerald,
                                        contentColor = OledBlack
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("save_calibration_button")
                                ) {
                                    Text(stringResource(R.string.action_save))
                                }
                            }
                        }
                    }
                }
            }

            // Group 4: GitHub Release Check & In-App Updater
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
                            Text(
                                text = "App updates",
                                style = MaterialTheme.typography.titleLarge,
                                color = PureWhite
                            )
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
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CarbonElevated,
                                contentColor = PureWhite
                            ),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("check_updates_button")
                        ) {
                            if (uiState.updateCheckResult is UpdateCheckResult.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = PrecisionCyan
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking GitHub Releases…")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    tint = ElectricEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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
                                        text = "Up to date (v${result.currentVersion})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ElectricEmerald
                                    )
                                }
                            }
                            is UpdateCheckResult.UpdateAvailable -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = EmeraldDim,
                                    border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Update available: ${result.releaseTitle} (${result.latestVersion})",
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
                                                text = "${result.apkFileName}$sizeLabel",
                                                style = MonoLabelSmall,
                                                color = ElectricEmerald
                                            )
                                        }
                                        when (val installState = uiState.apkInstallState) {
                                            is ApkInstallState.Downloading -> {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = "Downloading APK… ${installState.progressPercent}%",
                                                        style = MonoLabelSmall,
                                                        color = ElectricEmerald
                                                    )
                                                    LinearProgressIndicator(
                                                        progress = { installState.progressPercent / 100f },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        color = ElectricEmerald,
                                                        trackColor = CarbonElevated
                                                    )
                                                }
                                            }
                                            is ApkInstallState.NeedsInstallPermission -> {
                                                Text(
                                                    text = "Allow VoltCore to install updates in Android Settings, then tap Install APK.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = CautionAmber
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = onOpenUnknownSourcesSettings,
                                                        shape = RoundedCornerShape(10.dp),
                                                        border = BorderStroke(1.dp, CautionAmber),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 48.dp)
                                                    ) {
                                                        Text("Allow Installs", color = CautionAmber)
                                                    }
                                                    Button(
                                                        onClick = onRetryPendingApkInstall,
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = ElectricEmerald,
                                                            contentColor = OledBlack
                                                        ),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 48.dp)
                                                    ) {
                                                        Text("Install APK")
                                                    }
                                                }
                                            }
                                            is ApkInstallState.ReadyToInstall -> {
                                                Button(
                                                    onClick = onRetryPendingApkInstall,
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = ElectricEmerald,
                                                        contentColor = OledBlack
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 48.dp)
                                                        .testTag("download_apk_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.SystemUpdate,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Install Verified APK")
                                                }
                                            }
                                            is ApkInstallState.SignatureMismatch -> {
                                                Text(
                                                    text = installState.reason,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = CautionAmber
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            context.startActivity(
                                                                Intent(
                                                                    Intent.ACTION_VIEW,
                                                                    Uri.parse(installState.fallbackUrl)
                                                                )
                                                            )
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        border = BorderStroke(1.dp, HairlineBorder),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 48.dp)
                                                    ) {
                                                        Text("Open in Browser", color = PureWhite)
                                                    }
                                                    Button(
                                                        onClick = onUninstallForCleanInstall,
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = CriticalCoral,
                                                            contentColor = OledBlack
                                                        ),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 48.dp)
                                                    ) {
                                                        Text("Uninstall First")
                                                    }
                                                }
                                            }
                                            is ApkInstallState.Error -> {
                                                Text(
                                                    text = installState.message,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = CriticalCoral
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = { onDownloadAndInstallApk(result.apkDownloadUrl) },
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = ElectricEmerald,
                                                            contentColor = OledBlack
                                                        ),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 48.dp)
                                                    ) {
                                                        Text("Retry Download")
                                                    }
                                                    installState.fallbackUrl?.let { url ->
                                                        OutlinedButton(
                                                            onClick = {
                                                                context.startActivity(
                                                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                                )
                                                            },
                                                            shape = RoundedCornerShape(10.dp),
                                                            border = BorderStroke(1.dp, HairlineBorder),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .heightIn(min = 48.dp)
                                                        ) {
                                                            Text("Browser", color = PureWhite)
                                                        }
                                                    }
                                                }
                                            }
                                            ApkInstallState.Idle -> {
                                                Button(
                                                    onClick = { onDownloadAndInstallApk(result.apkDownloadUrl) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = ElectricEmerald,
                                                        contentColor = OledBlack
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 48.dp)
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

            // Group 5: Offline Telemetry Cache
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Offline telemetry cache",
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
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("clear_cache_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = CriticalCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear cache", color = PureWhite)
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
    step: Float,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = valueLabel,
                    style = MonoMetricMedium,
                    color = if (checked) ElectricEmerald else SlateMuted
                )
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OledBlack,
                        checkedTrackColor = ElectricEmerald,
                        uncheckedThumbColor = SlateMuted,
                        uncheckedTrackColor = CarbonElevated
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(testTag)
                )
            }
        }

        if (checked) {
            val coerced = value.coerceIn(valueRange.start, valueRange.endInclusive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        onValueChange((coerced - step).coerceIn(valueRange.start, valueRange.endInclusive))
                    },
                    enabled = coerced > valueRange.start,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease $title",
                        tint = if (coerced > valueRange.start) PureWhite else SlateMuted
                    )
                }

                Slider(
                    value = coerced,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricEmerald,
                        activeTrackColor = ElectricEmerald,
                        inactiveTrackColor = CarbonElevated
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        onValueChange((coerced + step).coerceIn(valueRange.start, valueRange.endInclusive))
                    },
                    enabled = coerced < valueRange.endInclusive,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase $title",
                        tint = if (coerced < valueRange.endInclusive) PureWhite else SlateMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) EmeraldDim else CarbonElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) ElectricEmerald else HairlineBorder
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) ElectricEmerald else SlateMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.VoltCoreViewModel
import com.example.ui.components.BleConnectionSheet
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.VoltCoreTheme

private const val ROUTE_MONITOR = "monitor"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_SETTINGS = "settings"

private data class VoltNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navDestinations = listOf(
    VoltNavDestination(
        route = ROUTE_MONITOR,
        labelRes = R.string.nav_monitor,
        selectedIcon = Icons.Rounded.Bolt,
        unselectedIcon = Icons.Outlined.Bolt
    ),
    VoltNavDestination(
        route = ROUTE_DIAGNOSTICS,
        labelRes = R.string.nav_diagnostics,
        selectedIcon = Icons.Rounded.Timeline,
        unselectedIcon = Icons.Outlined.Timeline
    ),
    VoltNavDestination(
        route = ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(OledBlack.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(CarbonSurface.toArgb())
        )
        setContent {
            VoltCoreTheme {
                VoltCoreApp()
            }
        }
    }
}

@Composable
fun VoltCoreApp(
    viewModel: VoltCoreViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val monitorState by viewModel.monitorUiState.collectAsStateWithLifecycle()
    val diagnosticsState by viewModel.diagnosticsUiState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val isSheetOpen by viewModel.isConnectionSheetOpen.collectAsStateWithLifecycle()

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
        viewModel.startBleScan()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = OledBlack,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = CarbonSurface,
                        tonalElevation = 0.dp
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == dest.route
                            } == true
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricEmerald,
                                    selectedTextColor = PureWhite,
                                    indicatorColor = EmeraldDim,
                                    unselectedIconColor = SlateMuted,
                                    unselectedTextColor = SlateMuted
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = CarbonSurface
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == dest.route
                            } == true
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricEmerald,
                                    selectedTextColor = PureWhite,
                                    indicatorColor = EmeraldDim,
                                    unselectedIconColor = SlateMuted,
                                    unselectedTextColor = SlateMuted
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_MONITOR
                    ) {
                        composable(ROUTE_MONITOR) {
                            MonitorScreen(
                                uiState = monitorState,
                                onOpenConnectionSheet = { viewModel.openConnectionSheet() },
                                onDisconnectBle = { viewModel.disconnectBle() },
                                onOpenDashboard = { viewModel.openStationDashboard() },
                                onCloseDashboard = { viewModel.closeStationDashboard() },
                                onConnectSavedStation = { station ->
                                    viewModel.connectSavedStation(station)
                                },
                                onRemoveSavedStation = { address ->
                                    viewModel.removeSavedStation(address)
                                },
                                onSavePluggedDevice = { id, name, port, watts ->
                                    viewModel.savePluggedDevice(id, name, port, watts)
                                },
                                onTogglePluggedDevice = { viewModel.togglePluggedDevice(it) },
                                onDeletePluggedDevice = { viewModel.deletePluggedDevice(it) }
                            )
                        }

                        composable(ROUTE_DIAGNOSTICS) {
                            DiagnosticsScreen(uiState = diagnosticsState)
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                uiState = settingsState,
                                onSelectDisplayDensity = { density ->
                                    viewModel.setDisplayDensity(density)
                                },
                                onSelectTemperatureUnit = { unit ->
                                    viewModel.setTemperatureUnit(unit)
                                },
                                onToggleAutoCalibration = { enabled ->
                                    viewModel.setAutoCalibrationEnabled(enabled)
                                },
                                onSaveCalibration = { wh, v ->
                                    viewModel.savePackCalibration(wh, v)
                                },
                                onUpdateChargeAlert = { enabled, target ->
                                    viewModel.updateChargeAlert(enabled, target)
                                },
                                onUpdateLowBatteryAlert = { enabled, threshold ->
                                    viewModel.updateLowBatteryAlert(enabled, threshold)
                                },
                                onUpdateHighTempAlert = { enabled, temp ->
                                    viewModel.updateHighTempAlert(enabled, temp)
                                },
                                onUpdateColdChargeAlert = { enabled, temp ->
                                    viewModel.updateColdChargeAlert(enabled, temp)
                                },
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                },
                                onSendTestAlert = { viewModel.sendTestNotification() },
                                onCheckForUpdates = { slug -> viewModel.checkForUpdates(slug) },
                                onDownloadAndInstallApk = { url -> viewModel.downloadAndInstallUpdate(url) },
                                onRetryPendingApkInstall = { viewModel.retryPendingApkInstall() },
                                onOpenUnknownSourcesSettings = { viewModel.openUnknownSourcesSettings() },
                                onUninstallForCleanInstall = { viewModel.uninstallCurrentAppForCleanInstall() },
                                onClearCache = { viewModel.clearOfflineHistory() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (isSheetOpen) {
        BleConnectionSheet(
            bleState = monitorState.bleState,
            onRequestBlePermissions = {
                blePermissionLauncher.launch(viewModel.requiredBlePermissions())
            },
            onStartScan = { viewModel.startBleScan() },
            onStopScan = { viewModel.stopBleScan() },
            onTogglePowerStationsOnly = { enabled -> viewModel.setBlePowerStationsOnly(enabled) },
            onConnectDevice = { address, name -> viewModel.connectBleDevice(address, name) },
            onDisconnect = { viewModel.disconnectBle() },
            onDismiss = { viewModel.closeConnectionSheet() }
        )
    }
}

package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AlertEventEntity
import com.example.data.local.BmsAlertType
import com.example.data.local.BmsDao
import com.example.data.local.TelemetrySnapshotEntity
import com.example.data.prefs.AppSettings

class BatteryAlertNotifier(
    private val context: Context,
    private val dao: BmsDao
) {
    companion object {
        const val CHANNEL_ID = "voltcore_bms_alerts"
        private const val ALERT_COOLDOWN_MS = 120_000L
    }

    private val lastAlertTimestampByType = mutableMapOf<BmsAlertType, Long>()

    init {
        ensureNotificationChannel()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Health & Charge Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for charge completion, low battery, and high cell temperature"
            }
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun evaluateAndNotify(snapshot: TelemetrySnapshotEntity, settings: AppSettings) {
        val now = System.currentTimeMillis()

        if (settings.chargeCompleteAlertEnabled &&
            snapshot.isCharging &&
            snapshot.socPercent >= settings.chargeTargetPercent
        ) {
            triggerAlert(
                type = BmsAlertType.CHARGE_COMPLETE,
                title = "Target Charge Reached (${snapshot.socPercent}%)",
                message = "${snapshot.deviceName} reached ${snapshot.socPercent}% at ${"%.1f".format(snapshot.voltageVolts)}V.",
                metricValue = "${snapshot.socPercent}%",
                nowMs = now
            )
        }

        if (settings.lowBatteryAlertEnabled &&
            !snapshot.isCharging &&
            snapshot.socPercent <= settings.lowBatteryThresholdPercent
        ) {
            triggerAlert(
                type = BmsAlertType.LOW_SOC,
                title = "Low Battery Warning (${snapshot.socPercent}%)",
                message = "${snapshot.deviceName} dropped to ${snapshot.socPercent}%. Recharge soon.",
                metricValue = "${snapshot.socPercent}%",
                nowMs = now
            )
        }

        if (settings.highTempAlertEnabled &&
            snapshot.temperatureCelsius >= settings.highTempThresholdCelsius
        ) {
            val tempText = com.example.data.ble.PowerMath.formatTempWithUnit(
                snapshot.temperatureCelsius,
                settings.temperatureUnit
            )
            val limitText = com.example.data.ble.PowerMath.formatTempWithUnit(
                settings.highTempThresholdCelsius,
                settings.temperatureUnit,
                decimals = 0
            )
            triggerAlert(
                type = BmsAlertType.HIGH_TEMP,
                title = "High Pack Temperature ($tempText)",
                message = "Battery thermal sensor exceeded $limitText threshold.",
                metricValue = tempText,
                nowMs = now
            )
        }

        if (settings.coldChargeAlertEnabled &&
            snapshot.isCharging &&
            snapshot.temperatureCelsius <= settings.coldChargeThresholdCelsius
        ) {
            val tempText = com.example.data.ble.PowerMath.formatTempWithUnit(
                snapshot.temperatureCelsius,
                settings.temperatureUnit
            )
            val limitText = com.example.data.ble.PowerMath.formatTempWithUnit(
                settings.coldChargeThresholdCelsius,
                settings.temperatureUnit,
                decimals = 0
            )
            triggerAlert(
                type = BmsAlertType.COLD_CHARGE,
                title = "Sub-Zero Charge Warning ($tempText)",
                message = "${snapshot.deviceName} is charging at $tempText (limit $limitText). Stop LiFePO4 charging to protect cells.",
                metricValue = tempText,
                nowMs = now
            )
        }
    }

    suspend fun sendTestAlert() {
        triggerAlert(
            type = BmsAlertType.CHARGE_COMPLETE,
            title = "VoltCore Alert Active",
            message = "Battery health and charge completion notifications are configured.",
            metricValue = "OK",
            nowMs = System.currentTimeMillis(),
            bypassCooldown = true
        )
    }

    private suspend fun triggerAlert(
        type: BmsAlertType,
        title: String,
        message: String,
        metricValue: String,
        nowMs: Long,
        bypassCooldown: Boolean = false
    ) {
        val lastFired = lastAlertTimestampByType[type] ?: 0L
        if (!bypassCooldown && (nowMs - lastFired) < ALERT_COOLDOWN_MS) return

        lastAlertTimestampByType[type] = nowMs

        dao.insertAlertEvent(
            AlertEventEntity(
                alertType = type.name,
                title = title,
                message = message,
                metricValue = metricValue,
                timestampMs = nowMs
            )
        )

        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1000 + type.ordinal, notification)
        } catch (_: SecurityException) {
            // Permission revoked mid-session
        }
    }
}

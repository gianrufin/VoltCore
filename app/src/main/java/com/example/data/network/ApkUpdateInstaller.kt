package com.example.data.network

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed interface ApkInstallState {
    data object Idle : ApkInstallState
    data class Downloading(val progressPercent: Int) : ApkInstallState
    data class Installing(val statusMessage: String = "Preparing system package installer…") : ApkInstallState
    data class NeedsInstallPermission(val apkFile: File, val fallbackUrl: String? = null) : ApkInstallState
    data class ReadyToInstall(val apkFile: File, val fallbackUrl: String? = null) : ApkInstallState
    data class SignatureMismatch(val reason: String, val fallbackUrl: String) : ApkInstallState
    data class Error(val message: String, val fallbackUrl: String?) : ApkInstallState
}

class ApkUpdateInstaller(private val context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val sessionRequestCounter = AtomicInteger(100)
    private var activeReceiver: BroadcastReceiver? = null

    suspend fun downloadAndPrepareInstall(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): ApkInstallState = withContext(Dispatchers.IO) {
        val updatesDir = File(appContext.cacheDir, "updates").apply {
            if (!exists()) mkdirs()
        }
        updatesDir.listFiles()?.forEach { it.delete() }
        val targetFile = File(updatesDir, "VoltCore-update.apk")

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "VoltCore-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ApkInstallState.Error(
                        message = "Download failed (HTTP ${response.code}).",
                        fallbackUrl = downloadUrl
                    )
                }
                val body = response.body ?: return@withContext ApkInstallState.Error(
                    message = "Empty APK download response.",
                    fallbackUrl = downloadUrl
                )
                val contentLength = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var totalRead = 0L
                        var lastReported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalRead += read
                            if (contentLength > 0L) {
                                val pct = ((totalRead * 100L) / contentLength).toInt().coerceIn(0, 100)
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (!isValidApkZipHeader(targetFile)) {
                targetFile.delete()
                return@withContext ApkInstallState.Error(
                    message = "Downloaded file is not a valid Android APK package.",
                    fallbackUrl = downloadUrl
                )
            }

            val validation = validateArchiveAgainstInstalled(targetFile, downloadUrl)
            if (validation != null) {
                return@withContext validation
            }

            if (!canInstallPackages()) {
                return@withContext ApkInstallState.NeedsInstallPermission(
                    apkFile = targetFile,
                    fallbackUrl = downloadUrl
                )
            }

            ApkInstallState.ReadyToInstall(apkFile = targetFile, fallbackUrl = downloadUrl)
        } catch (e: Exception) {
            targetFile.delete()
            ApkInstallState.Error(
                message = e.localizedMessage ?: "Failed to download APK update.",
                fallbackUrl = downloadUrl
            )
        }
    }

    fun canInstallPackages(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()
    }

    fun launchInstallUnknownAppsSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    /**
     * Installs the verified APK via Android's modern PackageInstaller.Session API so OEM devices
     * handle staged updates and surface real conflict/failure reasons cleanly. Falls back to
     * FileProvider ACTION_VIEW if session creation fails.
     */
    fun installApkWithSession(
        apkFile: File,
        fallbackUrl: String?,
        onStateChanged: (ApkInstallState) -> Unit
    ) {
        if (!apkFile.exists()) {
            onStateChanged(
                ApkInstallState.Error(
                    message = "Update APK file was removed from cache. Download it again.",
                    fallbackUrl = fallbackUrl
                )
            )
            return
        }

        if (!canInstallPackages()) {
            onStateChanged(ApkInstallState.NeedsInstallPermission(apkFile, fallbackUrl))
            return
        }

        onStateChanged(ApkInstallState.Installing("Staging update package…"))

        try {
            val packageInstaller = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(appContext.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId).use { session ->
                FileInputStream(apkFile).use { input ->
                    session.openWrite("VoltCore-update.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output, bufferSize = 32 * 1024)
                        session.fsync(output)
                    }
                }

                val action = "${appContext.packageName}.ACTION_APK_INSTALL_RESULT_${sessionRequestCounter.incrementAndGet()}"
                unregisterActiveReceiver()

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val status = intent.getIntExtra(
                            PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE
                        )
                        val statusMsg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

                        when (status) {
                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                val confirmIntent = IntentCompat.getParcelableExtra(
                                    intent,
                                    Intent.EXTRA_INTENT,
                                    Intent::class.java
                                )
                                if (confirmIntent != null) {
                                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    onStateChanged(
                                        ApkInstallState.Installing("Confirm update in Android system prompt…")
                                    )
                                    appContext.startActivity(confirmIntent)
                                } else {
                                    unregisterActiveReceiver()
                                    if (!launchFileProviderInstaller(apkFile)) {
                                        onStateChanged(
                                            ApkInstallState.Error(
                                                message = "System installer confirmation was missing.",
                                                fallbackUrl = fallbackUrl
                                            )
                                        )
                                    } else {
                                        onStateChanged(ApkInstallState.ReadyToInstall(apkFile, fallbackUrl))
                                    }
                                }
                            }

                            PackageInstaller.STATUS_SUCCESS -> {
                                unregisterActiveReceiver()
                                apkFile.delete()
                                onStateChanged(ApkInstallState.Idle)
                            }

                            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                                unregisterActiveReceiver()
                                onStateChanged(
                                    ApkInstallState.SignatureMismatch(
                                        reason = "Package conflict: this release APK has a different signing certificate than the currently installed build. Uninstall VoltCore once, then install the GitHub APK.",
                                        fallbackUrl = fallbackUrl.orEmpty()
                                    )
                                )
                            }

                            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                                unregisterActiveReceiver()
                                onStateChanged(ApkInstallState.ReadyToInstall(apkFile, fallbackUrl))
                            }

                            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                                unregisterActiveReceiver()
                                onStateChanged(
                                    ApkInstallState.Error(
                                        message = "Incompatible APK build (${statusMsg ?: "device ABI/SDK mismatch"}).",
                                        fallbackUrl = fallbackUrl
                                    )
                                )
                            }

                            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                                unregisterActiveReceiver()
                                onStateChanged(
                                    ApkInstallState.Error(
                                        message = "Not enough free device storage to install the update.",
                                        fallbackUrl = fallbackUrl
                                    )
                                )
                            }

                            else -> {
                                unregisterActiveReceiver()
                                onStateChanged(
                                    ApkInstallState.Error(
                                        message = statusMsg?.takeIf { it.isNotBlank() }
                                            ?: "Android package installer failed (code $status).",
                                        fallbackUrl = fallbackUrl
                                    )
                                )
                            }
                        }
                    }
                }

                activeReceiver = receiver
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    IntentFilter(action),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                val callbackIntent = Intent(action).setPackage(appContext.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val statusPendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    sessionId,
                    callbackIntent,
                    flags
                )
                session.commit(statusPendingIntent.intentSender)
            }
        } catch (e: Exception) {
            unregisterActiveReceiver()
            if (launchFileProviderInstaller(apkFile)) {
                onStateChanged(ApkInstallState.ReadyToInstall(apkFile, fallbackUrl))
            } else {
                onStateChanged(
                    ApkInstallState.Error(
                        message = e.localizedMessage ?: "Unable to start Android package installer.",
                        fallbackUrl = fallbackUrl
                    )
                )
            }
        }
    }

    fun launchFileProviderInstaller(apkFile: File): Boolean {
        return try {
            val contentUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchUninstallFlow() {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${appContext.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun unregisterActiveReceiver() {
        activeReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
            activeReceiver = null
        }
    }

    private fun isValidApkZipHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 1024) return false
        return file.inputStream().use { stream ->
            val header = ByteArray(4)
            val read = stream.read(header)
            read == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        }
    }

    @Suppress("DEPRECATION")
    private fun validateArchiveAgainstInstalled(apkFile: File, fallbackUrl: String): ApkInstallState? {
        val pm = appContext.packageManager
        val archiveInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES).toLong()
                )
            )
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
            } else {
                PackageManager.GET_SIGNATURES
            }
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } ?: return ApkInstallState.Error(
            message = "Could not parse the downloaded APK manifest.",
            fallbackUrl = fallbackUrl
        )

        if (archiveInfo.packageName != appContext.packageName) {
            return ApkInstallState.Error(
                message = "Package ID mismatch (${archiveInfo.packageName} vs ${appContext.packageName}).",
                fallbackUrl = fallbackUrl
            )
        }

        val installedInfo: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(
                        (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES).toLong()
                    )
                )
            } else {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
                } else {
                    PackageManager.GET_SIGNATURES
                }
                pm.getPackageInfo(appContext.packageName, flags)
            }
        } catch (_: Exception) {
            return null
        }

        val installedVersion = PackageInfoCompat.getLongVersionCode(installedInfo)
        val archiveVersion = PackageInfoCompat.getLongVersionCode(archiveInfo)
        if (archiveVersion < installedVersion) {
            return ApkInstallState.Error(
                message = "Android blocks downgrading versionCode ($archiveVersion < $installedVersion). Re-tag a newer GitHub release to upgrade.",
                fallbackUrl = fallbackUrl
            )
        }

        val installedDigests = extractCertDigests(installedInfo)
        val archiveDigests = extractCertDigests(archiveInfo)
        if (installedDigests.isNotEmpty() && archiveDigests.isNotEmpty() &&
            installedDigests.intersect(archiveDigests).isEmpty()
        ) {
            return ApkInstallState.SignatureMismatch(
                reason = "This GitHub APK was signed with a different certificate than your current install (which causes Android's \"App not installed\" conflict). Uninstall VoltCore once, then install the GitHub APK.",
                fallbackUrl = fallbackUrl
            )
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun extractCertDigests(info: PackageInfo): Set<String> {
        val rawSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            val modern = when {
                signingInfo == null -> emptyArray()
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
            if (!modern.isNullOrEmpty()) modern else (info.signatures ?: emptyArray())
        } else {
            info.signatures ?: emptyArray()
        }

        val sha256 = MessageDigest.getInstance("SHA-256")
        return rawSignatures.mapNotNull { sig ->
            runCatching {
                sha256.digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
            }.getOrNull()
        }.toSet()
    }
}

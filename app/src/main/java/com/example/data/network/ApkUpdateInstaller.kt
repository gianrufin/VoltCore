package com.example.data.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface ApkInstallState {
    data object Idle : ApkInstallState
    data class Downloading(val progressPercent: Int) : ApkInstallState
    data class NeedsInstallPermission(val apkFile: File) : ApkInstallState
    data class ReadyToInstall(val apkFile: File) : ApkInstallState
    data class SignatureMismatch(val reason: String, val fallbackUrl: String) : ApkInstallState
    data class Error(val message: String, val fallbackUrl: String?) : ApkInstallState
}

class ApkUpdateInstaller(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadAndPrepareInstall(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): ApkInstallState = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply {
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
                return@withContext ApkInstallState.NeedsInstallPermission(targetFile)
            }

            ApkInstallState.ReadyToInstall(targetFile)
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
            context.packageManager.canRequestPackageInstalls()
    }

    fun launchInstallUnknownAppsSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun launchApkInstaller(apkFile: File): Boolean {
        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchUninstallFlow() {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo: PackageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: return ApkInstallState.Error(
                message = "Could not parse the downloaded APK manifest.",
                fallbackUrl = fallbackUrl
            )

        if (archiveInfo.packageName != context.packageName) {
            return ApkInstallState.Error(
                message = "Package ID mismatch (${archiveInfo.packageName} vs ${context.packageName}).",
                fallbackUrl = fallbackUrl
            )
        }

        val installedInfo: PackageInfo = try {
            pm.getPackageInfo(context.packageName, flags)
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
                reason = "This GitHub APK was signed with a different certificate than your current install (which causes Android's \"App not installed\" error). Uninstall VoltCore once or download in browser after uninstalling.",
                fallbackUrl = fallbackUrl
            )
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun extractCertDigests(info: PackageInfo): Set<String> {
        val rawSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            when {
                signingInfo == null -> emptyArray()
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            info.signatures ?: emptyArray()
        }

        val sha256 = MessageDigest.getInstance("SHA-256")
        return rawSignatures.map { sig ->
            sha256.digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
        }.toSet()
    }
}

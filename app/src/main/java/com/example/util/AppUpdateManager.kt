package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String,
    val isNewUpdateAvailable: Boolean
)

class AppUpdateManager(private val context: Context) {
    private val TAG = "AppUpdateManager"
    private val GITHUB_API_URL = "https://api.github.com/repos/abhisheksati132/bds-apk/releases/latest"

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "v1.0.0"
        } catch (e: Exception) {
            "v1.0.0"
        }
    }

    suspend fun checkForUpdates(): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PrivateMessengerApp")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "GitHub API returned HTTP $responseCode")
                return@withContext Result.failure(IllegalStateException("No release found (HTTP $responseCode)"))
            }

            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)

            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName)
            val body = json.optString("body", "Bug fixes and performance improvements.")
            val publishedAt = json.optString("published_at", "")

            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (downloadUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No APK asset found in latest release"))
            }

            val currentVer = getCurrentVersionName().trim().lowercase().removePrefix("v")
            val latestVer = tagName.trim().lowercase().removePrefix("v")

            // If tag is not identical to current version name, consider it an update
            val hasUpdate = latestVer.isNotBlank() && latestVer != currentVer

            val releaseInfo = AppReleaseInfo(
                tagName = tagName,
                releaseName = if (name.isNotBlank()) name else tagName,
                releaseNotes = body,
                downloadUrl = downloadUrl,
                apkSizeBytes = apkSize,
                publishedAt = publishedAt,
                isNewUpdateAvailable = hasUpdate
            )

            Log.d(TAG, "Check update success: current=$currentVer, latest=$latestVer, hasUpdate=$hasUpdate")
            Result.success(releaseInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Float, Long, Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val apkFile = File(updateDir, "PrivateMessenger.apk")
            if (apkFile.exists()) apkFile.delete()

            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PrivateMessengerApp")
                connectTimeout = 15000
                readTimeout = 30000
            }

            // Follow redirect manually if needed
            var redirectConn = conn
            var status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                val newUrl = conn.getHeaderField("Location")
                redirectConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "PrivateMessengerApp")
                }
            }

            val contentLength = redirectConn.contentLengthLong
            var totalBytesRead = 0L

            redirectConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        val progress = if (contentLength > 0) {
                            (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else 0.5f
                        withContext(Dispatchers.Main) {
                            onProgress(progress, totalBytesRead, contentLength)
                        }
                    }
                    output.flush()
                }
            }

            Log.d(TAG, "APK Download completed: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update APK: ${e.message}")
            null
        }
    }

    fun installApk(apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) return false

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}")
            false
        }
    }
}

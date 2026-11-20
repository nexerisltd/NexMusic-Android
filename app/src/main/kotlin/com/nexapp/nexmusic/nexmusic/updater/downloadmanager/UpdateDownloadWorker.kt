package com.nexapp.nexmusic.nexmusic.updater.downloadmanager

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nexapp.nexmusic.BuildConfig
import com.nexapp.nexmusic.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens a download connection, following redirects by hand so the bearer token
 * is never forwarded to the storage host behind the redirect (GitHub asset
 * downloads redirect to a pre-signed URL that must be hit without auth).
 */
private fun openDownload(urlStr: String, accept: String): HttpURLConnection {
  var target = urlStr
  var hops = 0
  while (true) {
    val targetUrl = URL(target)
    val conn = targetUrl.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 15000
    conn.readTimeout = 30000
    conn.instanceFollowRedirects = false
    conn.setRequestProperty("Accept", accept)
    conn.setRequestProperty("User-Agent", "NexMusic-Android")
    if (targetUrl.host == "api.github.com" && BuildConfig.UPDATE_GITHUB_TOKEN.isNotBlank()) {
      conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.UPDATE_GITHUB_TOKEN}")
    }
    conn.connect()
    if (conn.responseCode in intArrayOf(301, 302, 303, 307, 308)) {
      val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without Location header")
      conn.disconnect()
      target = URL(targetUrl, location).toString()
      hops++
      if (hops > 5) throw IOException("Too many redirects")
      continue
    }
    return conn
  }
}

class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
  CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
      val apkUrl = inputData.getString("apk_url") ?: return@withContext Result.failure()
      val assetApiUrl = inputData.getString("asset_api_url") ?: ""
      val version = inputData.getString("version") ?: "unknown"
      val fileSize = inputData.getString("file_size") ?: ""

      // Prefer the API asset route: the plain browser_download_url 404s while
      // the release repository is private.
      val effectiveUrl =
        if (assetApiUrl.isNotBlank() && BuildConfig.UPDATE_GITHUB_TOKEN.isNotBlank()) {
          assetApiUrl
        } else {
          apkUrl
        }

      try {
        val startingNotification =
          DownloadNotificationManager.getDownloadStartingNotification(version, fileSize)
        val foregroundInfo =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
              DownloadNotificationManager.NOTIFICATION_ID,
              startingNotification,
              ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
          } else {
            ForegroundInfo(DownloadNotificationManager.NOTIFICATION_ID, startingNotification)
          }
        setForeground(foregroundInfo)
      } catch (e: Exception) {}

      try {
        val isAssetApi = effectiveUrl.startsWith("https://api.github.com/")
        val connection = openDownload(effectiveUrl, if (isAssetApi) "application/octet-stream" else "*/*")

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
          DownloadNotificationManager.showDownloadFailed(
            version,
            context.getString(R.string.server_error, connection.responseCode)
          )
          if (connection.responseCode >= 500) {
            return@withContext Result.retry()
          }
          return@withContext Result.failure()
        }

        val fileLength = connection.contentLength
        val inputStream = connection.inputStream

        val downloadDir =
          File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "nex_updates")
        if (!downloadDir.exists()) {
          downloadDir.mkdirs()
        }

        val isZip = apkUrl.contains("nightly.link") || apkUrl.endsWith(".zip")
        val downloadFile =
          if (isZip) File(downloadDir, "nex_temp.zip") else File(downloadDir, "nexmusic.apk")
        val outputStream = FileOutputStream(downloadFile)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead: Long = 0
        var lastProgress = -1
        var lastNotificationTime = 0L

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
          if (isStopped) {
            outputStream.close()
            inputStream.close()
            connection.disconnect()
            if (downloadFile.exists()) {
              downloadFile.delete()
            }
            return@withContext Result.retry()
          }

          outputStream.write(buffer, 0, bytesRead)
          totalBytesRead += bytesRead

          if (fileLength > 0) {
            val progress = (totalBytesRead.toFloat() / fileLength.toFloat() * 100).toInt()
            val currentTime = System.currentTimeMillis()

            if (progress > lastProgress && currentTime - lastNotificationTime >= 1000) {
              lastProgress = progress
              lastNotificationTime = currentTime

              val progressNotification =
                DownloadNotificationManager.getDownloadProgressNotification(progress, version)
              val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE)
                  as android.app.NotificationManager
              notificationManager.notify(
                DownloadNotificationManager.NOTIFICATION_ID,
                progressNotification
              )

              setProgress(workDataOf("progress" to progress.toFloat() / 100f))
            }
          }
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()
        connection.disconnect()

        val finalFile =
          if (isZip) {
            val targetApkFile = File(downloadDir, "nexmusic.apk")
            var extracted = false
            try {
              ZipInputStream(downloadFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                  if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                    FileOutputStream(targetApkFile).use { fos -> zis.copyTo(fos) }
                    extracted = true
                    break
                  }
                  entry = zis.nextEntry
                }
              }
            } catch (e: Exception) {
              if (downloadFile.exists()) downloadFile.delete()
              DownloadNotificationManager.showDownloadFailed(
                version,
                e.message ?: "Failed to extract zip file"
              )
              return@withContext Result.failure()
            } finally {
              if (downloadFile.exists()) {
                downloadFile.delete()
              }
            }
            if (!extracted) {
              DownloadNotificationManager.showDownloadFailed(version, "Could not find APK in zip")
              return@withContext Result.failure()
            }
            targetApkFile
          } else {
            downloadFile
          }

        if (version.startsWith("nightly-r")) {
          val runNumberString = version.removePrefix("nightly-r")
          val runNumber = runNumberString.toIntOrNull()
          if (runNumber != null) {
            val sharedPreferences =
              context.getSharedPreferences("update_settings", Context.MODE_PRIVATE)
            sharedPreferences.edit().putInt("last_installed_nightly_run", runNumber).apply()
          }
        }

        DownloadNotificationManager.showDownloadComplete(version, finalFile.absolutePath)

        Result.success(workDataOf("file_path" to finalFile.absolutePath))
      } catch (e: IOException) {
        DownloadNotificationManager.showDownloadFailed(
          version,
          e.message ?: context.getString(R.string.download_failed)
        )
        return@withContext Result.retry()
      } catch (e: Exception) {
        DownloadNotificationManager.showDownloadFailed(
          version,
          e.message ?: context.getString(R.string.download_failed)
        )
        Result.failure()
      }
    }
}

package com.astute.repo.domain

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.astute.repo.data.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstallManager @Inject constructor(
    private val application: Application
) {
    private val downloadManager: DownloadManager =
        application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    fun canInstallFromUnknownSources(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    suspend fun downloadAndInstall(app: AppEntry): Intent? {
        cleanupApkForApp(app.id)

        val fileName = "${app.id}_${app.versionCode}.apk"
        val destinationDir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("External files directory unavailable")
        val destinationFile = File(destinationDir, fileName)

        val request = DownloadManager.Request(Uri.parse(app.apkUrl))
            .setTitle("Downloading ${app.name}")
            .setDescription("v${app.versionName}")
            .setDestinationUri(Uri.fromFile(destinationFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        val downloadId = downloadManager.enqueue(request)
        updateState(app.id, DownloadState.Downloading(downloadId, 0, 0L, 0L))

        return when (val result = pollDownloadProgress(app.id, downloadId)) {
            is DownloadResult.Success -> {
                updateState(app.id, DownloadState.Downloaded(destinationFile.absolutePath))
                createInstallIntent(destinationFile)
            }
            is DownloadResult.Failure -> {
                updateState(app.id, DownloadState.Failed(result.reason))
                null
            }
        }
    }

    fun markInstalling(appId: String) {
        updateState(appId, DownloadState.Installing)
    }

    fun markIdle(appId: String) {
        updateState(appId, DownloadState.Idle)
    }

    fun cancelDownload(appId: String) {
        val state = _downloadStates.value[appId]
        if (state is DownloadState.Downloading) {
            downloadManager.remove(state.downloadId)
        }
        updateState(appId, DownloadState.Idle)
    }

    fun cleanupOldApks(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        val dir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            if (file.extension == "apk" && (now - file.lastModified()) > maxAgeMs) {
                file.delete()
            }
        }
    }

    private fun cleanupApkForApp(appId: String) {
        val dir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        dir.listFiles()?.filter { it.name.startsWith("${appId}_") }?.forEach { it.delete() }
    }

    private suspend fun pollDownloadProgress(
        appId: String,
        downloadId: Long
    ): DownloadResult = withContext(Dispatchers.IO) {
        while (true) {
            val cursor = downloadManager.query(
                DownloadManager.Query().setFilterById(downloadId)
            )
            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                return@withContext DownloadResult.Failure("Download removed or unavailable")
            }

            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            val bytesDownloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val bytesTotal = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val reason = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            )
            cursor.close()

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    updateState(appId, DownloadState.Downloading(downloadId, 100, bytesTotal, bytesTotal))
                    return@withContext DownloadResult.Success
                }
                DownloadManager.STATUS_FAILED -> {
                    val message = mapDownloadError(reason)
                    return@withContext DownloadResult.Failure(message)
                }
                else -> {
                    val progress = if (bytesTotal > 0) {
                        ((bytesDownloaded * 100) / bytesTotal).toInt()
                    } else 0
                    updateState(appId, DownloadState.Downloading(downloadId, progress, bytesDownloaded, bytesTotal))
                }
            }
            delay(POLL_INTERVAL_MS)
        }
        @Suppress("UNREACHABLE_CODE")
        DownloadResult.Failure("Unexpected exit")
    }

    private fun mapDownloadError(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network data error"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error"
        DownloadManager.ERROR_FILE_ERROR -> "File storage error"
        DownloadManager.ERROR_CANNOT_RESUME -> "Download cannot be resumed"
        else -> "Download failed"
    }

    private fun createInstallIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun updateState(appId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            if (state is DownloadState.Idle) remove(appId)
            else put(appId, state)
        }
    }

    private sealed interface DownloadResult {
        data object Success : DownloadResult
        data class Failure(val reason: String) : DownloadResult
    }

    companion object {
        private const val TAG = "InstallManager"
        private const val POLL_INTERVAL_MS = 300L
    }
}

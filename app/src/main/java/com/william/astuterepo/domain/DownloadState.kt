package com.william.astuterepo.domain

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(
        val downloadId: Long,
        val progress: Int,
        val bytesDownloaded: Long,
        val bytesTotal: Long
    ) : DownloadState
    data class Downloaded(val filePath: String) : DownloadState
    data object Installing : DownloadState
    data class Failed(val reason: String) : DownloadState
}

package com.william.astuterepo.ui.applist

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.william.astuterepo.data.model.AppEntry
import com.william.astuterepo.data.repository.AppRepository
import com.william.astuterepo.domain.AppWithStatus
import com.william.astuterepo.domain.DownloadState
import com.william.astuterepo.domain.InstallManager
import com.william.astuterepo.domain.InstallStatus
import com.william.astuterepo.domain.VersionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppListUiState(
    val apps: List<AppWithStatus> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedApp: AppWithStatus? = null,
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val showPermissionDialog: Boolean = false,
    val pendingInstallAppId: String? = null,
    val installIntent: Intent? = null
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val repository: AppRepository,
    private val versionChecker: VersionChecker,
    private val installManager: InstallManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        installManager.cleanupOldApks()

        viewModelScope.launch {
            repository.observeApps().collect { apps ->
                val appsWithStatus = apps.map { versionChecker.resolveStatus(it) }
                _uiState.value = _uiState.value.copy(apps = appsWithStatus)
            }
        }

        viewModelScope.launch {
            installManager.downloadStates.collect { states ->
                _uiState.value = _uiState.value.copy(downloadStates = states)
            }
        }

        refreshManifest()
    }

    fun refreshManifest() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                repository.refreshManifest()
                Log.d(TAG, "Manifest refresh completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh manifest", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun recheckStatuses() {
        val currentApps = _uiState.value.apps
        if (currentApps.isEmpty()) return
        val refreshed = currentApps.map { versionChecker.resolveStatus(it.app) }
        _uiState.value = _uiState.value.copy(apps = refreshed)

        // Clear download states for apps that finished installing or were cancelled
        val downloadStates = installManager.downloadStates.value
        refreshed.forEach { appWithStatus ->
            val dlState = downloadStates[appWithStatus.app.id]
            if (dlState is DownloadState.Installing || dlState is DownloadState.Downloaded) {
                installManager.markIdle(appWithStatus.app.id)
            }
        }
    }

    fun downloadAndInstall(app: AppEntry) {
        val currentState = _uiState.value.downloadStates[app.id]
        if (currentState is DownloadState.Downloading || currentState is DownloadState.Installing) {
            return
        }

        if (app.minSdk != null && Build.VERSION.SDK_INT < app.minSdk) {
            _uiState.value = _uiState.value.copy(
                error = "${app.name} requires Android API ${app.minSdk} but this device is API ${Build.VERSION.SDK_INT}"
            )
            return
        }

        if (!installManager.canInstallFromUnknownSources()) {
            _uiState.value = _uiState.value.copy(
                showPermissionDialog = true,
                pendingInstallAppId = app.id
            )
            return
        }

        viewModelScope.launch {
            try {
                val intent = installManager.downloadAndInstall(app)
                if (intent != null) {
                    installManager.markInstalling(app.id)
                    _uiState.value = _uiState.value.copy(installIntent = intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${app.name}", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Download failed"
                )
            }
        }
    }

    fun onPermissionDialogDismiss() {
        _uiState.value = _uiState.value.copy(
            showPermissionDialog = false,
            pendingInstallAppId = null
        )
    }

    fun onPermissionGrantResult() {
        val pendingId = _uiState.value.pendingInstallAppId
        _uiState.value = _uiState.value.copy(
            showPermissionDialog = false,
            pendingInstallAppId = null
        )
        if (pendingId != null && installManager.canInstallFromUnknownSources()) {
            val app = _uiState.value.apps.find { it.app.id == pendingId }?.app
            if (app != null) downloadAndInstall(app)
        }
    }

    fun onInstallIntentLaunched() {
        _uiState.value = _uiState.value.copy(installIntent = null)
    }

    fun cancelDownload(appId: String) {
        installManager.cancelDownload(appId)
    }

    fun selectApp(app: AppWithStatus) {
        _uiState.value = _uiState.value.copy(selectedApp = app)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedApp = null)
    }

    companion object {
        private const val TAG = "AppListViewModel"
    }
}

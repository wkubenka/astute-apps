package com.astute.repo.ui.applist

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astute.repo.data.model.AppEntry
import com.astute.repo.data.network.ConnectivityObserver
import com.astute.repo.data.repository.AppRepository
import com.astute.repo.data.repository.RefreshResult
import com.astute.repo.domain.AppWithStatus
import com.astute.repo.domain.DownloadState
import com.astute.repo.domain.InstallManager
import com.astute.repo.domain.VersionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ErrorState {
    data class Offline(
        val lastFetchTimeMillis: Long,
        val message: String
    ) : ErrorState

    data class Transient(val message: String) : ErrorState
}

data class AppListUiState(
    val apps: List<AppWithStatus> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val errorState: ErrorState? = null,
    val searchQuery: String = "",
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
    private val installManager: InstallManager,
    private val connectivityObserver: ConnectivityObserver
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

        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                val wasOffline = _uiState.value.isOffline
                if (online && wasOffline) {
                    refreshManifest()
                }
                if (!online) {
                    _uiState.value = _uiState.value.copy(isOffline = true)
                }
            }
        }

        refreshManifest()
    }

    fun refreshManifest() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.refreshManifest()) {
                is RefreshResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        errorState = null,
                        isOffline = false
                    )
                }
                is RefreshResult.Error -> {
                    if (result.hasCachedData) {
                        _uiState.value = _uiState.value.copy(
                            isOffline = true,
                            errorState = ErrorState.Offline(
                                lastFetchTimeMillis = repository.lastFetchTimeMillis,
                                message = "Showing cached data"
                            )
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            errorState = ErrorState.Transient(
                                result.exception.message ?: "Failed to load apps"
                            )
                        )
                    }
                }
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun recheckStatuses() {
        val currentApps = _uiState.value.apps
        if (currentApps.isEmpty()) return
        val refreshed = currentApps.map { versionChecker.resolveStatus(it.app) }

        val updatedSelected = _uiState.value.selectedApp?.let { selected ->
            refreshed.find { it.app.id == selected.app.id }
        }
        _uiState.value = _uiState.value.copy(
            apps = refreshed,
            selectedApp = updatedSelected ?: _uiState.value.selectedApp
        )

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

        if (app.apkUrl == null) {
            _uiState.value = _uiState.value.copy(
                errorState = ErrorState.Transient("${app.name} has no downloadable release")
            )
            return
        }

        if (app.minSdk != null && Build.VERSION.SDK_INT < app.minSdk) {
            _uiState.value = _uiState.value.copy(
                errorState = ErrorState.Transient(
                    "${app.name} requires Android API ${app.minSdk} but this device is API ${Build.VERSION.SDK_INT}"
                )
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
                    errorState = ErrorState.Transient(
                        e.message ?: "Download failed"
                    )
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun clearTransientError() {
        val current = _uiState.value.errorState
        if (current is ErrorState.Transient) {
            _uiState.value = _uiState.value.copy(errorState = null)
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

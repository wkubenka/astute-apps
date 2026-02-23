package com.william.astuterepo.ui.applist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.william.astuterepo.data.repository.AppRepository
import com.william.astuterepo.domain.AppWithStatus
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
    val selectedApp: AppWithStatus? = null
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val repository: AppRepository,
    private val versionChecker: VersionChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeApps().collect { apps ->
                val appsWithStatus = apps.map { versionChecker.resolveStatus(it) }
                _uiState.value = _uiState.value.copy(apps = appsWithStatus)
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

package com.william.astuterepo.ui.applist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.william.astuterepo.data.model.AppEntry
import com.william.astuterepo.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppListUiState(
    val apps: List<AppEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeApps().collect { apps ->
                _uiState.value = _uiState.value.copy(apps = apps)
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

    companion object {
        private const val TAG = "AppListViewModel"
    }
}

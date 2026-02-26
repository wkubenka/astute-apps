package com.astute.repo.data.repository

import android.util.Log
import com.astute.repo.BuildConfig
import com.astute.repo.data.local.AppDao
import com.astute.repo.data.local.ManifestPreferences
import com.astute.repo.data.model.AppEntry
import com.astute.repo.data.remote.ManifestApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RefreshResult {
    data class Success(val apps: List<AppEntry>) : RefreshResult
    data class Error(val exception: Exception, val hasCachedData: Boolean) : RefreshResult
}

@Singleton
class AppRepository @Inject constructor(
    private val manifestApi: ManifestApi,
    private val appDao: AppDao,
    private val manifestPreferences: ManifestPreferences
) {
    fun observeApps(): Flow<List<AppEntry>> {
        return appDao.getAllApps()
    }

    val lastFetchTimeMillis: Long get() = manifestPreferences.lastFetchTimeMillis

    suspend fun refreshManifest(): RefreshResult {
        Log.d(TAG, "Fetching manifest from: ${BuildConfig.MANIFEST_URL}")

        return try {
            val response = manifestApi.fetchManifest(BuildConfig.MANIFEST_URL)
            val apps = response.apps

            Log.d(TAG, "Manifest fetched successfully. ${apps.size} apps found:")
            apps.forEach { app ->
                Log.d(TAG, "  - ${app.name} (${app.id}) v${app.versionName} (code=${app.versionCode})")
            }

            appDao.deleteAll()
            appDao.insertAll(apps)
            manifestPreferences.lastFetchTimeMillis = System.currentTimeMillis()
            Log.d(TAG, "Manifest cached to local database.")

            RefreshResult.Success(apps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh manifest", e)
            RefreshResult.Error(
                exception = e,
                hasCachedData = manifestPreferences.hasCachedData()
            )
        }
    }

    companion object {
        private const val TAG = "AppRepository"
    }
}

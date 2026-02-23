package com.william.astuterepo.data.repository

import android.util.Log
import com.william.astuterepo.BuildConfig
import com.william.astuterepo.data.local.AppDao
import com.william.astuterepo.data.model.AppEntry
import com.william.astuterepo.data.remote.ManifestApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val manifestApi: ManifestApi,
    private val appDao: AppDao
) {
    fun observeApps(): Flow<List<AppEntry>> {
        return appDao.getAllApps()
    }

    suspend fun refreshManifest(): List<AppEntry> {
        Log.d(TAG, "Fetching manifest from: ${BuildConfig.MANIFEST_URL}")

        val response = manifestApi.fetchManifest(BuildConfig.MANIFEST_URL)
        val apps = response.apps

        Log.d(TAG, "Manifest fetched successfully. ${apps.size} apps found:")
        apps.forEach { app ->
            Log.d(TAG, "  - ${app.name} (${app.id}) v${app.versionName} (code=${app.versionCode})")
        }

        appDao.deleteAll()
        appDao.insertAll(apps)
        Log.d(TAG, "Manifest cached to local database.")

        return apps
    }

    companion object {
        private const val TAG = "AppRepository"
    }
}

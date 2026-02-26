package com.astute.repo.data.repository

import android.util.Log
import com.astute.repo.BuildConfig
import com.astute.repo.data.local.AppDao
import com.astute.repo.data.local.ManifestPreferences
import com.astute.repo.data.model.AppEntry
import com.astute.repo.data.model.GitHubRelease
import com.astute.repo.data.model.ManifestEntry
import com.astute.repo.data.remote.GitHubApi
import com.astute.repo.data.remote.ManifestApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RefreshResult {
    data class Success(val apps: List<AppEntry>) : RefreshResult
    data class Error(val exception: Exception, val hasCachedData: Boolean) : RefreshResult
}

@Singleton
class AppRepository @Inject constructor(
    private val manifestApi: ManifestApi,
    private val gitHubApi: GitHubApi,
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
            val manifestEntries = response.apps

            Log.d(TAG, "Manifest fetched. ${manifestEntries.size} apps found. Fetching releases...")

            val apps = fetchReleasesAndMerge(manifestEntries)

            Log.d(TAG, "Releases resolved. ${apps.size} apps ready:")
            apps.forEach { app ->
                Log.d(TAG, "  - ${app.name} (${app.id}) v${app.versionName ?: "no release"}")
            }

            appDao.deleteAll()
            appDao.insertAll(apps)
            manifestPreferences.lastFetchTimeMillis = System.currentTimeMillis()
            Log.d(TAG, "Manifest + releases cached to local database.")

            RefreshResult.Success(apps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh manifest", e)
            RefreshResult.Error(
                exception = e,
                hasCachedData = manifestPreferences.hasCachedData()
            )
        }
    }

    private suspend fun fetchReleasesAndMerge(
        entries: List<ManifestEntry>
    ): List<AppEntry> = coroutineScope {
        entries.map { entry ->
            async { mergeWithRelease(entry) }
        }.awaitAll()
    }

    private suspend fun mergeWithRelease(entry: ManifestEntry): AppEntry {
        val parts = entry.github.split("/", limit = 2)
        val owner = parts[0]
        val repo = parts[1]
        val release = fetchLatestReleaseSafe(owner, repo)

        val versionName = release?.tagName?.removePrefix("v")
        val apkAsset = release?.assets?.firstOrNull { it.name.endsWith(".apk") }
        val apkUrl = apkAsset?.browserDownloadUrl
        val changelog = release?.body

        return AppEntry(
            id = entry.id,
            name = entry.name,
            description = entry.description,
            iconUrl = entry.iconUrl,
            github = entry.github,
            versionName = versionName,
            apkUrl = apkUrl,
            changelog = changelog,
            minSdk = entry.minSdk
        )
    }

    private suspend fun fetchLatestReleaseSafe(owner: String, repo: String): GitHubRelease? {
        return try {
            gitHubApi.getLatestRelease(owner, repo)
        } catch (e: HttpException) {
            if (e.code() == 404) {
                Log.w(TAG, "No releases found for $owner/$repo")
            } else {
                Log.w(TAG, "GitHub API error for $owner/$repo: ${e.code()}", e)
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch release for $owner/$repo", e)
            null
        }
    }

    companion object {
        private const val TAG = "AppRepository"
    }
}

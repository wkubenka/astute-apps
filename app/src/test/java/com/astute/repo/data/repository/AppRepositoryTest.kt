package com.astute.repo.data.repository

import com.astute.repo.data.local.AppDao
import com.astute.repo.data.local.ManifestPreferences
import com.astute.repo.data.model.AppEntry
import com.astute.repo.data.model.GitHubAsset
import com.astute.repo.data.model.GitHubRelease
import com.astute.repo.data.model.ManifestEntry
import com.astute.repo.data.model.ManifestResponse
import com.astute.repo.data.remote.GitHubApi
import com.astute.repo.data.remote.ManifestApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AppRepositoryTest {

    private lateinit var manifestApi: ManifestApi
    private lateinit var gitHubApi: GitHubApi
    private lateinit var appDao: AppDao
    private lateinit var manifestPreferences: ManifestPreferences
    private lateinit var repository: AppRepository

    private val sampleManifestEntries = listOf(
        ManifestEntry(
            id = "com.test.app1",
            name = "App One",
            description = "First app",
            iconUrl = "https://example.com/icon1.png",
            github = "test/app1"
        ),
        ManifestEntry(
            id = "com.test.app2",
            name = "App Two",
            description = "Second app",
            iconUrl = "https://example.com/icon2.png",
            github = "test/app2"
        )
    )

    private val sampleRelease1 = GitHubRelease(
        tagName = "v1.0.0",
        body = "First release",
        assets = listOf(
            GitHubAsset(
                name = "app1-1.0.0.apk",
                browserDownloadUrl = "https://github.com/test/app1/releases/download/v1.0.0/app1-1.0.0.apk"
            )
        )
    )

    private val sampleRelease2 = GitHubRelease(
        tagName = "v2.0.0",
        body = "Major update",
        assets = listOf(
            GitHubAsset(
                name = "app2-2.0.0.apk",
                browserDownloadUrl = "https://github.com/test/app2/releases/download/v2.0.0/app2-2.0.0.apk"
            )
        )
    )

    @Before
    fun setup() {
        manifestApi = mockk()
        gitHubApi = mockk()
        appDao = mockk(relaxUnitFun = true)
        manifestPreferences = mockk(relaxed = true)
        repository = AppRepository(manifestApi, gitHubApi, appDao, manifestPreferences)
    }

    @Test
    fun `refreshManifest fetches manifest and releases then caches`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(sampleManifestEntries)
        coEvery { gitHubApi.getLatestRelease("test", "app1") } returns sampleRelease1
        coEvery { gitHubApi.getLatestRelease("test", "app2") } returns sampleRelease2

        val result = repository.refreshManifest()

        assertTrue(result is RefreshResult.Success)
        val apps = (result as RefreshResult.Success).apps
        assertEquals(2, apps.size)

        assertEquals("com.test.app1", apps[0].id)
        assertEquals("App One", apps[0].name)
        assertEquals("1.0.0", apps[0].versionName)
        assertEquals("First release", apps[0].changelog)
        assertEquals("https://github.com/test/app1/releases/download/v1.0.0/app1-1.0.0.apk", apps[0].apkUrl)

        assertEquals("com.test.app2", apps[1].id)
        assertEquals("2.0.0", apps[1].versionName)

        coVerifyOrder {
            manifestApi.fetchManifest(any())
            appDao.deleteAll()
            appDao.insertAll(any())
        }
    }

    @Test
    fun `refreshManifest strips v prefix from tag name`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(
            listOf(sampleManifestEntries[0])
        )
        coEvery { gitHubApi.getLatestRelease("test", "app1") } returns sampleRelease1

        val result = repository.refreshManifest()

        val app = (result as RefreshResult.Success).apps[0]
        assertEquals("1.0.0", app.versionName)
    }

    @Test
    fun `refreshManifest handles release with no APK asset`() = runTest {
        val releaseNoApk = GitHubRelease(
            tagName = "v1.0.0",
            body = "Source only",
            assets = listOf(
                GitHubAsset(name = "source.tar.gz", browserDownloadUrl = "https://example.com/source.tar.gz")
            )
        )

        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(
            listOf(sampleManifestEntries[0])
        )
        coEvery { gitHubApi.getLatestRelease("test", "app1") } returns releaseNoApk

        val result = repository.refreshManifest()

        val app = (result as RefreshResult.Success).apps[0]
        assertEquals("1.0.0", app.versionName)
        assertNull(app.apkUrl)
    }

    @Test
    fun `refreshManifest handles 404 from GitHub gracefully`() = runTest {
        val notFoundResponse = Response.error<GitHubRelease>(
            404,
            "Not Found".toResponseBody()
        )

        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(sampleManifestEntries)
        coEvery { gitHubApi.getLatestRelease("test", "app1") } throws HttpException(notFoundResponse)
        coEvery { gitHubApi.getLatestRelease("test", "app2") } returns sampleRelease2

        val result = repository.refreshManifest()

        assertTrue(result is RefreshResult.Success)
        val apps = (result as RefreshResult.Success).apps
        assertEquals(2, apps.size)

        // App1 has no release info
        assertEquals("com.test.app1", apps[0].id)
        assertNull(apps[0].versionName)
        assertNull(apps[0].apkUrl)
        assertNull(apps[0].changelog)

        // App2 still got its release
        assertEquals("com.test.app2", apps[1].id)
        assertEquals("2.0.0", apps[1].versionName)
    }

    @Test
    fun `refreshManifest handles GitHub API error gracefully`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(
            listOf(sampleManifestEntries[0])
        )
        coEvery { gitHubApi.getLatestRelease("test", "app1") } throws IOException("Connection reset")

        val result = repository.refreshManifest()

        assertTrue(result is RefreshResult.Success)
        val app = (result as RefreshResult.Success).apps[0]
        assertEquals("com.test.app1", app.id)
        assertEquals("App One", app.name)
        assertNull(app.versionName)
        assertNull(app.apkUrl)
    }

    @Test
    fun `refreshManifest returns Error on manifest fetch failure`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } throws IOException("Network error")
        every { manifestPreferences.hasCachedData() } returns false

        val result = repository.refreshManifest()

        assertTrue(result is RefreshResult.Error)
        val error = result as RefreshResult.Error
        assertEquals("Network error", error.exception.message)
        assertFalse(error.hasCachedData)
    }

    @Test
    fun `refreshManifest returns Error with hasCachedData true when cache exists`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } throws IOException("No connection")
        every { manifestPreferences.hasCachedData() } returns true

        val result = repository.refreshManifest()

        assertTrue(result is RefreshResult.Error)
        val error = result as RefreshResult.Error
        assertTrue(error.hasCachedData)
    }

    @Test
    fun `refreshManifest does not delete cached data on manifest fetch failure`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } throws IOException("Timeout")

        repository.refreshManifest()

        coVerify(exactly = 0) { appDao.deleteAll() }
        coVerify(exactly = 0) { appDao.insertAll(any()) }
    }

    @Test
    fun `refreshManifest updates lastFetchTime on success`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(emptyList())

        repository.refreshManifest()

        verify { manifestPreferences.lastFetchTimeMillis = any() }
    }

    @Test
    fun `observeApps delegates to DAO`() = runTest {
        val cachedApps = listOf(
            AppEntry(
                id = "com.test.app1",
                name = "App One",
                description = "First app",
                iconUrl = "https://example.com/icon1.png",
                github = "test/app1",
                versionName = "1.0.0",
                apkUrl = "https://example.com/app1.apk"
            )
        )
        every { appDao.getAllApps() } returns flowOf(cachedApps)

        val flow = repository.observeApps()

        flow.collect { apps ->
            assertEquals(1, apps.size)
            assertEquals("com.test.app1", apps[0].id)
        }
    }

    @Test
    fun `refreshManifest handles empty manifest`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(emptyList())

        val result = repository.refreshManifest()

        assertTrue(result is RefreshResult.Success)
        assertEquals(0, (result as RefreshResult.Success).apps.size)
        coVerify {
            appDao.deleteAll()
            appDao.insertAll(emptyList())
        }
    }

    @Test
    fun `refreshManifest preserves manifest static fields in merged AppEntry`() = runTest {
        val entry = ManifestEntry(
            id = "com.test.app",
            name = "My App",
            description = "My description",
            iconUrl = "https://example.com/icon.png",
            github = "owner/repo",
            minSdk = 28
        )
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(listOf(entry))
        coEvery { gitHubApi.getLatestRelease("owner", "repo") } returns sampleRelease1

        val result = repository.refreshManifest()

        val app = (result as RefreshResult.Success).apps[0]
        assertEquals("com.test.app", app.id)
        assertEquals("My App", app.name)
        assertEquals("My description", app.description)
        assertEquals("https://example.com/icon.png", app.iconUrl)
        assertEquals("owner/repo", app.github)
        assertEquals(28, app.minSdk)
    }
}

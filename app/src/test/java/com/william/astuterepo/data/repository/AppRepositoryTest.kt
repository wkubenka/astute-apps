package com.william.astuterepo.data.repository

import com.william.astuterepo.data.local.AppDao
import com.william.astuterepo.data.model.AppEntry
import com.william.astuterepo.data.model.ManifestResponse
import com.william.astuterepo.data.remote.ManifestApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AppRepositoryTest {

    private lateinit var manifestApi: ManifestApi
    private lateinit var appDao: AppDao
    private lateinit var repository: AppRepository

    private val sampleApps = listOf(
        AppEntry(
            id = "com.test.app1",
            name = "App One",
            versionCode = 1,
            versionName = "1.0.0",
            description = "First app",
            apkUrl = "https://example.com/app1.apk",
            iconUrl = "https://example.com/icon1.png"
        ),
        AppEntry(
            id = "com.test.app2",
            name = "App Two",
            versionCode = 2,
            versionName = "2.0.0",
            description = "Second app",
            apkUrl = "https://example.com/app2.apk",
            iconUrl = "https://example.com/icon2.png"
        )
    )

    @Before
    fun setup() {
        manifestApi = mockk()
        appDao = mockk(relaxUnitFun = true)
        repository = AppRepository(manifestApi, appDao)
    }

    @Test
    fun `refreshManifest fetches from API and caches in DAO`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(sampleApps)

        val result = repository.refreshManifest()

        assertEquals(2, result.size)
        assertEquals("com.test.app1", result[0].id)
        assertEquals("com.test.app2", result[1].id)

        coVerifyOrder {
            manifestApi.fetchManifest(any())
            appDao.deleteAll()
            appDao.insertAll(sampleApps)
        }
    }

    @Test
    fun `refreshManifest clears old data before inserting new`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(sampleApps)

        repository.refreshManifest()

        coVerifyOrder {
            appDao.deleteAll()
            appDao.insertAll(sampleApps)
        }
    }

    @Test(expected = IOException::class)
    fun `refreshManifest propagates network errors`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } throws IOException("Network error")

        repository.refreshManifest()
    }

    @Test
    fun `observeApps delegates to DAO`() = runTest {
        every { appDao.getAllApps() } returns flowOf(sampleApps)

        val flow = repository.observeApps()

        flow.collect { apps ->
            assertEquals(2, apps.size)
            assertEquals("com.test.app1", apps[0].id)
        }
    }

    @Test
    fun `refreshManifest returns apps from API response`() = runTest {
        val singleApp = listOf(sampleApps[0])
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(singleApp)

        val result = repository.refreshManifest()

        assertEquals(1, result.size)
        assertEquals("App One", result[0].name)
    }

    @Test
    fun `refreshManifest handles empty manifest`() = runTest {
        coEvery { manifestApi.fetchManifest(any()) } returns ManifestResponse(emptyList())

        val result = repository.refreshManifest()

        assertEquals(0, result.size)
        coVerify {
            appDao.deleteAll()
            appDao.insertAll(emptyList())
        }
    }
}

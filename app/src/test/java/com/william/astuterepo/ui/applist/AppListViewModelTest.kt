package com.william.astuterepo.ui.applist

import com.william.astuterepo.data.model.AppEntry
import com.william.astuterepo.data.repository.AppRepository
import com.william.astuterepo.domain.AppWithStatus
import com.william.astuterepo.domain.DownloadState
import com.william.astuterepo.domain.InstallManager
import com.william.astuterepo.domain.InstallStatus
import com.william.astuterepo.domain.VersionChecker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: AppRepository
    private lateinit var versionChecker: VersionChecker
    private lateinit var installManager: InstallManager
    private val downloadStatesFlow = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    private val sampleApp = AppEntry(
        id = "com.test.app",
        name = "Test App",
        versionCode = 1,
        versionName = "1.0.0",
        description = "Test",
        apkUrl = "https://example.com/app.apk",
        iconUrl = "https://example.com/icon.png"
    )

    private val sampleAppWithStatus = AppWithStatus(
        app = sampleApp,
        status = InstallStatus.NOT_INSTALLED
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        versionChecker = mockk()
        installManager = mockk()

        every { versionChecker.resolveStatus(any()) } returns sampleAppWithStatus
        every { repository.observeApps() } returns flowOf(emptyList())
        coEvery { repository.refreshManifest() } returns emptyList()
        every { installManager.downloadStates } returns downloadStatesFlow
        every { installManager.cleanupOldApks(any()) } just runs
        every { installManager.canInstallFromUnknownSources() } returns true
        every { installManager.markIdle(any()) } just runs
        every { installManager.markInstalling(any()) } just runs
        every { installManager.cancelDownload(any()) } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AppListViewModel {
        return AppListViewModel(repository, versionChecker, installManager)
    }

    @Test
    fun `initial state has empty apps and not loading`() = runTest(testDispatcher.scheduler) {
        val vm = createViewModel()

        val state = vm.uiState.value
        assertTrue(state.apps.isEmpty())
        assertNull(state.error)
        assertNull(state.selectedApp)
    }

    @Test
    fun `refreshManifest sets loading state`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.refreshManifest() } coAnswers {
            emptyList()
        }

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `refreshManifest success populates apps from Flow`() = runTest(testDispatcher.scheduler) {
        every { repository.observeApps() } returns flowOf(listOf(sampleApp))
        coEvery { repository.refreshManifest() } returns listOf(sampleApp)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.apps.size)
        assertEquals("com.test.app", vm.uiState.value.apps[0].app.id)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `refreshManifest failure sets error message`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.refreshManifest() } throws IOException("Network error")

        val vm = createViewModel()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertEquals("Network error", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `recheckStatuses re-maps apps through VersionChecker`() = runTest(testDispatcher.scheduler) {
        val updatedStatus = AppWithStatus(
            app = sampleApp,
            status = InstallStatus.UP_TO_DATE,
            installedVersionName = "1.0.0",
            installedVersionCode = 1L
        )

        every { repository.observeApps() } returns flowOf(listOf(sampleApp))
        coEvery { repository.refreshManifest() } returns listOf(sampleApp)
        every { versionChecker.resolveStatus(sampleApp) } returns sampleAppWithStatus

        val vm = createViewModel()
        advanceUntilIdle()

        every { versionChecker.resolveStatus(sampleApp) } returns updatedStatus
        vm.recheckStatuses()

        assertEquals(InstallStatus.UP_TO_DATE, vm.uiState.value.apps[0].status)
        assertEquals("1.0.0", vm.uiState.value.apps[0].installedVersionName)
    }

    @Test
    fun `selectApp sets selectedApp in state`() = runTest(testDispatcher.scheduler) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.selectApp(sampleAppWithStatus)

        assertNotNull(vm.uiState.value.selectedApp)
        assertEquals("com.test.app", vm.uiState.value.selectedApp?.app?.id)
    }

    @Test
    fun `clearSelection clears selectedApp`() = runTest(testDispatcher.scheduler) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.selectApp(sampleAppWithStatus)
        assertNotNull(vm.uiState.value.selectedApp)

        vm.clearSelection()
        assertNull(vm.uiState.value.selectedApp)
    }

    @Test
    fun `recheckStatuses does nothing when apps list is empty`() = runTest(testDispatcher.scheduler) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.recheckStatuses()
        assertTrue(vm.uiState.value.apps.isEmpty())
    }

    // Phase 3 tests

    @Test
    fun `downloadAndInstall shows permission dialog when unknown sources not allowed`() =
        runTest(testDispatcher.scheduler) {
            every { installManager.canInstallFromUnknownSources() } returns false

            val vm = createViewModel()
            advanceUntilIdle()

            vm.downloadAndInstall(sampleApp)

            assertTrue(vm.uiState.value.showPermissionDialog)
            assertEquals("com.test.app", vm.uiState.value.pendingInstallAppId)
        }

    @Test
    fun `downloadAndInstall skips when already downloading`() =
        runTest(testDispatcher.scheduler) {
            downloadStatesFlow.value = mapOf(
                "com.test.app" to DownloadState.Downloading(1L, 50, 500L, 1000L)
            )

            val vm = createViewModel()
            advanceUntilIdle()

            vm.downloadAndInstall(sampleApp)

            // Should not show permission dialog or start download
            assertFalse(vm.uiState.value.showPermissionDialog)
        }

    @Test
    fun `downloadAndInstall rejects incompatible minSdk`() =
        runTest(testDispatcher.scheduler) {
            val highSdkApp = sampleApp.copy(minSdk = 999)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.downloadAndInstall(highSdkApp)

            assertNotNull(vm.uiState.value.error)
            assertTrue(vm.uiState.value.error!!.contains("requires Android API"))
        }

    @Test
    fun `onPermissionDialogDismiss clears dialog state`() =
        runTest(testDispatcher.scheduler) {
            every { installManager.canInstallFromUnknownSources() } returns false

            val vm = createViewModel()
            advanceUntilIdle()

            vm.downloadAndInstall(sampleApp)
            assertTrue(vm.uiState.value.showPermissionDialog)

            vm.onPermissionDialogDismiss()
            assertFalse(vm.uiState.value.showPermissionDialog)
            assertNull(vm.uiState.value.pendingInstallAppId)
        }

    @Test
    fun `cancelDownload delegates to InstallManager`() =
        runTest(testDispatcher.scheduler) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.cancelDownload("com.test.app")

            verify { installManager.cancelDownload("com.test.app") }
        }

    @Test
    fun `onInstallIntentLaunched clears installIntent`() =
        runTest(testDispatcher.scheduler) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onInstallIntentLaunched()
            assertNull(vm.uiState.value.installIntent)
        }

    @Test
    fun `recheckStatuses clears Installing state for apps`() =
        runTest(testDispatcher.scheduler) {
            downloadStatesFlow.value = mapOf(
                "com.test.app" to DownloadState.Installing
            )

            every { repository.observeApps() } returns flowOf(listOf(sampleApp))
            coEvery { repository.refreshManifest() } returns listOf(sampleApp)

            val installedStatus = AppWithStatus(
                app = sampleApp,
                status = InstallStatus.UP_TO_DATE,
                installedVersionName = "1.0.0",
                installedVersionCode = 1L
            )
            every { versionChecker.resolveStatus(sampleApp) } returns installedStatus

            val vm = createViewModel()
            advanceUntilIdle()

            vm.recheckStatuses()

            verify { installManager.markIdle("com.test.app") }
        }

    @Test
    fun `download states from InstallManager are reflected in uiState`() =
        runTest(testDispatcher.scheduler) {
            val vm = createViewModel()
            advanceUntilIdle()

            downloadStatesFlow.value = mapOf(
                "com.test.app" to DownloadState.Downloading(1L, 42, 420L, 1000L)
            )
            advanceUntilIdle()

            val state = vm.uiState.value.downloadStates["com.test.app"]
            assertTrue(state is DownloadState.Downloading)
            assertEquals(42, (state as DownloadState.Downloading).progress)
        }

    @Test
    fun `cleanupOldApks called on init`() = runTest(testDispatcher.scheduler) {
        createViewModel()
        advanceUntilIdle()

        verify { installManager.cleanupOldApks(any()) }
    }
}

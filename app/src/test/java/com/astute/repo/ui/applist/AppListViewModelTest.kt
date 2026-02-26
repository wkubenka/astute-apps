package com.astute.repo.ui.applist

import com.astute.repo.data.model.AppEntry
import com.astute.repo.data.network.ConnectivityObserver
import com.astute.repo.data.repository.AppRepository
import com.astute.repo.data.repository.RefreshResult
import com.astute.repo.domain.AppWithStatus
import com.astute.repo.domain.DownloadState
import com.astute.repo.domain.InstallManager
import com.astute.repo.domain.InstallStatus
import com.astute.repo.domain.VersionChecker
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

@OptIn(ExperimentalCoroutinesApi::class)
class AppListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: AppRepository
    private lateinit var versionChecker: VersionChecker
    private lateinit var installManager: InstallManager
    private lateinit var connectivityObserver: ConnectivityObserver
    private val downloadStatesFlow = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    private val connectivityFlow = MutableStateFlow(true)

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
        connectivityObserver = mockk()

        every { versionChecker.resolveStatus(any()) } returns sampleAppWithStatus
        every { repository.observeApps() } returns flowOf(emptyList())
        coEvery { repository.refreshManifest() } returns RefreshResult.Success(emptyList())
        every { installManager.downloadStates } returns downloadStatesFlow
        every { installManager.cleanupOldApks(any()) } just runs
        every { installManager.canInstallFromUnknownSources() } returns true
        every { installManager.markIdle(any()) } just runs
        every { installManager.markInstalling(any()) } just runs
        every { installManager.cancelDownload(any()) } just runs
        every { connectivityObserver.isOnline } returns connectivityFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AppListViewModel {
        return AppListViewModel(repository, versionChecker, installManager, connectivityObserver)
    }

    @Test
    fun `initial state has empty apps and not loading`() = runTest(testDispatcher.scheduler) {
        val vm = createViewModel()

        val state = vm.uiState.value
        assertTrue(state.apps.isEmpty())
        assertNull(state.errorState)
        assertNull(state.selectedApp)
    }

    @Test
    fun `refreshManifest sets loading state`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.refreshManifest() } coAnswers {
            RefreshResult.Success(emptyList())
        }

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `refreshManifest success populates apps from Flow`() = runTest(testDispatcher.scheduler) {
        every { repository.observeApps() } returns flowOf(listOf(sampleApp))
        coEvery { repository.refreshManifest() } returns RefreshResult.Success(listOf(sampleApp))

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.apps.size)
        assertEquals("com.test.app", vm.uiState.value.apps[0].app.id)
        assertNull(vm.uiState.value.errorState)
    }

    @Test
    fun `refreshManifest failure without cache sets Transient error`() =
        runTest(testDispatcher.scheduler) {
            coEvery { repository.refreshManifest() } returns RefreshResult.Error(
                exception = Exception("Network error"),
                hasCachedData = false
            )

            val vm = createViewModel()
            advanceUntilIdle()

            val errorState = vm.uiState.value.errorState
            assertTrue(errorState is ErrorState.Transient)
            assertEquals("Network error", (errorState as ErrorState.Transient).message)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `refreshManifest failure with cache sets Offline error`() =
        runTest(testDispatcher.scheduler) {
            every { repository.lastFetchTimeMillis } returns 1000L
            coEvery { repository.refreshManifest() } returns RefreshResult.Error(
                exception = Exception("No connection"),
                hasCachedData = true
            )

            val vm = createViewModel()
            advanceUntilIdle()

            val errorState = vm.uiState.value.errorState
            assertTrue(errorState is ErrorState.Offline)
            assertTrue(vm.uiState.value.isOffline)
        }

    @Test
    fun `recheckStatuses re-maps apps through VersionChecker`() =
        runTest(testDispatcher.scheduler) {
            val updatedStatus = AppWithStatus(
                app = sampleApp,
                status = InstallStatus.UP_TO_DATE,
                installedVersionName = "1.0.0",
                installedVersionCode = 1L
            )

            every { repository.observeApps() } returns flowOf(listOf(sampleApp))
            coEvery { repository.refreshManifest() } returns RefreshResult.Success(
                listOf(sampleApp)
            )
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
    fun `recheckStatuses does nothing when apps list is empty`() =
        runTest(testDispatcher.scheduler) {
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

            val errorState = vm.uiState.value.errorState
            assertNotNull(errorState)
            assertTrue(errorState is ErrorState.Transient)
            assertTrue((errorState as ErrorState.Transient).message.contains("requires Android API"))
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
            coEvery { repository.refreshManifest() } returns RefreshResult.Success(
                listOf(sampleApp)
            )

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

    // Phase 4 tests

    @Test
    fun `onSearchQueryChanged updates search query in state`() =
        runTest(testDispatcher.scheduler) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onSearchQueryChanged("Money")
            assertEquals("Money", vm.uiState.value.searchQuery)

            vm.onSearchQueryChanged("")
            assertEquals("", vm.uiState.value.searchQuery)
        }

    @Test
    fun `clearTransientError clears only Transient errors`() =
        runTest(testDispatcher.scheduler) {
            coEvery { repository.refreshManifest() } returns RefreshResult.Error(
                exception = Exception("Fail"),
                hasCachedData = false
            )

            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.errorState is ErrorState.Transient)

            vm.clearTransientError()
            assertNull(vm.uiState.value.errorState)
        }

    @Test
    fun `clearTransientError does not clear Offline errors`() =
        runTest(testDispatcher.scheduler) {
            every { repository.lastFetchTimeMillis } returns 1000L
            coEvery { repository.refreshManifest() } returns RefreshResult.Error(
                exception = Exception("No network"),
                hasCachedData = true
            )

            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.errorState is ErrorState.Offline)

            vm.clearTransientError()
            // Should still be Offline
            assertTrue(vm.uiState.value.errorState is ErrorState.Offline)
        }

    @Test
    fun `connectivity restored triggers auto-refresh when previously offline`() =
        runTest(testDispatcher.scheduler) {
            // Start offline
            connectivityFlow.value = false
            every { repository.lastFetchTimeMillis } returns 1000L
            coEvery { repository.refreshManifest() } returns RefreshResult.Error(
                exception = Exception("Offline"),
                hasCachedData = true
            )

            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isOffline)

            // Restore connectivity — should trigger refresh
            coEvery { repository.refreshManifest() } returns RefreshResult.Success(emptyList())
            connectivityFlow.value = true
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isOffline)
            assertNull(vm.uiState.value.errorState)
        }

    @Test
    fun `connectivity change to offline sets isOffline flag`() =
        runTest(testDispatcher.scheduler) {
            val vm = createViewModel()
            advanceUntilIdle()

            connectivityFlow.value = false
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isOffline)
        }
}

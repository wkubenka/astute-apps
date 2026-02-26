package com.william.astuterepo.ui.applist

import com.william.astuterepo.data.model.AppEntry
import com.william.astuterepo.data.repository.AppRepository
import com.william.astuterepo.domain.AppWithStatus
import com.william.astuterepo.domain.InstallStatus
import com.william.astuterepo.domain.VersionChecker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

        every { versionChecker.resolveStatus(any()) } returns sampleAppWithStatus
        every { repository.observeApps() } returns flowOf(emptyList())
        coEvery { repository.refreshManifest() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AppListViewModel {
        return AppListViewModel(repository, versionChecker)
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
            // During refresh, loading should be true
            emptyList()
        }

        val vm = createViewModel()
        advanceUntilIdle()

        // After completion, loading should be false
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

        // Now change what VersionChecker returns and recheck
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

        // Should not throw
        vm.recheckStatuses()
        assertTrue(vm.uiState.value.apps.isEmpty())
    }
}

package com.william.astuterepo.domain

import android.app.Application
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallManagerTest {

    private lateinit var application: Application
    private lateinit var installManager: InstallManager

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        installManager = InstallManager(application)
    }

    @Test
    fun `initial download states are empty`() {
        assertTrue(installManager.downloadStates.value.isEmpty())
    }

    @Test
    fun `markInstalling sets Installing state`() {
        installManager.markInstalling("com.test.app")

        val state = installManager.downloadStates.value["com.test.app"]
        assertTrue(state is DownloadState.Installing)
    }

    @Test
    fun `markIdle removes app from download states`() {
        installManager.markInstalling("com.test.app")
        assertTrue(installManager.downloadStates.value.containsKey("com.test.app"))

        installManager.markIdle("com.test.app")
        assertNull(installManager.downloadStates.value["com.test.app"])
    }

    @Test
    fun `markIdle on unknown app is no-op`() {
        installManager.markIdle("com.nonexistent.app")
        assertTrue(installManager.downloadStates.value.isEmpty())
    }

    @Test
    fun `cancelDownload resets state to idle`() {
        installManager.markInstalling("com.test.app")
        installManager.cancelDownload("com.test.app")
        assertNull(installManager.downloadStates.value["com.test.app"])
    }

    @Test
    fun `multiple apps tracked independently`() {
        installManager.markInstalling("com.app.one")
        installManager.markInstalling("com.app.two")

        assertEquals(2, installManager.downloadStates.value.size)
        assertTrue(installManager.downloadStates.value["com.app.one"] is DownloadState.Installing)
        assertTrue(installManager.downloadStates.value["com.app.two"] is DownloadState.Installing)

        installManager.markIdle("com.app.one")
        assertEquals(1, installManager.downloadStates.value.size)
        assertNull(installManager.downloadStates.value["com.app.one"])
        assertTrue(installManager.downloadStates.value["com.app.two"] is DownloadState.Installing)
    }

    @Test
    fun `cleanupOldApks removes old files`() {
        val dir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        dir.mkdirs()
        val oldFile = File(dir, "com.test.app_1.apk")
        oldFile.createNewFile()
        oldFile.setLastModified(System.currentTimeMillis() - 48 * 60 * 60 * 1000L)

        installManager.cleanupOldApks()

        assertFalse(oldFile.exists())
    }

    @Test
    fun `cleanupOldApks preserves recent files`() {
        val dir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        dir.mkdirs()
        val recentFile = File(dir, "com.test.app_1.apk")
        recentFile.createNewFile()

        installManager.cleanupOldApks()

        assertTrue(recentFile.exists())
    }

    @Test
    fun `cleanupOldApks ignores non-apk files`() {
        val dir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        dir.mkdirs()
        val txtFile = File(dir, "notes.txt")
        txtFile.createNewFile()
        txtFile.setLastModified(System.currentTimeMillis() - 48 * 60 * 60 * 1000L)

        installManager.cleanupOldApks()

        assertTrue(txtFile.exists())
    }
}

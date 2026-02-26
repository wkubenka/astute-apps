package com.astute.repo.domain

import android.app.Application
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.astute.repo.data.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VersionCheckerTest {

    private lateinit var application: Application
    private lateinit var versionChecker: VersionChecker

    private val testAppId = "com.test.installed"

    private fun createAppEntry(
        id: String = testAppId,
        versionName: String? = "1.0.0"
    ) = AppEntry(
        id = id,
        name = "Test App",
        description = "Test",
        iconUrl = "https://example.com/icon.png",
        github = "test/app",
        versionName = versionName,
        apkUrl = "https://example.com/app.apk"
    )

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        versionChecker = VersionChecker(application)
    }

    private fun installPackage(packageName: String, versionCode: Long, versionName: String) {
        val packageInfo = PackageInfo().apply {
            this.packageName = packageName
            this.versionName = versionName
            @Suppress("DEPRECATION")
            this.versionCode = versionCode.toInt()
            this.longVersionCode = versionCode
        }
        shadowOf(application.packageManager).installPackage(packageInfo)
    }

    @Test
    fun `getInstallStatus returns NOT_INSTALLED for missing package`() {
        val status = versionChecker.getInstallStatus("com.nonexistent.app", "1.0.0")
        assertEquals(InstallStatus.NOT_INSTALLED, status)
    }

    @Test
    fun `getInstallStatus returns UP_TO_DATE when versions match`() {
        installPackage(testAppId, 5L, "1.0.0")

        val status = versionChecker.getInstallStatus(testAppId, "1.0.0")
        assertEquals(InstallStatus.UP_TO_DATE, status)
    }

    @Test
    fun `getInstallStatus returns UP_TO_DATE when installed is newer`() {
        installPackage(testAppId, 10L, "2.0.0")

        val status = versionChecker.getInstallStatus(testAppId, "1.0.0")
        assertEquals(InstallStatus.UP_TO_DATE, status)
    }

    @Test
    fun `getInstallStatus returns UPDATE_AVAILABLE when installed is older`() {
        installPackage(testAppId, 3L, "0.9.0")

        val status = versionChecker.getInstallStatus(testAppId, "1.0.0")
        assertEquals(InstallStatus.UPDATE_AVAILABLE, status)
    }

    @Test
    fun `getInstallStatus returns NO_RELEASE when manifest version is null`() {
        val status = versionChecker.getInstallStatus(testAppId, null)
        assertEquals(InstallStatus.NO_RELEASE, status)
    }

    @Test
    fun `getInstalledVersionName returns name for installed package`() {
        installPackage(testAppId, 5L, "1.2.3")

        val name = versionChecker.getInstalledVersionName(testAppId)
        assertEquals("1.2.3", name)
    }

    @Test
    fun `getInstalledVersionName returns null for missing package`() {
        val name = versionChecker.getInstalledVersionName("com.nonexistent.app")
        assertNull(name)
    }

    @Test
    fun `getInstalledVersionCode returns code for installed package`() {
        installPackage(testAppId, 42L, "1.0.0")

        val code = versionChecker.getInstalledVersionCode(testAppId)
        assertEquals(42L, code)
    }

    @Test
    fun `getInstalledVersionCode returns null for missing package`() {
        val code = versionChecker.getInstalledVersionCode("com.nonexistent.app")
        assertNull(code)
    }

    @Test
    fun `resolveStatus returns NOT_INSTALLED with null version info`() {
        val app = createAppEntry(id = "com.nonexistent.app")

        val result = versionChecker.resolveStatus(app)

        assertEquals(InstallStatus.NOT_INSTALLED, result.status)
        assertEquals(app, result.app)
        assertNull(result.installedVersionName)
        assertNull(result.installedVersionCode)
    }

    @Test
    fun `resolveStatus returns UP_TO_DATE with version info`() {
        installPackage(testAppId, 5L, "1.0.0")
        val app = createAppEntry(versionName = "1.0.0")

        val result = versionChecker.resolveStatus(app)

        assertEquals(InstallStatus.UP_TO_DATE, result.status)
        assertNotNull(result.installedVersionName)
        assertNotNull(result.installedVersionCode)
        assertEquals("1.0.0", result.installedVersionName)
        assertEquals(5L, result.installedVersionCode)
    }

    @Test
    fun `resolveStatus returns UPDATE_AVAILABLE with version info`() {
        installPackage(testAppId, 3L, "0.9.0")
        val app = createAppEntry(versionName = "1.0.0")

        val result = versionChecker.resolveStatus(app)

        assertEquals(InstallStatus.UPDATE_AVAILABLE, result.status)
        assertEquals("0.9.0", result.installedVersionName)
        assertEquals(3L, result.installedVersionCode)
    }

    @Test
    fun `resolveStatus returns NO_RELEASE when versionName is null`() {
        val app = createAppEntry(versionName = null)

        val result = versionChecker.resolveStatus(app)

        assertEquals(InstallStatus.NO_RELEASE, result.status)
        assertNull(result.installedVersionName)
        assertNull(result.installedVersionCode)
    }

    @Test
    fun `compareVersions returns zero for equal versions`() {
        assertEquals(0, VersionChecker.compareVersions("1.0.0", "1.0.0"))
    }

    @Test
    fun `compareVersions returns positive when first is greater`() {
        assertTrue(VersionChecker.compareVersions("2.0.0", "1.0.0") > 0)
        assertTrue(VersionChecker.compareVersions("1.1.0", "1.0.0") > 0)
        assertTrue(VersionChecker.compareVersions("1.0.1", "1.0.0") > 0)
    }

    @Test
    fun `compareVersions returns negative when first is lesser`() {
        assertTrue(VersionChecker.compareVersions("1.0.0", "2.0.0") < 0)
        assertTrue(VersionChecker.compareVersions("1.0.0", "1.1.0") < 0)
        assertTrue(VersionChecker.compareVersions("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun `compareVersions handles different segment counts`() {
        assertEquals(0, VersionChecker.compareVersions("1.0", "1.0.0"))
        assertTrue(VersionChecker.compareVersions("1.0.1", "1.0") > 0)
        assertTrue(VersionChecker.compareVersions("1.0", "1.0.1") < 0)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}

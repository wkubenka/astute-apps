package com.william.astuterepo.domain

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.william.astuterepo.data.model.AppEntry
import javax.inject.Inject
import javax.inject.Singleton

enum class InstallStatus {
    NOT_INSTALLED,
    UP_TO_DATE,
    UPDATE_AVAILABLE
}

data class AppWithStatus(
    val app: AppEntry,
    val status: InstallStatus,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null
)

@Singleton
class VersionChecker @Inject constructor(
    private val application: Application
) {
    private val packageManager: PackageManager get() = application.packageManager

    fun getInstallStatus(appId: String, manifestVersionCode: Int): InstallStatus {
        return try {
            val info = packageManager.getPackageInfo(appId, 0)
            val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            if (installedCode >= manifestVersionCode) {
                InstallStatus.UP_TO_DATE
            } else {
                InstallStatus.UPDATE_AVAILABLE
            }
        } catch (_: PackageManager.NameNotFoundException) {
            InstallStatus.NOT_INSTALLED
        }
    }

    fun getInstalledVersionName(appId: String): String? {
        return try {
            packageManager.getPackageInfo(appId, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun getInstalledVersionCode(appId: String): Long? {
        return try {
            val info = packageManager.getPackageInfo(appId, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun resolveStatus(app: AppEntry): AppWithStatus {
        val status = getInstallStatus(app.id, app.versionCode)
        return AppWithStatus(
            app = app,
            status = status,
            installedVersionName = if (status != InstallStatus.NOT_INSTALLED) getInstalledVersionName(app.id) else null,
            installedVersionCode = if (status != InstallStatus.NOT_INSTALLED) getInstalledVersionCode(app.id) else null
        )
    }
}

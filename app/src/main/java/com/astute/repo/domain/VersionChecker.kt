package com.astute.repo.domain

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.astute.repo.data.model.AppEntry
import javax.inject.Inject
import javax.inject.Singleton

enum class InstallStatus {
    NOT_INSTALLED,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    NO_RELEASE
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

    fun getInstallStatus(appId: String, manifestVersionName: String?): InstallStatus {
        if (manifestVersionName == null) return InstallStatus.NO_RELEASE

        return try {
            val info = packageManager.getPackageInfo(appId, 0)
            val installedVersionName = info.versionName ?: return InstallStatus.UPDATE_AVAILABLE
            if (compareVersions(installedVersionName, manifestVersionName) >= 0) {
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
        val status = getInstallStatus(app.id, app.versionName)
        return AppWithStatus(
            app = app,
            status = status,
            installedVersionName = if (status != InstallStatus.NOT_INSTALLED &&
                status != InstallStatus.NO_RELEASE) getInstalledVersionName(app.id) else null,
            installedVersionCode = if (status != InstallStatus.NOT_INSTALLED &&
                status != InstallStatus.NO_RELEASE) getInstalledVersionCode(app.id) else null
        )
    }

    companion object {
        fun compareVersions(v1: String, v2: String): Int {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }
    }
}

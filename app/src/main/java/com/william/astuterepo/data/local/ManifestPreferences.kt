package com.william.astuterepo.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManifestPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("manifest_prefs", Context.MODE_PRIVATE)

    var lastFetchTimeMillis: Long
        get() = prefs.getLong(KEY_LAST_FETCH, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_FETCH, value).apply()

    fun hasCachedData(): Boolean = lastFetchTimeMillis > 0L

    companion object {
        private const val KEY_LAST_FETCH = "last_fetch_time"
    }
}

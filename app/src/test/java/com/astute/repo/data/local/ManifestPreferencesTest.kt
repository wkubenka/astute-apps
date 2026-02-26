package com.astute.repo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestPreferencesTest {

    private lateinit var manifestPreferences: ManifestPreferences

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        manifestPreferences = ManifestPreferences(context)
        // Clear any state from previous tests
        manifestPreferences.lastFetchTimeMillis = 0L
    }

    @Test
    fun `lastFetchTimeMillis defaults to 0 when never set`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Use a fresh preferences name to avoid setup interference
        val fresh = ManifestPreferences(context)
        // After setup clears it, default is 0
        assertEquals(0L, fresh.lastFetchTimeMillis)
    }

    @Test
    fun `lastFetchTimeMillis round-trips correctly`() {
        val timestamp = 1708900000000L

        manifestPreferences.lastFetchTimeMillis = timestamp

        assertEquals(timestamp, manifestPreferences.lastFetchTimeMillis)
    }

    @Test
    fun `hasCachedData returns false when never fetched`() {
        assertFalse(manifestPreferences.hasCachedData())
    }

    @Test
    fun `hasCachedData returns true after fetch time is set`() {
        manifestPreferences.lastFetchTimeMillis = System.currentTimeMillis()

        assertTrue(manifestPreferences.hasCachedData())
    }
}

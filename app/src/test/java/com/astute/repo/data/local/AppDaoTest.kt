package com.astute.repo.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.astute.repo.data.model.AppEntry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: AppDao

    private fun createApp(
        id: String,
        name: String,
        versionName: String? = "1.0.0"
    ) = AppEntry(
        id = id,
        name = name,
        description = "Description for $name",
        iconUrl = "https://example.com/$id.png",
        github = "test/$id",
        versionName = versionName,
        apkUrl = "https://example.com/$id.apk"
    )

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.appDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertAll and getAllApps returns ordered by name`() = runTest {
        val apps = listOf(
            createApp("com.test.zebra", "Zebra App"),
            createApp("com.test.alpha", "Alpha App"),
            createApp("com.test.middle", "Middle App")
        )
        dao.insertAll(apps)

        dao.getAllApps().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertEquals("Alpha App", result[0].name)
            assertEquals("Middle App", result[1].name)
            assertEquals("Zebra App", result[2].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll clears all entries`() = runTest {
        dao.insertAll(listOf(
            createApp("com.test.one", "One"),
            createApp("com.test.two", "Two")
        ))
        dao.deleteAll()

        dao.getAllApps().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll with conflict replaces existing entry`() = runTest {
        val original = createApp("com.test.app", "Original Name", versionName = "1.0.0")
        dao.insertAll(listOf(original))

        val updated = createApp("com.test.app", "Updated Name", versionName = "2.0.0")
        dao.insertAll(listOf(updated))

        dao.getAllApps().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Updated Name", result[0].name)
            assertEquals("2.0.0", result[0].versionName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllApps emits empty list when database is empty`() = runTest {
        dao.getAllApps().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll preserves all fields`() = runTest {
        val app = AppEntry(
            id = "com.test.full",
            name = "Full App",
            description = "Full description",
            iconUrl = "https://example.com/full.png",
            github = "test/full",
            versionName = "2.1.0",
            apkUrl = "https://example.com/full.apk",
            changelog = "Fixed bugs",
            minSdk = 28
        )
        dao.insertAll(listOf(app))

        dao.getAllApps().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            val stored = result[0]
            assertEquals("com.test.full", stored.id)
            assertEquals("Full App", stored.name)
            assertEquals("Full description", stored.description)
            assertEquals("https://example.com/full.png", stored.iconUrl)
            assertEquals("test/full", stored.github)
            assertEquals("2.1.0", stored.versionName)
            assertEquals("https://example.com/full.apk", stored.apkUrl)
            assertEquals("Fixed bugs", stored.changelog)
            assertEquals(28, stored.minSdk)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

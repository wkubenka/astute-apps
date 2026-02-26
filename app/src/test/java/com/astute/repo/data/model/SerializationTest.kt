package com.astute.repo.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val sampleManifestJson = """
        {
          "apps": [
            {
              "id": "com.william.moneymanager",
              "name": "Money Manager",
              "description": "Personal finance tracker.",
              "icon_url": "https://example.com/money-manager.png",
              "github": "william/money-manager",
              "min_sdk": 26
            },
            {
              "id": "com.william.habitsync",
              "name": "HabitSync",
              "description": "Habit tracker.",
              "icon_url": "https://example.com/habitsync.png",
              "github": "william/habitsync"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parse manifest with multiple apps`() {
        val response = json.decodeFromString<ManifestResponse>(sampleManifestJson)

        assertEquals(2, response.apps.size)
    }

    @Test
    fun `parse manifest entry with all fields`() {
        val response = json.decodeFromString<ManifestResponse>(sampleManifestJson)
        val entry = response.apps[0]

        assertEquals("com.william.moneymanager", entry.id)
        assertEquals("Money Manager", entry.name)
        assertEquals("Personal finance tracker.", entry.description)
        assertEquals("https://example.com/money-manager.png", entry.iconUrl)
        assertEquals("william/money-manager", entry.github)
        assertEquals(26, entry.minSdk)
    }

    @Test
    fun `parse manifest entry with missing optional fields`() {
        val response = json.decodeFromString<ManifestResponse>(sampleManifestJson)
        val entry = response.apps[1]

        assertEquals("com.william.habitsync", entry.id)
        assertNull(entry.minSdk)
    }

    @Test
    fun `ignore unknown keys in manifest JSON`() {
        val jsonWithExtra = """
            {
              "apps": [{
                "id": "com.test.app",
                "name": "Test",
                "description": "Desc",
                "icon_url": "https://example.com/icon.png",
                "github": "test/app",
                "unknown_field": "should be ignored",
                "another_extra": 42
              }]
            }
        """.trimIndent()

        val response = json.decodeFromString<ManifestResponse>(jsonWithExtra)
        assertEquals(1, response.apps.size)
        assertEquals("com.test.app", response.apps[0].id)
    }

    @Test
    fun `empty apps list parses correctly`() {
        val emptyJson = """{"apps": []}"""

        val response = json.decodeFromString<ManifestResponse>(emptyJson)
        assertEquals(0, response.apps.size)
    }

    @Test
    fun `parse GitHub release with all fields`() {
        val releaseJson = """
            {
              "tag_name": "v1.2.0",
              "body": "Added CSV export.",
              "assets": [
                {
                  "name": "money-manager-1.2.0.apk",
                  "browser_download_url": "https://github.com/william/money-manager/releases/download/v1.2.0/money-manager-1.2.0.apk"
                }
              ]
            }
        """.trimIndent()

        val release = json.decodeFromString<GitHubRelease>(releaseJson)

        assertEquals("v1.2.0", release.tagName)
        assertEquals("Added CSV export.", release.body)
        assertEquals(1, release.assets.size)
        assertEquals("money-manager-1.2.0.apk", release.assets[0].name)
        assertEquals(
            "https://github.com/william/money-manager/releases/download/v1.2.0/money-manager-1.2.0.apk",
            release.assets[0].browserDownloadUrl
        )
    }

    @Test
    fun `parse GitHub release with no assets`() {
        val releaseJson = """
            {
              "tag_name": "v0.1.0",
              "body": "Initial release",
              "assets": []
            }
        """.trimIndent()

        val release = json.decodeFromString<GitHubRelease>(releaseJson)

        assertEquals("v0.1.0", release.tagName)
        assertTrue(release.assets.isEmpty())
    }

    @Test
    fun `parse GitHub release with null body`() {
        val releaseJson = """
            {
              "tag_name": "v1.0.0",
              "body": null,
              "assets": []
            }
        """.trimIndent()

        val release = json.decodeFromString<GitHubRelease>(releaseJson)

        assertNull(release.body)
    }

    @Test
    fun `parse GitHub release ignores unknown keys`() {
        val releaseJson = """
            {
              "tag_name": "v1.0.0",
              "body": "Notes",
              "assets": [],
              "id": 12345,
              "node_id": "abc123",
              "html_url": "https://github.com/...",
              "draft": false,
              "prerelease": false
            }
        """.trimIndent()

        val release = json.decodeFromString<GitHubRelease>(releaseJson)
        assertEquals("v1.0.0", release.tagName)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}

package com.william.astuterepo.data.model

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
              "version_code": 3,
              "version_name": "1.2.0",
              "description": "Personal finance tracker.",
              "apk_url": "https://example.com/money-manager.apk",
              "icon_url": "https://example.com/money-manager.png",
              "changelog": "Added CSV export.",
              "min_sdk": 26
            },
            {
              "id": "com.william.habitsync",
              "name": "HabitSync",
              "version_code": 1,
              "version_name": "0.1.0",
              "description": "Habit tracker.",
              "apk_url": "https://example.com/habitsync.apk",
              "icon_url": "https://example.com/habitsync.png",
              "changelog": null,
              "min_sdk": 26
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
    fun `parse app entry with all fields`() {
        val response = json.decodeFromString<ManifestResponse>(sampleManifestJson)
        val app = response.apps[0]

        assertEquals("com.william.moneymanager", app.id)
        assertEquals("Money Manager", app.name)
        assertEquals(3, app.versionCode)
        assertEquals("1.2.0", app.versionName)
        assertEquals("Personal finance tracker.", app.description)
        assertEquals("https://example.com/money-manager.apk", app.apkUrl)
        assertEquals("https://example.com/money-manager.png", app.iconUrl)
        assertEquals("Added CSV export.", app.changelog)
        assertEquals(26, app.minSdk)
    }

    @Test
    fun `parse app entry with null changelog`() {
        val response = json.decodeFromString<ManifestResponse>(sampleManifestJson)
        val app = response.apps[1]

        assertEquals("com.william.habitsync", app.id)
        assertNull(app.changelog)
    }

    @Test
    fun `parse app entry with missing optional fields`() {
        val jsonWithMissing = """
            {
              "apps": [{
                "id": "com.test.app",
                "name": "Test",
                "version_code": 1,
                "version_name": "1.0",
                "description": "Desc",
                "apk_url": "https://example.com/app.apk",
                "icon_url": "https://example.com/icon.png"
              }]
            }
        """.trimIndent()

        val response = json.decodeFromString<ManifestResponse>(jsonWithMissing)
        val app = response.apps[0]

        assertNull(app.changelog)
        assertNull(app.minSdk)
    }

    @Test
    fun `ignore unknown keys in JSON`() {
        val jsonWithExtra = """
            {
              "apps": [{
                "id": "com.test.app",
                "name": "Test",
                "version_code": 1,
                "version_name": "1.0",
                "description": "Desc",
                "apk_url": "https://example.com/app.apk",
                "icon_url": "https://example.com/icon.png",
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
}

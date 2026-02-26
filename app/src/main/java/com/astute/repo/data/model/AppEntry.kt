package com.astute.repo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppEntry(
    @PrimaryKey
    val id: String,

    val name: String,

    val description: String,

    val iconUrl: String,

    val github: String,

    val versionName: String? = null,

    val apkUrl: String? = null,

    val changelog: String? = null,

    val minSdk: Int? = null
)

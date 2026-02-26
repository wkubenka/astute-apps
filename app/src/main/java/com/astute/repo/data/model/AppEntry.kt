package com.astute.repo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "apps")
data class AppEntry(
    @PrimaryKey
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("version_code")
    val versionCode: Int,

    @SerialName("version_name")
    val versionName: String,

    @SerialName("description")
    val description: String,

    @SerialName("apk_url")
    val apkUrl: String,

    @SerialName("icon_url")
    val iconUrl: String,

    @SerialName("changelog")
    val changelog: String? = null,

    @SerialName("min_sdk")
    val minSdk: Int? = null
)

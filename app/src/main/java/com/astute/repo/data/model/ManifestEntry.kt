package com.astute.repo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ManifestEntry(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("description")
    val description: String,

    @SerialName("icon_url")
    val iconUrl: String,

    @SerialName("github")
    val github: String,

    @SerialName("min_sdk")
    val minSdk: Int? = null
)

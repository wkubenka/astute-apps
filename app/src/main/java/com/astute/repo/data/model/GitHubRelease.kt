package com.astute.repo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String,

    @SerialName("body")
    val body: String? = null,

    @SerialName("assets")
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    @SerialName("name")
    val name: String,

    @SerialName("browser_download_url")
    val browserDownloadUrl: String
)

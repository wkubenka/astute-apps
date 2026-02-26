package com.astute.repo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ManifestResponse(
    @SerialName("apps")
    val apps: List<AppEntry>
)

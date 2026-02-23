package com.william.astuterepo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ManifestResponse(
    @SerialName("apps")
    val apps: List<AppEntry>
)

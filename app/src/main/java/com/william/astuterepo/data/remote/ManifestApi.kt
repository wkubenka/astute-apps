package com.william.astuterepo.data.remote

import com.william.astuterepo.data.model.ManifestResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface ManifestApi {

    @GET
    suspend fun fetchManifest(@Url url: String): ManifestResponse
}

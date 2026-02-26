package com.astute.repo.data.remote

import com.astute.repo.data.model.ManifestResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface ManifestApi {

    @GET
    suspend fun fetchManifest(@Url url: String): ManifestResponse
}

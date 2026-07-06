package com.example.gifkeyboard.media

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Matches Tenor API v2's response shape. If you swap to GIPHY instead, only this
 * interface + the mapping in MediaRepository.toMediaItems() need to change —
 * everything above (UI, IME, send pipeline) is provider-agnostic.
 */
interface MediaProviderApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("client_key") clientKey: String = "gifboard",
        @Query("limit") limit: Int = 30,
        @Query("media_filter") mediaFilter: String = "mp4,webm,tinygif"
    ): TenorSearchResponse

    @GET("featured")
    suspend fun featured(
        @Query("key") apiKey: String,
        @Query("client_key") clientKey: String = "gifboard",
        @Query("limit") limit: Int = 30,
        @Query("media_filter") mediaFilter: String = "mp4,webm,tinygif"
    ): TenorSearchResponse
}

// ---- Raw response DTOs (Tenor v2 shape) ----

data class TenorSearchResponse(
    val results: List<TenorResult> = emptyList(),
    val next: String? = null
)

data class TenorResult(
    val id: String,
    val title: String? = null,
    val media_formats: Map<String, TenorMediaFormat> = emptyMap(),
    val has_audio: Boolean = false
)

data class TenorMediaFormat(
    val url: String,
    val dims: List<Int> = listOf(0, 0),
    val duration: Double = 0.0,
    val size: Long = 0
)

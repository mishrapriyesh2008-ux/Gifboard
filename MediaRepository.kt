package com.example.gifkeyboard.media

import com.example.gifkeyboard.BuildConfig
import com.example.gifkeyboard.data.MediaCategory
import com.example.gifkeyboard.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for fetching clips. If MEDIA_PROVIDER_API_KEY is blank
 * (the default in this scaffold, since no key was provided), every call
 * transparently serves SampleClips instead of hitting the network — so the app
 * is fully runnable and demoable out of the box. Drop a real Tenor/GIPHY key
 * into BuildConfig.MEDIA_PROVIDER_API_KEY (via build.gradle.kts or local.properties)
 * to switch to live results with zero other code changes.
 */
class MediaRepository {

    private val hasRealKey: Boolean
        get() = BuildConfig.MEDIA_PROVIDER_API_KEY.isNotBlank()

    private val api: MediaProviderApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.MEDIA_PROVIDER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MediaProviderApi::class.java)
    }

    val categories: List<MediaCategory> = listOf(
        MediaCategory("trending", "Trending", "trending"),
        MediaCategory("reactions", "Reactions", "reaction"),
        MediaCategory("funny", "Funny", "funny"),
        MediaCategory("love", "Love", "love"),
        MediaCategory("celebrate", "Celebrate", "celebration")
    )

    suspend fun search(query: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        if (!hasRealKey) {
            return@withContext Result.success(SampleClips.filterByQuery(query))
        }
        runCatching {
            val response = api.search(query = query, apiKey = BuildConfig.MEDIA_PROVIDER_API_KEY)
            response.results.mapNotNull { it.toMediaItem() }
        }.recoverCatching {
            // Network/provider failure: degrade gracefully instead of showing a dead screen
            SampleClips.filterByQuery(query)
        }
    }

    suspend fun featured(): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        if (!hasRealKey) {
            return@withContext Result.success(SampleClips.all())
        }
        runCatching {
            val response = api.featured(apiKey = BuildConfig.MEDIA_PROVIDER_API_KEY)
            response.results.mapNotNull { it.toMediaItem() }
        }.recoverCatching {
            SampleClips.all()
        }
    }

    private fun TenorResult.toMediaItem(): MediaItem? {
        val video = media_formats["mp4"] ?: media_formats["webm"] ?: return null
        val preview = media_formats["tinygif"] ?: media_formats["gif"] ?: video
        return MediaItem(
            id = id,
            title = title.orEmpty(),
            previewUrl = preview.url,
            videoUrl = video.url,
            width = video.dims.getOrElse(0) { 0 },
            height = video.dims.getOrElse(1) { 0 },
            hasAudio = has_audio
        )
    }
}

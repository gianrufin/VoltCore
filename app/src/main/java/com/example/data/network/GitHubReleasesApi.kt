package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    @Json(name = "name") val name: String,
    @Json(name = "size") val sizeBytes: Long,
    @Json(name = "browser_download_url") val browserDownloadUrl: String,
    @Json(name = "content_type") val contentType: String?
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "name") val name: String?,
    @Json(name = "body") val body: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "published_at") val publishedAt: String?,
    @Json(name = "assets") val assets: List<GitHubAssetDto> = emptyList()
)

interface GitHubReleasesService {
    @Headers("Accept: application/vnd.github+json", "User-Agent: VoltCore-Android")
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubReleaseDto
}

object GitHubReleasesClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttp = OkHttpClient.Builder().build()

    val service: GitHubReleasesService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubReleasesService::class.java)
    }

    /**
     * Parses `owner/repo` or full `https://github.com/owner/repo` string.
     */
    fun parseOwnerAndRepo(input: String): Pair<String, String>? {
        val cleaned = input.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        val parts = cleaned.split("/").filter { it.isNotBlank() }
        if (parts.size < 2) return null
        return parts[0] to parts[1]
    }

    /**
     * Compares semantic version tags like "v1.2.0" vs "1.0".
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val remoteParts = remoteTag.trimStart('v', 'V')
            .substringBefore('-')
            .split(".")
            .map { it.toIntOrNull() ?: 0 }
        val currentParts = currentVersion.trimStart('v', 'V')
            .substringBefore('-')
            .split(".")
            .map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

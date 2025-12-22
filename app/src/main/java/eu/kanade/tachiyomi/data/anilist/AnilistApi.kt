package eu.kanade.tachiyomi.data.anilist

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import yokai.domain.ui.UiPreferences

/**
 * AniList API client for fetching manga data using GraphQL
 * 
 * API Documentation: https://anilist.gitbook.io/anilist-apiv2-docs/
 */
class AnilistApi {
    private val network: NetworkHelper by injectLazy()
    private val client get() = network.client
    private val json = Json { ignoreUnknownKeys = true }
    private val uiPreferences: UiPreferences by injectLazy()
    
    companion object {
        private const val BASE_URL = "https://graphql.anilist.co"
        
        // Rate Limiting State
        private val rateLimitReset = AtomicLong(0)
        private val rateLimitRemaining = AtomicLong(90) // Default conservative start
        private val mutex = Mutex() // To prevent concurrent race conditions on heavy bursts
    }

    suspend fun getTopManga(page: Int = 1, limit: Int = 20): List<AnilistManga> {
        val variables = buildJsonObject {
            addCommonVariables(page, limit)
            put("sort", "SCORE_DESC")
        }
        return executeQuery(baseQuery, variables)
    }

    suspend fun getPopularManga(page: Int = 1, limit: Int = 20): List<AnilistManga> {
        val variables = buildJsonObject {
            addCommonVariables(page, limit)
            put("sort", "POPULARITY_DESC")
        }
        return executeQuery(baseQuery, variables)
    }
    
    suspend fun getTrendingManga(page: Int = 1, limit: Int = 20): List<AnilistManga> {
        val variables = buildJsonObject {
            addCommonVariables(page, limit)
            put("sort", "TRENDING_DESC")
        }
        return executeQuery(baseQuery, variables)
    }

    suspend fun getPublishingManga(page: Int = 1, limit: Int = 20): List<AnilistManga> {
        val variables = buildJsonObject {
            addCommonVariables(page, limit)
            put("sort", "POPULARITY_DESC")
            put("status", "RELEASING")
        }
        return executeQuery(baseQuery, variables)
    }

    suspend fun searchManga(query: String, page: Int = 1, limit: Int = 20): List<AnilistManga> {
        val variables = buildJsonObject {
            put("page", page)
            put("perPage", limit)
            put("search", query)
            put("type", "MANGA")
            put("isAdult", false)
        }
        return executeQuery(baseQuery, variables)
    }
    
    private fun JsonObjectBuilder.addCommonVariables(page: Int, limit: Int) {
        val allowedTypes = uiPreferences.homeContent().get()
        
        // Map preferences to AniList filters
        var country: String? = null
        var format: String? = null
        
        if (allowedTypes.size == 1) {
            when {
                allowedTypes.contains("Manga") -> country = "JP"
                allowedTypes.contains("Manhwa") -> country = "KR"
                allowedTypes.contains("Manhua") -> country = "CN"
                allowedTypes.contains("Novel") -> format = "NOVEL"
            }
        }
        
        put("page", page)
        put("perPage", limit)
        put("type", "MANGA")
        put("isAdult", false)
        if (country != null) put("countryOfOrigin", country)
        if (format != null) put("format", format)
    }

    private suspend fun executeQuery(query: String, variables: JsonObject): List<AnilistManga> {
        // Rate Limit Handling
        waitRateLimit()
        
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        
        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        
        val request = Request.Builder()
            .url(BASE_URL)
            .post(requestBody)
            .build()
            
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                // Update Rate Limits
                val remaining = response.header("X-RateLimit-Remaining")?.toLongOrNull()
                val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()
                
                if (remaining != null) rateLimitRemaining.set(remaining)
                if (reset != null) rateLimitReset.set(reset * 1000) // Convert to ms
                
                if (!response.isSuccessful) {
                    if (response.code == 429) {
                         // Hit limit, throw exception to trigger retry or error logic
                         val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 60
                         rateLimitReset.set(System.currentTimeMillis() + (retryAfter * 1000))
                         rateLimitRemaining.set(0)
                         throw Exception("AniList Rate Limit Hit. Please wait.")
                    }
                    throw Exception("AniList API error: ${response.code}")
                }
                
                val body = response.body?.string() ?: throw Exception("Empty response")
                val apiResponse = json.decodeFromString<AnilistResponse<AnilistPageData>>(body)
                apiResponse.data.page.media
            }
        }
    }
    
    private suspend fun waitRateLimit() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (rateLimitRemaining.get() < 5 && now < rateLimitReset.get()) {
                val waitTime = rateLimitReset.get() - now + 1000
                kotlinx.coroutines.delay(waitTime)
            }
        }
    }

    private val baseQuery = """
        query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}search: String, ${'$'}sort: [MediaSort], ${'$'}status: MediaStatus, ${'$'}countryOfOrigin: CountryCode, ${'$'}format: MediaFormat, ${'$'}type: MediaType, ${'$'}isAdult: Boolean) {
          Page (page: ${'$'}page, perPage: ${'$'}perPage) {
            pageInfo {
              total
              perPage
              currentPage
              lastPage
              hasNextPage
            }
            media (search: ${'$'}search, sort: ${'$'}sort, status: ${'$'}status, countryOfOrigin: ${'$'}countryOfOrigin, format: ${'$'}format, type: ${'$'}type, isAdult: ${'$'}isAdult) {
              id
              title {
                romaji
                english
                native
                userPreferred
              }
              coverImage {
                extraLarge
                large
                medium
                color
              }
              description
              status
              format
              countryOfOrigin
              averageScore
              popularity
              favourites
              genres
            }
          }
        }
    """.trimIndent()
}

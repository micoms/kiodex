package eu.kanade.tachiyomi.data.jikan

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import okhttp3.Request
import uy.kohesive.injekt.injectLazy
import yokai.domain.ui.UiPreferences

/**
 * Jikan API client for fetching manga data from MyAnimeList
 * 
 * API Documentation: https://docs.api.jikan.moe/
 */
class JikanApi {
    private val network: NetworkHelper by injectLazy()
    private val client get() = network.client
    private val json = Json { ignoreUnknownKeys = true }
    private val uiPreferences: UiPreferences by injectLazy()
    
    companion object {
        private const val BASE_URL = "https://api.jikan.moe/v4"
    }

    /**
     * Get top manga by ranking
     */
    suspend fun getTopManga(page: Int = 1, limit: Int = 25): JikanResponse<List<JikanManga>> {
        val url = "$BASE_URL/top/manga?page=$page&limit=$limit"
        return fetchList(url).filterTypes()
    }

    /**
     * Get popular manga (by members count)
     */
    suspend fun getPopularManga(page: Int = 1, limit: Int = 25): JikanResponse<List<JikanManga>> {
        val url = "$BASE_URL/manga?order_by=members&sort=desc&page=$page&limit=$limit"
        return fetchList(url).filterTypes()
    }

    /**
     * Get manga currently publishing
     */
    suspend fun getPublishingManga(page: Int = 1, limit: Int = 25): JikanResponse<List<JikanManga>> {
        val url = "$BASE_URL/manga?status=publishing&order_by=score&sort=desc&page=$page&limit=$limit"
        return fetchList(url).filterTypes()
    }

    /**
     * Get recommended manga (Recent Recommendations)
     */
    suspend fun getRecommendations(page: Int = 1, limit: Int = 25): JikanResponse<List<JikanManga>> {
        val url = "$BASE_URL/recommendations/manga?page=$page"
        val responseJson = executeRequest(url)
        val response = json.decodeFromString<JikanResponse<List<JikanRecommendation>>>(responseJson)
        
        val allowedTypes = uiPreferences.homeContent().get()
        val mangas = response.data.flatMap { it.entry }
            .distinctBy { it.malId }
            .filter { checkType(it.type, allowedTypes) }
            .take(limit)
            
        return JikanResponse(data = mangas, pagination = response.pagination)
    }

    /**
     * Search manga by title
     */
    suspend fun searchManga(query: String, page: Int = 1, limit: Int = 25): JikanResponse<List<JikanManga>> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/manga?q=$encodedQuery&page=$page&limit=$limit"
        return fetchList(url).filterTypes()
    }

    /**
     * Search manga by genre IDs
     * Genre IDs can be found at: https://api.jikan.moe/v4/genres/manga
     * 
     * @param genreIds List of genre IDs to search for (uses AND logic)
     * @param page Page number for pagination
     * @param limit Number of results per page
     */
    suspend fun getMangaByGenres(
        genreIds: List<Int>,
        page: Int = 1,
        limit: Int = 25
    ): JikanResponse<List<JikanManga>> {
        if (genreIds.isEmpty()) {
            return JikanResponse(data = emptyList(), pagination = null)
        }
        val genresParam = genreIds.joinToString(",")
        val url = "$BASE_URL/manga?genres=$genresParam&order_by=score&sort=desc&page=$page&limit=$limit"
        return fetchList(url).filterTypes()
    }

    private fun JikanResponse<List<JikanManga>>.filterTypes(): JikanResponse<List<JikanManga>> {
        val allowedTypes = uiPreferences.homeContent().get()
        val filtered = data.filter { checkType(it.type, allowedTypes) }
        return copy(data = filtered)
    }

    private fun checkType(type: String?, allowedTypes: Set<String>): Boolean {
        if (type == null) return false
        if (allowedTypes.contains(type)) return true
        if (type == "Light Novel" && allowedTypes.contains("Novel")) return true
        return false
    }

    /**
     * Get manga by ID
     */
    suspend fun getMangaById(malId: Long): JikanManga? {
        val url = "$BASE_URL/manga/$malId"
        return try {
            val response = executeRequest(url)
            json.decodeFromString<JikanResponse<JikanManga>>(response).data
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get manga recommendations
     */
    suspend fun getMangaRecommendations(malId: Long): List<JikanManga> {
        val url = "$BASE_URL/manga/$malId/recommendations"
        return try {
            val response = executeRequest(url)
            // Recommendations have a different structure, simplified for now
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchList(url: String): JikanResponse<List<JikanManga>> {
        val response = executeRequest(url)
        return json.decodeFromString(response)
    }

    private suspend fun executeRequest(url: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Jikan API error: ${response.code}")
                }
                response.body?.string() ?: throw Exception("Empty response")
            }
        }
    }
}

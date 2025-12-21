package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.jikan.JikanApi
import eu.kanade.tachiyomi.data.jikan.JikanManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetLibraryManga
import java.util.concurrent.TimeUnit

/**
 * Recommendation engine that analyzes user's library to provide personalized manga recommendations.
 * Uses a local algorithm approach - no external AI service required.
 * 
 * Algorithm:
 * 1. Extract genres from user's library manga
 * 2. Weight genres by reading frequency and recency
 * 3. Query Jikan API for manga matching top genres
 * 4. Filter out manga already in library
 * 5. Cache results to reduce API calls
 */
class RecommendationEngine(
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val jikanApi: JikanApi = JikanApi(),
    private val preferences: PreferencesHelper = Injekt.get(),
) {
    
    // Cache for recommendations
    private var cachedRecommendations: List<JikanManga> = emptyList()
    private var cacheTimestamp: Long = 0
    private val cacheValidityMs = TimeUnit.HOURS.toMillis(1)
    
    // Genre ID mapping for Jikan API (common genres)
    // Full list: https://api.jikan.moe/v4/genres/manga
    private val genreNameToId = mapOf(
        "action" to 1,
        "adventure" to 2,
        "avant garde" to 5,
        "award winning" to 46,
        "boys love" to 28,
        "comedy" to 4,
        "drama" to 8,
        "fantasy" to 10,
        "girls love" to 26,
        "gourmet" to 47,
        "horror" to 14,
        "mystery" to 7,
        "romance" to 22,
        "sci-fi" to 24,
        "slice of life" to 36,
        "sports" to 30,
        "supernatural" to 37,
        "suspense" to 45,
        "ecchi" to 9,
        "erotica" to 49,
        "hentai" to 12,
        // Themes
        "gore" to 58,
        "historical" to 13,
        "isekai" to 62,
        "martial arts" to 17,
        "mecha" to 18,
        "military" to 38,
        "music" to 19,
        "parody" to 20,
        "psychological" to 40,
        "school" to 23,
        "super power" to 31,
        "vampire" to 32,
        // Demographics
        "josei" to 43,
        "kids" to 15,
        "seinen" to 42,
        "shoujo" to 25,
        "shounen" to 27,
    )
    
    /**
     * Get personalized recommendations based on user's library.
     * Returns cached results if still valid, otherwise fetches new recommendations.
     */
    suspend fun getPersonalizedRecommendations(limit: Int = 15): List<JikanManga> = withContext(Dispatchers.IO) {
        // Return cached results if still valid
        if (cachedRecommendations.isNotEmpty() && 
            System.currentTimeMillis() - cacheTimestamp < cacheValidityMs) {
            return@withContext cachedRecommendations.take(limit)
        }
        
        try {
            val library = getLibraryManga.await()
            
            // If library is empty, return empty list (will fallback to generic recommendations)
            if (library.isEmpty()) {
                return@withContext emptyList()
            }
            
            // Extract and weight genres from library
            val genreScores = analyzeLibraryGenres(library)
            
            if (genreScores.isEmpty()) {
                return@withContext emptyList()
            }
            
            // Get top genres to search for
            val topGenreIds = genreScores
                .sortedByDescending { it.value }
                .take(3)
                .mapNotNull { genreNameToId[it.key.lowercase()] }
            
            if (topGenreIds.isEmpty()) {
                return@withContext emptyList()
            }
            
            // Get library manga IDs to filter out
            val libraryMalIds = library.mapNotNull { getMalIdFromManga(it) }.toSet()
            
            // Fetch recommendations from Jikan
            val recommendations = jikanApi.getMangaByGenres(topGenreIds, limit = 50)
                .data
                .filter { it.malId !in libraryMalIds }
                .take(limit)
            
            // Cache results
            cachedRecommendations = recommendations
            cacheTimestamp = System.currentTimeMillis()
            
            recommendations
        } catch (e: Exception) {
            // On error, return empty list
            emptyList()
        }
    }
    
    /**
     * Analyze library manga to extract weighted genre preferences.
     * Returns a map of genre name to score.
     */
    private fun analyzeLibraryGenres(library: List<LibraryManga>): Map<String, Double> {
        val genreScores = mutableMapOf<String, Double>()
        
        for (manga in library) {
            val genres = manga.manga.getGenres() ?: continue
            
            // Calculate weight based on reading progress
            val progressWeight = when {
                manga.unread == 0 && manga.totalChapters > 0 -> 2.0 // Completed
                manga.hasRead -> 1.5 // Started reading
                else -> 1.0 // Just in library
            }
            
            // Calculate weight based on recency (last read time)
            val recencyWeight = when {
                manga.lastRead > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7) -> 2.0
                manga.lastRead > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30) -> 1.5
                manga.lastRead > 0 -> 1.0
                else -> 0.5
            }
            
            val weight = progressWeight * recencyWeight
            
            for (genre in genres) {
                val normalizedGenre = genre.lowercase().trim()
                genreScores[normalizedGenre] = (genreScores[normalizedGenre] ?: 0.0) + weight
            }
        }
        
        return genreScores
    }
    
    /**
     * Try to extract MAL ID from manga if it has tracking data.
     * This helps filter out manga already in library.
     */
    private fun getMalIdFromManga(manga: LibraryManga): Long? {
        // For now, we can't reliably get MAL ID without tracker data
        // We'll filter by title matching instead in a future enhancement
        return null
    }
    
    /**
     * Get the top genres from user's library for display purposes.
     */
    suspend fun getTopGenres(limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        try {
            val library = getLibraryManga.await()
            val genreScores = analyzeLibraryGenres(library)
            
            genreScores
                .entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { it.key.replaceFirstChar { c -> c.uppercase() } }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Clear the recommendation cache.
     * Call this when library is updated significantly.
     */
    fun clearCache() {
        cachedRecommendations = emptyList()
        cacheTimestamp = 0
    }
    
    /**
     * Check if we have enough library data to make recommendations.
     */
    suspend fun hasEnoughData(): Boolean = withContext(Dispatchers.IO) {
        try {
            val library = getLibraryManga.await()
            library.size >= 3 // Need at least 3 manga for meaningful recommendations
        } catch (e: Exception) {
            false
        }
    }
}

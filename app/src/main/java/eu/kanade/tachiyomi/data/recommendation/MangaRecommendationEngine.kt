package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.jikan.JikanApi
import eu.kanade.tachiyomi.data.jikan.JikanManga
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetLibraryManga

/**
 * Manga Recommendation Engine that analyzes user's library to provide personalized recommendations.
 * 
 * Algorithm:
 * 1. Extract genres from user's library manga
 * 2. Weight genres by recency (recently read manga contribute more)
 * 3. Query Jikan API with top genres
 * 4. Filter out manga already in library
 * 5. Return personalized recommendations
 */
class MangaRecommendationEngine {
    
    private val jikanApi = JikanApi()
    private val getLibraryManga: GetLibraryManga by injectLazy()
    
    /**
     * Get personalized manga recommendations based on user's library
     * 
     * @param limit Maximum number of recommendations to return
     * @return List of recommended JikanManga
     */
    suspend fun getPersonalizedRecommendations(limit: Int = 15): List<JikanManga> {
        val libraryManga = getLibraryManga.await()
        
        // If library is empty, return empty list (caller should fallback to general recommendations)
        if (libraryManga.isEmpty()) {
            return emptyList()
        }
        
        // Extract library manga titles for filtering
        val libraryTitles = libraryManga.map { it.manga.title.lowercase() }.toSet()
        
        // Get top genres from user's library
        val topGenres = getTopGenres(libraryManga)
        
        if (topGenres.isEmpty()) {
            return emptyList()
        }
        
        // Search for manga with similar genres
        val recommendations = mutableListOf<JikanManga>()
        
        for (genre in topGenres.take(3)) {
            try {
                val genreResults = jikanApi.searchManga(genre, limit = 25)
                recommendations.addAll(genreResults.data)
                
                // Rate limiting - small delay between requests
                kotlinx.coroutines.delay(350)
            } catch (e: Exception) {
                // Continue with other genres if one fails
                continue
            }
        }
        
        // Filter and deduplicate
        return recommendations
            .distinctBy { it.malId }
            .filter { manga ->
                // Exclude manga already in library (fuzzy title match)
                val mangaTitle = manga.title.lowercase()
                !libraryTitles.any { libraryTitle ->
                    mangaTitle == libraryTitle || 
                    mangaTitle.contains(libraryTitle) || 
                    libraryTitle.contains(mangaTitle)
                }
            }
            .sortedByDescending { it.score ?: 0.0 }
            .take(limit)
    }
    
    /**
     * Extract top genres from user's library, weighted by reading activity
     * 
     * @param libraryManga List of manga in user's library
     * @return List of genre names, sorted by frequency
     */
    private fun getTopGenres(libraryManga: List<LibraryManga>): List<String> {
        val genreCount = mutableMapOf<String, Int>()
        
        for (manga in libraryManga) {
            // Get genres from manga - uses getOriginalGenres() which splits the genre string
            val genres: List<String> = manga.manga.getOriginalGenres() ?: continue
            
            // Weight by read status (manga with more progress = more weight)
            val weight = when {
                manga.read > 0 -> 2  // Has been read
                else -> 1
            }
            
            genres.forEach { genre ->
                val normalizedGenre = genre.trim()
                if (normalizedGenre.isNotBlank()) {
                    genreCount[normalizedGenre] = (genreCount[normalizedGenre] ?: 0) + weight
                }
            }
        }

        
        // Return top genres sorted by count
        return genreCount.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }
    
    /**
     * Get the top authors from user's library for potential author-based recommendations
     */
    private fun getTopAuthors(libraryManga: List<LibraryManga>): List<String> {
        val authorCount = mutableMapOf<String, Int>()
        
        for (manga in libraryManga) {
            val author = manga.manga.author
            if (!author.isNullOrBlank()) {
                authorCount[author] = (authorCount[author] ?: 0) + 1
            }
            
            val artist = manga.manga.artist
            if (!artist.isNullOrBlank() && artist != author) {
                authorCount[artist] = (authorCount[artist] ?: 0) + 1
            }
        }
        
        return authorCount.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
    }
}

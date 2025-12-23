package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.anilist.AnilistApi
import eu.kanade.tachiyomi.data.anilist.AnilistManga
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetLibraryManga
import yokai.domain.history.interactor.GetHistory
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Manga Recommendation Engine that analyzes user's library to provide personalized recommendations.
 * 
 * Algorithm:
 * 1. Extract genres from user's library manga
 * 2. Weight genres by recency (recently read manga contribute more)
 * 3. Query AniList API with top genres
 * 4. Filter out manga already in library
 * 5. Return personalized recommendations
 */
class MangaRecommendationEngine {
    
    private val anilistApi = AnilistApi()
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getHistory: GetHistory by injectLazy()
    
    /**
     * Get personalized manga recommendations based on user's library
     * 
     * @param limit Maximum number of recommendations to return
     * @return List of recommended AnilistManga
     */
    suspend fun getPersonalizedRecommendations(limit: Int = 15): List<AnilistManga> {
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
        
        // Search for manga with similar genres using AniList's genre filter
        val recommendations = mutableListOf<AnilistManga>()
        
        for (genre in topGenres.take(3)) {
            try {
                // Use searchByGenre for accurate genre-based search
                val genreResults = anilistApi.searchByGenre(genre, limit = 25)
                recommendations.addAll(genreResults)
            } catch (e: Exception) {
                // Continue with other genres if one fails
                continue
            }
        }
        
        // Filter and deduplicate
        return recommendations
            .distinctBy { it.id }
            .filter { manga ->
                // Exclude manga already in library (fuzzy title match)
                val mangaTitle = manga.title.userTitle().lowercase()
                !libraryTitles.any { libraryTitle ->
                    mangaTitle == libraryTitle || 
                    mangaTitle.contains(libraryTitle) || 
                    libraryTitle.contains(mangaTitle)
                }
            }
            .sortedByDescending { it.averageScore ?: 0 }
            .take(limit)
    }
    
    /**
     * Extract top genres from user's library, weighted by reading activity and recency
     * 
     * @param libraryManga List of manga in user's library
     * @return List of genre names, sorted by weighted frequency
     */
    private suspend fun getTopGenres(libraryManga: List<LibraryManga>): List<String> {
        val genreWeights = mutableMapOf<String, Double>()
        val currentTime = Date().time
        
        // Time thresholds in milliseconds
        val oneWeek = TimeUnit.DAYS.toMillis(7)
        val oneMonth = TimeUnit.DAYS.toMillis(30)
        
        for (manga in libraryManga) {
            // Get genres from manga
            val genres: List<String> = manga.manga.getOriginalGenres() ?: continue
            
            // Skip manga with no read progress
            if (manga.read == 0) continue
            
            // Get reading history for this manga
            val history = try {
                getHistory.awaitByMangaId(manga.manga.id ?: continue)
            } catch (e: Exception) {
                null
            }
            
            // Calculate weight based on recency and reading progress
            val weight = if (history != null && history.last_read > 0) {
                val timeSinceLastRead = currentTime - history.last_read
                val recencyWeight = when {
                    timeSinceLastRead < oneWeek -> 5.0   // Read in last week = 5x weight
                    timeSinceLastRead < oneMonth -> 3.0  // Read in last month = 3x weight
                    else -> 1.0                          // Older reads = 1x weight
                }
                
                // Also weight by reading progress (more chapters read = more relevant)
                val progressWeight = when {
                    manga.read >= 10 -> 1.5  // Read 10+ chapters
                    manga.read >= 5 -> 1.2   // Read 5+ chapters
                    manga.read >= 2 -> 1.0   // Read 2+ chapters
                    else -> 0.3              // Only 1 chapter (might be a test read)
                }
                
                recencyWeight * progressWeight
            } else {
                // No history data, use basic read count weighting
                if (manga.read >= 5) 1.0 else 0.5
            }
            
            // Apply weight to all genres of this manga
            genres.forEach { genre ->
                val normalizedGenre = genre.trim()
                if (normalizedGenre.isNotBlank()) {
                    genreWeights[normalizedGenre] = (genreWeights[normalizedGenre] ?: 0.0) + weight
                }
            }
        }
        
        // Return top genres sorted by weighted count
        return genreWeights.entries
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

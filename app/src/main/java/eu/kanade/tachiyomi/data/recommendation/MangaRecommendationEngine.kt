package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.anilist.AnilistApi
import eu.kanade.tachiyomi.data.anilist.AnilistManga
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetLibraryManga

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
        // Note: AniList genres are specific (Action, Adventure, etc.) - simple string matching usually works
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

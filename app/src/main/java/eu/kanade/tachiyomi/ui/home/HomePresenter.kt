package eu.kanade.tachiyomi.ui.home

import eu.kanade.tachiyomi.data.anilist.AnilistApi
import eu.kanade.tachiyomi.data.anilist.AnilistManga
import eu.kanade.tachiyomi.data.recommendation.MangaRecommendationEngine
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Presenter for Home screen - loads manga from AniList API and personalized recommendations
 */
class HomePresenter : BaseCoroutinePresenter<HomeController>() {
    
    private val anilistApi = AnilistApi()
    private val recommendationEngine = MangaRecommendationEngine()
    
    // "For You" - Personalized recommendations based on user's library
    private val _forYouManga = MutableStateFlow<List<AnilistManga>>(emptyList())
    val forYouManga: StateFlow<List<AnilistManga>> = _forYouManga.asStateFlow()
    
    private val _topManga = MutableStateFlow<List<AnilistManga>>(emptyList())
    val topManga: StateFlow<List<AnilistManga>> = _topManga.asStateFlow()
    
    private val _popularManga = MutableStateFlow<List<AnilistManga>>(emptyList())
    val popularManga: StateFlow<List<AnilistManga>> = _popularManga.asStateFlow()
    
    private val _publishingManga = MutableStateFlow<List<AnilistManga>>(emptyList())
    val publishingManga: StateFlow<List<AnilistManga>> = _publishingManga.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        loadHomeData()
    }

    fun loadHomeData() {
        presenterScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Load personalized "For You" recommendations first
                try {
                    val personalizedRecs = recommendationEngine.getPersonalizedRecommendations(limit = 15)
                    _forYouManga.value = personalizedRecs
                } catch (e: Exception) {
                    // If personalized fails, leave it empty - the section will be hidden
                    _forYouManga.value = emptyList()
                }
                
                // Load all other sections - AnilistApi handles rate limiting internally
                
                val topResponse = anilistApi.getTopManga(limit = 15)
                _topManga.value = topResponse
                
                val popularResponse = anilistApi.getPopularManga(limit = 15)
                _popularManga.value = popularResponse
                
                val publishingResponse = anilistApi.getPublishingManga(limit = 15)
                _publishingManga.value = publishingResponse
                
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load manga"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchManga(query: String) {
        presenterScope.launch {
            _isLoading.value = true
            try {
                val response = anilistApi.searchManga(query, limit = 25)
                _topManga.value = response
                _popularManga.value = emptyList()
                _publishingManga.value = emptyList()
                _forYouManga.value = emptyList()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadHomeData()
    }
}

package eu.kanade.tachiyomi.ui.home

import eu.kanade.tachiyomi.data.jikan.JikanApi
import eu.kanade.tachiyomi.data.jikan.JikanManga
import eu.kanade.tachiyomi.data.recommendation.RecommendationEngine
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter for Home screen - loads manga from Jikan API
 * Includes personalized "For You" recommendations based on user's library
 */
class HomePresenter : BaseCoroutinePresenter<HomeController>() {
    
    private val jikanApi = JikanApi()
    private val recommendationEngine = RecommendationEngine()
    
    // Personalized recommendations based on library
    private val _forYouManga = MutableStateFlow<List<JikanManga>>(emptyList())
    val forYouManga: StateFlow<List<JikanManga>> = _forYouManga.asStateFlow()
    
    private val _topManga = MutableStateFlow<List<JikanManga>>(emptyList())
    val topManga: StateFlow<List<JikanManga>> = _topManga.asStateFlow()
    
    private val _popularManga = MutableStateFlow<List<JikanManga>>(emptyList())
    val popularManga: StateFlow<List<JikanManga>> = _popularManga.asStateFlow()
    
    private val _publishingManga = MutableStateFlow<List<JikanManga>>(emptyList())
    val publishingManga: StateFlow<List<JikanManga>> = _publishingManga.asStateFlow()

    private val _recommendedManga = MutableStateFlow<List<JikanManga>>(emptyList())
    val recommendedManga: StateFlow<List<JikanManga>> = _recommendedManga.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Whether user has enough library data for personalized recommendations
    private val _hasPersonalizedData = MutableStateFlow(false)
    val hasPersonalizedData: StateFlow<Boolean> = _hasPersonalizedData.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        loadHomeData()
    }

    fun loadHomeData() {
        presenterScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // First, try to load personalized recommendations
                loadPersonalizedRecommendations()
                
                // Load all sections in parallel
                val topResponse = jikanApi.getTopManga(limit = 15)
                _topManga.value = topResponse.data
                
                // Small delay to respect Jikan rate limiting (3 requests/second)
                kotlinx.coroutines.delay(350)
                
                val popularResponse = jikanApi.getPopularManga(limit = 15)
                _popularManga.value = popularResponse.data
                
                kotlinx.coroutines.delay(350)
                
                val publishingResponse = jikanApi.getPublishingManga(limit = 15)
                _publishingManga.value = publishingResponse.data

                kotlinx.coroutines.delay(350)

                val recommendedResponse = jikanApi.getRecommendations(limit = 15)
                _recommendedManga.value = recommendedResponse.data
                
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load manga"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load personalized recommendations based on user's library.
     * Falls back gracefully if library is empty or insufficient.
     */
    private suspend fun loadPersonalizedRecommendations() {
        try {
            val hasData = recommendationEngine.hasEnoughData()
            _hasPersonalizedData.value = hasData
            
            if (hasData) {
                val recommendations = recommendationEngine.getPersonalizedRecommendations(limit = 15)
                _forYouManga.value = recommendations
            } else {
                _forYouManga.value = emptyList()
            }
        } catch (e: Exception) {
            // Silently fail - personalized recommendations are optional
            _forYouManga.value = emptyList()
            _hasPersonalizedData.value = false
        }
    }

    fun searchManga(query: String) {
        presenterScope.launch {
            _isLoading.value = true
            try {
                val response = jikanApi.searchManga(query, limit = 25)
                _topManga.value = response.data
                _popularManga.value = emptyList()
                _publishingManga.value = emptyList()
                _recommendedManga.value = emptyList()
                _forYouManga.value = emptyList()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        // Clear recommendation cache on manual refresh
        recommendationEngine.clearCache()
        loadHomeData()
    }
}

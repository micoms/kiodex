package eu.kanade.tachiyomi.ui.home

import eu.kanade.tachiyomi.data.jikan.JikanApi
import eu.kanade.tachiyomi.data.jikan.JikanManga
import eu.kanade.tachiyomi.data.recommendation.MangaRecommendationEngine
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Presenter for Home screen - loads manga from Jikan API and personalized recommendations
 */
class HomePresenter : BaseCoroutinePresenter<HomeController>() {
    
    private val jikanApi = JikanApi()
    private val recommendationEngine = MangaRecommendationEngine()
    
    // "For You" - Personalized recommendations based on user's library
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
                
                kotlinx.coroutines.delay(350)
                
                // Load all other sections
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
        loadHomeData()
    }
}

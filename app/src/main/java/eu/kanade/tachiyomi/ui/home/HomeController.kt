package eu.kanade.tachiyomi.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.jikan.JikanManga
import eu.kanade.tachiyomi.databinding.HomeControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsMainController
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Controller for Home screen - displays manga from Jikan (MyAnimeList) API
 */
class HomeController(bundle: Bundle? = null) :
    BaseCoroutineController<HomeControllerBinding, HomePresenter>(bundle),
    RootSearchInterface,
    FloatingSearchInterface {

    override var presenter = HomePresenter()
    
    private var topMangaAdapter: HomeMangaAdapter? = null
    private var popularMangaAdapter: HomeMangaAdapter? = null
    private var publishingMangaAdapter: HomeMangaAdapter? = null
    private var recommendationsAdapter: HomeMangaAdapter? = null

    override fun getTitle(): String? {
        return view?.context?.getString(MR.strings.home)
    }

    override fun showFloatingBar() = true

    override fun createBinding(inflater: LayoutInflater) =
        HomeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        
        // Setup swipe refresh
        binding.swipeRefresh.setStyle()
        binding.swipeRefresh.setOnRefreshListener {
            presenter.refresh()
        }

        // Setup scroll view
        scrollViewWith(
            binding.nestedScrollView,
            swipeRefreshLayout = binding.swipeRefresh,
            afterInsets = {
                val systemInsets = it.ignoredSystemInsets
                binding.nestedScrollView.updatePadding(
                    bottom = activityBinding?.bottomNav?.height ?: systemInsets.bottom,
                )
            },
        )

        // Setup top manga section (default horizontal)
        topMangaAdapter = HomeMangaAdapter { manga -> onMangaClick(manga) }
        binding.topMangaRecycler.adapter = topMangaAdapter
        binding.topMangaRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)

        // Setup popular manga section
        popularMangaAdapter = HomeMangaAdapter { manga -> onMangaClick(manga) }
        binding.popularMangaRecycler.adapter = popularMangaAdapter
        binding.popularMangaRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)

        // Setup publishing manga section
        publishingMangaAdapter = HomeMangaAdapter { manga -> onMangaClick(manga) }
        binding.publishingMangaRecycler.adapter = publishingMangaAdapter
        binding.publishingMangaRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)

        // Setup recommendations section
        recommendationsAdapter = HomeMangaAdapter { manga -> onMangaClick(manga) }
        binding.recommendationsRecycler.adapter = recommendationsAdapter
        binding.recommendationsRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)

        // Observe presenter state
        presenter.topManga.onEach { manga ->
            topMangaAdapter?.submitList(manga)
            binding.topMangaSection.isVisible = manga.isNotEmpty()
        }.launchIn(viewScope)

        presenter.popularManga.onEach { manga ->
            popularMangaAdapter?.submitList(manga)
            binding.popularMangaSection.isVisible = manga.isNotEmpty()
        }.launchIn(viewScope)

        presenter.publishingManga.onEach { manga ->
            publishingMangaAdapter?.submitList(manga)
            binding.publishingMangaSection.isVisible = manga.isNotEmpty()
        }.launchIn(viewScope)

        presenter.recommendedManga.onEach { manga ->
            recommendationsAdapter?.submitList(manga)
            binding.recommendationsSection.isVisible = manga.isNotEmpty()
        }.launchIn(viewScope)

        presenter.isLoading.onEach { loading ->
            binding.swipeRefresh.isRefreshing = loading
            binding.progressBar.isVisible = loading && topMangaAdapter?.itemCount == 0
        }.launchIn(viewScope)

        presenter.error.onEach { error ->
            binding.errorText.isVisible = error != null
            binding.errorText.text = error
        }.launchIn(viewScope)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.home, menu)
        
        val searchView = activityBinding?.searchToolbar?.searchView

        activityBinding?.searchToolbar?.setQueryHint(view?.context?.getString(MR.strings.search), false)
        
        setOnQueryTextChangeListener(searchView) { query ->
            if (!query.isNullOrBlank()) {
                // Switch to Grid for search results
                binding.topMangaRecycler.layoutManager = GridLayoutManager(view?.context, 3)
                presenter.searchManga(query)
                binding.topMangaHeader.text = view?.context?.getString(MR.strings.search_results)
            } else {
                // Switch back to Horizontal for Home
                binding.topMangaRecycler.layoutManager = LinearLayoutManager(view?.context, LinearLayoutManager.HORIZONTAL, false)
                presenter.loadHomeData()
                binding.topMangaHeader.text = view?.context?.getString(MR.strings.top_rated)
            }
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Fallback to super to handle global actions like action_more
        return super.onOptionsItemSelected(item)
    }

    private fun onMangaClick(manga: JikanManga) {
        // Show source picker dialog
        val dialog = SourcePickerDialog(manga)
        dialog.showDialog(router)
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        topMangaAdapter = null
        popularMangaAdapter = null
        publishingMangaAdapter = null
        recommendationsAdapter = null
    }
}

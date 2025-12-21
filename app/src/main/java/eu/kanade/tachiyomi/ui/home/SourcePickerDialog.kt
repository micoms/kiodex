package eu.kanade.tachiyomi.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.jikan.JikanManga
import eu.kanade.tachiyomi.smartsearch.SmartSearchEngine
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy

/**
 * Dialog to let user pick a source to search for a manga from Jikan
 */
class SourcePickerDialog(bundle: Bundle? = null) : DialogController(bundle) {

    private val sourceManager: SourceManager by injectLazy()
    private var scope: CoroutineScope? = null
    
    private var mangaTitle: String = ""
    private var adapter: SourceAdapter? = null
    
    private var progressBar: ProgressBar? = null
    private var recycler: RecyclerView? = null
    private var titleView: TextView? = null

    constructor(manga: JikanManga) : this(Bundle().apply {
        putString(MANGA_TITLE_KEY, manga.title)
    }) {
        mangaTitle = manga.title
    }
    
    override fun onCreateDialog(savedViewState: Bundle?): android.app.Dialog {
        mangaTitle = args.getString(MANGA_TITLE_KEY, "") ?: ""
        
        val dialog = BottomSheetDialog(activity!!)
        val view = LayoutInflater.from(activity).inflate(R.layout.controller_source_selector, null)
        dialog.setContentView(view)

        titleView = view.findViewById(R.id.dialog_title)
        progressBar = view.findViewById(R.id.progress_bar)
        recycler = view.findViewById(R.id.recycler_view)

        titleView?.text = "Searching for \"$mangaTitle\"..."
        
        adapter = SourceAdapter { source, manga ->
            onItemClick(source, manga)
        }
        recycler?.adapter = adapter
        recycler?.layoutManager = LinearLayoutManager(activity)

        startSearch()

        return dialog
    }

    private fun startSearch() {
        progressBar?.isVisible = true
        scope = CoroutineScope(Job() + Dispatchers.IO)
        scope?.launch {
            val validSources = sourceManager.getCatalogueSources()
                .filterIsInstance<CatalogueSource>()
                .filter { it.supportsLatest } 
            
            val smartSearchEngine = SmartSearchEngine(coroutineContext)
            
            val deferred = validSources.map { source ->
                async {
                    try {
                        val result = smartSearchEngine.normalSearch(source, mangaTitle)
                        if (result != null) {
                            launchUI {
                                adapter?.addResult(SourceResult(source, result))
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore errors
                    }
                }
            }
            
            deferred.awaitAll()
            
            launchUI {
                if (progressBar?.isVisible == true) { // only hide if check ok
                     progressBar?.isVisible = false
                }
                if (adapter?.itemCount == 0) {
                    titleView?.text = "No results found"
                } else {
                    titleView?.text = "Select a source"
                }
            }
        }
    }

    private fun onItemClick(source: CatalogueSource, manga: SManga) {
        // UI feedback
        progressBar?.isVisible = true
        recycler?.isVisible = false
        titleView?.text = "Opening..."
        
        // Capture router safely
        val navRouter = router

        scope?.launch {
            try {
                val smartSearchEngine = SmartSearchEngine(coroutineContext)
                val localManga = smartSearchEngine.networkToLocalManga(manga, source.id)
                launchUI {
                    navRouter.pushController(
                         MangaDetailsController(localManga, true).withFadeTransaction()
                    )
                    dismissDialog()
                }
            } catch (e: Exception) {
                launchUI {
                    progressBar?.isVisible = false
                    recycler?.isVisible = true
                    titleView?.text = "Error opening manga"
                    Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope?.cancel()
        progressBar = null
        recycler = null
        titleView = null
        adapter = null
    }

    class SourceAdapter(private val onClick: (CatalogueSource, SManga) -> Unit) : RecyclerView.Adapter<SourceAdapter.Holder>() {
        private val items = mutableListOf<SourceResult>()

        fun addResult(result: SourceResult) {
            items.add(result)
            notifyItemInserted(items.size - 1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_source_selection, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onClick(item.source, item.manga) }
        }

        override fun getItemCount(): Int = items.size

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val name = view.findViewById<TextView>(R.id.source_name)
            private val title = view.findViewById<TextView>(R.id.manga_title)
            private val icon = view.findViewById<ImageView>(R.id.source_icon) // Unused but bound

            fun bind(item: SourceResult) {
                name.text = item.source.name
                title.text = item.manga.title
                icon.isVisible = false // Hide icon dynamically as we don't load it
            }
        }
    }

    data class SourceResult(val source: CatalogueSource, val manga: SManga)
    
    companion object {
        private const val MANGA_TITLE_KEY = "manga_title"
    }
}

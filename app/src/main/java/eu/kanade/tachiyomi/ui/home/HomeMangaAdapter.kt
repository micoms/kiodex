package eu.kanade.tachiyomi.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import eu.kanade.tachiyomi.data.jikan.JikanManga
import eu.kanade.tachiyomi.databinding.ItemHomeMangaBinding

/**
 * Adapter for displaying Jikan manga items in the Home screen
 */
class HomeMangaAdapter(
    private val onMangaClick: (JikanManga) -> Unit,
) : ListAdapter<JikanManga, HomeMangaAdapter.MangaViewHolder>(MangaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val binding = ItemHomeMangaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return MangaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onMangaClick(item)
        }
    }

    override fun onViewRecycled(holder: MangaViewHolder) {
        super.onViewRecycled(holder)
        holder.itemView.setOnClickListener(null)
    }

    class MangaViewHolder(
        private val binding: ItemHomeMangaBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(manga: JikanManga) {
            binding.mangaTitle.text = manga.title
            binding.mangaScore.text = manga.score?.let { "★ %.1f".format(it) } ?: ""
            binding.mangaType.text = manga.type ?: ""
            
            // Load cover image
            val imageUrl = manga.images?.jpg?.largeImageUrl 
                ?: manga.images?.jpg?.imageUrl
                ?: manga.images?.webp?.largeImageUrl
            
            binding.mangaCover.load(imageUrl) {
                crossfade(true)
            }
            
            // Show status badge if publishing
            binding.statusBadge.visibility = if (manga.status == "Publishing") {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private class MangaDiffCallback : DiffUtil.ItemCallback<JikanManga>() {
        override fun areItemsTheSame(oldItem: JikanManga, newItem: JikanManga): Boolean {
            return oldItem.malId == newItem.malId
        }

        override fun areContentsTheSame(oldItem: JikanManga, newItem: JikanManga): Boolean {
            return oldItem == newItem
        }
    }
}


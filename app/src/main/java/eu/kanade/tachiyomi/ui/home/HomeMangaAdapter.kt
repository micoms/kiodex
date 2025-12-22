package eu.kanade.tachiyomi.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import eu.kanade.tachiyomi.data.anilist.AnilistManga
import eu.kanade.tachiyomi.databinding.ItemHomeMangaBinding

/**
 * Adapter for displaying AniList manga items in the Home screen
 */
class HomeMangaAdapter(
    private val onMangaClick: (AnilistManga) -> Unit,
) : ListAdapter<AnilistManga, HomeMangaAdapter.MangaViewHolder>(MangaDiffCallback()) {

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

        fun bind(manga: AnilistManga) {
            binding.mangaTitle.text = manga.title.userTitle()
            // Convert score (0-100) to 10-point scale
            binding.mangaScore.text = manga.averageScore?.let { "★ %.1f".format(it / 10f) } ?: ""
            // binding.mangaType.text = manga.format ?: ""
            
            // Load cover image
            val imageUrl = manga.coverImage.extraLarge 
                ?: manga.coverImage.large
                ?: manga.coverImage.medium
            
            binding.mangaCover.load(imageUrl) {
                crossfade(true)
            }
            
            // Show status badge if currently releasing
            binding.statusBadge.visibility = if (manga.status == "RELEASING") {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private class MangaDiffCallback : DiffUtil.ItemCallback<AnilistManga>() {
        override fun areItemsTheSame(oldItem: AnilistManga, newItem: AnilistManga): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AnilistManga, newItem: AnilistManga): Boolean {
            return oldItem == newItem
        }
    }
}


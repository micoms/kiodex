package eu.kanade.tachiyomi.data.jikan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Jikan API response wrapper
 */
@Serializable
data class JikanResponse<T>(
    val data: T,
    val pagination: JikanPagination? = null,
)

@Serializable
data class JikanPagination(
    @SerialName("last_visible_page") val lastVisiblePage: Int,
    @SerialName("has_next_page") val hasNextPage: Boolean,
    @SerialName("current_page") val currentPage: Int = 1,
)

/**
 * Jikan Manga model
 */
@Serializable
data class JikanManga(
    @SerialName("mal_id") val malId: Long,
    val url: String? = null,
    val images: JikanImages? = null,
    val title: String,
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    val type: String? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val status: String? = null,
    val score: Double? = null,
    @SerialName("scored_by") val scoredBy: Int? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val members: Int? = null,
    val favorites: Int? = null,
    val synopsis: String? = null,
    val background: String? = null,
    val authors: List<JikanAuthor>? = null,
    val genres: List<JikanGenre>? = null,
    val themes: List<JikanGenre>? = null,
    val demographics: List<JikanGenre>? = null,
)

@Serializable
data class JikanImages(
    val jpg: JikanImageUrls? = null,
    val webp: JikanImageUrls? = null,
)

@Serializable
data class JikanImageUrls(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("small_image_url") val smallImageUrl: String? = null,
    @SerialName("large_image_url") val largeImageUrl: String? = null,
)

@Serializable
data class JikanAuthor(
    @SerialName("mal_id") val malId: Long,
    val type: String? = null,
    val name: String,
    val url: String? = null,
)

@Serializable
data class JikanGenre(
    @SerialName("mal_id") val malId: Long,
    val type: String? = null,
    val name: String,
    val url: String? = null,
)

@Serializable
data class JikanRecommendation(
    @SerialName("mal_id") val malId: String,
    val entry: List<JikanManga>,
    val content: String,
)

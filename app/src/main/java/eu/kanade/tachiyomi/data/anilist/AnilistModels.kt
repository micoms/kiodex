package eu.kanade.tachiyomi.data.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AniList GraphQL Response Wrapper
 */
@Serializable
data class AnilistResponse<T>(
    val data: T
)

@Serializable
data class AnilistPageData(
    @SerialName("Page") val page: AnilistPage
)

@Serializable
data class AnilistPage(
    val pageInfo: AnilistPageInfo,
    val media: List<AnilistManga>
)

@Serializable
data class AnilistPageInfo(
    val total: Int,
    val perPage: Int,
    val currentPage: Int,
    val lastPage: Int,
    val hasNextPage: Boolean
)

/**
 * AniList Media Model (Manga)
 */
@Serializable
data class AnilistManga(
    val id: Long,
    val title: AnilistTitle,
    val coverImage: AnilistCoverImage,
    val description: String? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val format: String? = null,
    val countryOfOrigin: String? = null,
    val genres: List<String>? = null,
    val favourites: Int? = null
)

@Serializable
data class AnilistTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
    val userPreferred: String? = null
) {
    // Helper to get best available title
    fun userTitle(): String = userPreferred ?: english ?: romaji ?: native ?: ""
}

@Serializable
data class AnilistCoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
    val color: String? = null
)

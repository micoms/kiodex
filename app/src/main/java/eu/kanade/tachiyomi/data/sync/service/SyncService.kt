package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger
import yokai.domain.sync.SyncPreferences

@Serializable
data class SyncData(
    val deviceId: String = "",
    val backup: Backup? = null,
)

abstract class SyncService(
    val context: Context,
    val json: Json,
    val syncPreferences: SyncPreferences,
) {
    abstract suspend fun doSync(syncData: SyncData): Backup?

    /**
     * Merges the local and remote sync data into a single JSON string.
     *
     * @param localSyncData The SData containing the local sync data.
     * @param remoteSyncData The SData containing the remote sync data.
     * @return The JSON string containing the merged sync data.
     */
    protected fun mergeSyncData(localSyncData: SyncData, remoteSyncData: SyncData): SyncData {
        val mergedCategoriesList =
            mergeCategoriesLists(localSyncData.backup?.backupCategories, remoteSyncData.backup?.backupCategories)

        val mergedMangaList = mergeMangaLists(
            localSyncData.backup?.backupManga,
            remoteSyncData.backup?.backupManga,
            localSyncData.backup?.backupCategories ?: emptyList(),
            remoteSyncData.backup?.backupCategories ?: emptyList(),
            mergedCategoriesList,
        )

        val mergedSourcesList =
            mergeSourcesLists(localSyncData.backup?.backupSources, remoteSyncData.backup?.backupSources)
        val mergedPreferencesList =
            mergePreferencesLists(localSyncData.backup?.backupPreferences, remoteSyncData.backup?.backupPreferences)
        val mergedSourcePreferencesList = mergeSourcePreferencesLists(
            localSyncData.backup?.backupSourcePreferences,
            remoteSyncData.backup?.backupSourcePreferences,
        )

        // Create the merged Backup object
        val mergedBackup = Backup(
            backupManga = mergedMangaList,
            backupCategories = mergedCategoriesList,
            backupSources = mergedSourcesList,
            backupPreferences = mergedPreferencesList,
            backupSourcePreferences = mergedSourcePreferencesList,
        )

        // Create the merged SData object
        return SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = mergedBackup,
        )
    }

    /**
     * Merges two lists of BackupManga objects, preferring local manga when conflicts arise.
     */
    private fun mergeMangaLists(
        localMangaList: List<BackupManga>?,
        remoteMangaList: List<BackupManga>?,
        localCategories: List<BackupCategory>,
        remoteCategories: List<BackupCategory>,
        mergedCategories: List<BackupCategory>,
    ): List<BackupManga> {
        val logTag = "MergeMangaLists"

        val localMangaListSafe = localMangaList.orEmpty()
        val remoteMangaListSafe = remoteMangaList.orEmpty()

        Logger.d(logTag) {
            "Starting merge. Local list size: ${localMangaListSafe.size}, Remote list size: ${remoteMangaListSafe.size}"
        }

        fun mangaCompositeKey(manga: BackupManga): String {
            return "${manga.source}|${manga.url}|${manga.title.lowercase().trim()}|${manga.author?.lowercase()?.trim()}"
        }

        // Create maps using composite keys
        val localMangaMap = localMangaListSafe.associateBy { mangaCompositeKey(it) }
        val remoteMangaMap = remoteMangaListSafe.associateBy { mangaCompositeKey(it) }

        val localCategoriesMapByOrder = localCategories.associateBy { it.order }
        val remoteCategoriesMapByOrder = remoteCategories.associateBy { it.order }
        val mergedCategoriesMapByName = mergedCategories.associateBy { it.name }

        fun updateCategories(theManga: BackupManga, theMap: Map<Int, BackupCategory>): BackupManga {
            return theManga.copy(
                categories = theManga.categories.mapNotNull {
                    theMap[it]?.let { category ->
                        mergedCategoriesMapByName[category.name]?.order
                    }
                },
            )
        }

        val mergedList = (localMangaMap.keys + remoteMangaMap.keys).distinct().mapNotNull { compositeKey ->
            val local = localMangaMap[compositeKey]
            val remote = remoteMangaMap[compositeKey]

            when {
                local != null && remote == null -> updateCategories(local, localCategoriesMapByOrder)
                local == null && remote != null -> updateCategories(remote, remoteCategoriesMapByOrder)
                local != null && remote != null -> {
                    // Prefer local manga metadata with merged chapters
                    Logger.d(logTag) { "Merging manga ${local.title}: keeping local with merged chapters" }
                    updateCategories(
                        local.copy(chapters = mergeChapters(local.chapters, remote.chapters)),
                        localCategoriesMapByOrder,
                    )
                }
                else -> null
            }
        }

        val (favorites, nonFavorites) = mergedList.partition { it.favorite }

        Logger.d(logTag) {
            "Merge completed. Total merged manga: ${mergedList.size}, Favorites: ${favorites.size}, " +
                "Non-Favorites: ${nonFavorites.size}"
        }

        return mergedList
    }

    /**
     * Merges two lists of BackupChapter objects, preferring read chapters and local when conflicts.
     */
    private fun mergeChapters(
        localChapters: List<BackupChapter>,
        remoteChapters: List<BackupChapter>,
    ): List<BackupChapter> {
        val logTag = "MergeChapters"

        fun chapterCompositeKey(chapter: BackupChapter): String {
            return "${chapter.url}|${chapter.name}|${chapter.chapterNumber}"
        }

        val localChapterMap = localChapters.associateBy { chapterCompositeKey(it) }
        val remoteChapterMap = remoteChapters.associateBy { chapterCompositeKey(it) }

        Logger.d(logTag) {
            "Starting chapter merge. Local chapters: ${localChapters.size}, Remote chapters: ${remoteChapters.size}"
        }

        val mergedChapters = (localChapterMap.keys + remoteChapterMap.keys).distinct().mapNotNull { compositeKey ->
            val localChapter = localChapterMap[compositeKey]
            val remoteChapter = remoteChapterMap[compositeKey]

            when {
                localChapter != null && remoteChapter == null -> localChapter
                localChapter == null && remoteChapter != null -> remoteChapter
                localChapter != null && remoteChapter != null -> {
                    // Prefer read chapters, otherwise prefer local
                    when {
                        localChapter.read && !remoteChapter.read -> localChapter
                        !localChapter.read && remoteChapter.read -> remoteChapter
                        // Prefer higher last page read progress
                        localChapter.lastPageRead >= remoteChapter.lastPageRead -> localChapter
                        else -> remoteChapter
                    }
                }
                else -> null
            }
        }

        Logger.d(logTag) { "Chapter merge completed. Total merged chapters: ${mergedChapters.size}" }

        return mergedChapters
    }

    /**
     * Merges two lists of BackupCategory objects.
     */
    private fun mergeCategoriesLists(
        localCategoriesList: List<BackupCategory>?,
        remoteCategoriesList: List<BackupCategory>?,
    ): List<BackupCategory> {
        if (localCategoriesList == null) return remoteCategoriesList ?: emptyList()
        if (remoteCategoriesList == null) return localCategoriesList
        val localCategoriesMap = localCategoriesList.associateBy { it.name }
        val remoteCategoriesMap = remoteCategoriesList.associateBy { it.name }

        val mergedCategoriesMap = mutableMapOf<String, BackupCategory>()

        localCategoriesMap.forEach { (name, localCategory) ->
            val remoteCategory = remoteCategoriesMap[name]
            if (remoteCategory != null) {
                val mergedCategory = if (localCategory.order > remoteCategory.order) {
                    localCategory
                } else {
                    remoteCategory
                }
                mergedCategoriesMap[name] = mergedCategory
            } else {
                mergedCategoriesMap[name] = localCategory
            }
        }

        remoteCategoriesMap.forEach { (name, remoteCategory) ->
            if (!mergedCategoriesMap.containsKey(name)) {
                mergedCategoriesMap[name] = remoteCategory
            }
        }

        return mergedCategoriesMap.values.toList()
    }

    private fun mergeSourcesLists(
        localSources: List<BackupSource>?,
        remoteSources: List<BackupSource>?,
    ): List<BackupSource> {
        val localSourceMap = localSources?.associateBy { it.sourceId } ?: emptyMap()
        val remoteSourceMap = remoteSources?.associateBy { it.sourceId } ?: emptyMap()

        val mergedSources = (localSourceMap.keys + remoteSourceMap.keys).distinct().mapNotNull { sourceId ->
            val localSource = localSourceMap[sourceId]
            val remoteSource = remoteSourceMap[sourceId]

            when {
                localSource != null && remoteSource == null -> localSource
                remoteSource != null && localSource == null -> remoteSource
                localSource != null && remoteSource != null -> localSource // prefer local
                else -> null
            }
        }

        return mergedSources
    }

    private fun mergePreferencesLists(
        localPreferences: List<BackupPreference>?,
        remotePreferences: List<BackupPreference>?,
    ): List<BackupPreference> {
        val localPreferencesMap = localPreferences?.associateBy { it.key } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.key } ?: emptyMap()

        val mergedPreferences = (localPreferencesMap.keys + remotePreferencesMap.keys).distinct().mapNotNull { key ->
            val localPreference = localPreferencesMap[key]
            val remotePreference = remotePreferencesMap[key]

            when {
                localPreference != null && remotePreference == null -> localPreference
                remotePreference != null && localPreference == null -> remotePreference
                localPreference != null && remotePreference != null -> localPreference // prefer local
                else -> null
            }
        }

        return mergedPreferences
    }

    private fun mergeSourcePreferencesLists(
        localPreferences: List<BackupSourcePreferences>?,
        remotePreferences: List<BackupSourcePreferences>?,
    ): List<BackupSourcePreferences> {
        val localPreferencesMap = localPreferences?.associateBy { it.sourceKey } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.sourceKey } ?: emptyMap()

        val mergedSourcePreferences = (localPreferencesMap.keys + remotePreferencesMap.keys).distinct()
            .mapNotNull { sourceKey ->
                val localSourcePreference = localPreferencesMap[sourceKey]
                val remoteSourcePreference = remotePreferencesMap[sourceKey]

                when {
                    localSourcePreference != null && remoteSourcePreference == null -> localSourcePreference
                    remoteSourcePreference != null && localSourcePreference == null -> remoteSourcePreference
                    localSourcePreference != null && remoteSourcePreference != null -> {
                        val mergedPrefs =
                            mergeIndividualPreferences(localSourcePreference.prefs, remoteSourcePreference.prefs)
                        BackupSourcePreferences(sourceKey, mergedPrefs)
                    }
                    else -> null
                }
            }

        return mergedSourcePreferences
    }

    private fun mergeIndividualPreferences(
        localPrefs: List<BackupPreference>,
        remotePrefs: List<BackupPreference>,
    ): List<BackupPreference> {
        val mergedPrefsMap = (localPrefs + remotePrefs).associateBy { it.key }
        return mergedPrefsMap.values.toList()
    }
}

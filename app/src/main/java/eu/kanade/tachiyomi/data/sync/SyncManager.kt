package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.sync.service.SyncData
import eu.kanade.tachiyomi.data.sync.service.SyncYomiSyncService
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.manga.interactor.GetManga
import yokai.domain.sync.SyncPreferences
import java.io.File
import java.io.IOException
import java.util.Date

/**
 * A manager to handle synchronization tasks in the app.
 */
class SyncManager(
    private val context: Context,
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private var json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    private val notifier: SyncNotifier = SyncNotifier(context)

    // Backup creators
    private val categoriesBackupCreator: CategoriesBackupCreator = CategoriesBackupCreator()
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator()
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator()
    private val sourcesBackupCreator: SourcesBackupCreator = SourcesBackupCreator()

    enum class SyncService(val value: Int) {
        NONE(0),
        SYNCYOMI(1),
        ;

        companion object {
            fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
        }
    }

    /**
     * Syncs data with a sync service.
     */
    suspend fun syncData() {
        val syncSettings = syncPreferences.getSyncSettings()

        val backupOptions = BackupOptions(
            libraryEntries = syncSettings.libraryEntries,
            categories = syncSettings.categories,
            chapters = syncSettings.chapters,
            tracking = syncSettings.tracking,
            history = syncSettings.history,
            appPrefs = syncSettings.appPrefs,
            sourcePrefs = syncSettings.sourcePrefs,
            customInfo = syncSettings.customInfo,
            readManga = syncSettings.readManga,
            includePrivate = syncSettings.includePrivate,
        )

        Logger.d { "Begin create backup for sync" }
        val backup = createBackupForSync(backupOptions)
        Logger.d { "End create backup for sync" }

        // Create the SyncData object
        val syncData = SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = backup,
        )

        // Handle sync based on the selected service
        val syncService = when (val syncServiceType = SyncService.fromInt(syncPreferences.syncService().get())) {
            SyncService.SYNCYOMI -> {
                SyncYomiSyncService(
                    context,
                    json,
                    syncPreferences,
                    notifier,
                )
            }

            else -> {
                Logger.e { "Invalid sync service type: $syncServiceType" }
                null
            }
        }

        val remoteBackup = syncService?.doSync(syncData)

        if (remoteBackup == null) {
            Logger.d { "Skip restore due to network issues" }
            return
        }

        if (remoteBackup === syncData.backup) {
            // nothing changed
            Logger.d { "Skip restore due to remote was overwritten from local" }
            syncPreferences.lastSyncTimestamp().set(Date().time)
            notifier.showSyncSuccess("Sync completed successfully")
            return
        }

        // Stop the sync early if the remote backup is null or empty
        if (remoteBackup.backupManga.isEmpty()) {
            notifier.showSyncError("No data found on remote server.")
            return
        }

        // Check if it's first sync based on lastSyncTimestamp
        if (syncPreferences.lastSyncTimestamp().get() == 0L) {
            // It's first sync no need to restore data. (just update remote data)
            syncPreferences.lastSyncTimestamp().set(Date().time)
            notifier.showSyncSuccess("Updated remote data successfully")
            return
        }

        val (filteredFavorites, _) = filterFavoritesAndNonFavorites(remoteBackup)

        val newSyncData = backup.copy(
            backupManga = filteredFavorites,
            backupCategories = remoteBackup.backupCategories,
            backupSources = remoteBackup.backupSources,
            backupPreferences = remoteBackup.backupPreferences,
            backupSourcePreferences = remoteBackup.backupSourcePreferences,
        )

        // It's local sync no need to restore data
        if (filteredFavorites.isEmpty()) {
            syncPreferences.lastSyncTimestamp().set(Date().time)
            notifier.showSyncSuccess("Sync completed successfully")
            return
        }

        val backupUri = writeSyncDataToCache(context, newSyncData)
        Logger.d { "Got Backup Uri: $backupUri" }
        if (backupUri != null) {
            BackupRestoreJob.start(
                context,
                backupUri,
            )

            syncPreferences.lastSyncTimestamp().set(Date().time)
        } else {
            Logger.e { "Failed to write sync data to file" }
        }
    }

    /**
     * Creates a Backup object suitable for syncing (in-memory, not written to file).
     */
    private suspend fun createBackupForSync(options: BackupOptions): Backup {
        val mangas = getManga.awaitFavorites()
        val backupManga = if (options.libraryEntries) mangaBackupCreator(mangas, options) else emptyList()

        return Backup(
            backupManga = backupManga,
            backupCategories = if (options.categories) categoriesBackupCreator() else emptyList(),
            backupSources = sourcesBackupCreator(backupManga),
            backupPreferences = if (options.appPrefs) preferenceBackupCreator.createApp(options.includePrivate) else emptyList(),
            backupSourcePreferences = if (options.sourcePrefs) preferenceBackupCreator.createSource(options.includePrivate) else emptyList(),
        )
    }

    private fun writeSyncDataToCache(context: Context, backup: Backup): Uri? {
        val protoBuf: ProtoBuf = Injekt.get()
        val cacheFile = File(context.cacheDir, "tachiyomi_sync_data.proto.gz")
        return try {
            cacheFile.outputStream().use { output ->
                output.write(protoBuf.encodeToByteArray(Backup.serializer(), backup))
                Uri.fromFile(cacheFile)
            }
        } catch (e: IOException) {
            Logger.e(e) { "Failed to write sync data to cache" }
            null
        }
    }

    /**
     * Filters the favorite and non-favorite manga from the backup.
     */
    private fun filterFavoritesAndNonFavorites(backup: Backup): Pair<List<BackupManga>, List<BackupManga>> {
        val favorites = mutableListOf<BackupManga>()
        val nonFavorites = mutableListOf<BackupManga>()

        backup.backupManga.forEach { remoteManga ->
            when {
                remoteManga.favorite -> favorites.add(remoteManga)
                else -> nonFavorites.add(remoteManga)
            }
        }

        return Pair(favorites, nonFavorites)
    }
}

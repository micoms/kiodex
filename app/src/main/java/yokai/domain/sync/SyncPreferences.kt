package yokai.domain.sync

import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.models.SyncTriggerOptions
import yokai.domain.sync.models.SyncSettings
import java.util.UUID

class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun clientHost() = preferenceStore.getString("sync_client_host", "https://sync.tachiyomi.org")
    fun clientAPIKey() = preferenceStore.getString("sync_client_api_key", "")
    fun lastSyncTimestamp() = preferenceStore.getLong(Preference.appStateKey("last_sync_timestamp"), 0L)

    fun lastSyncEtag() = preferenceStore.getString("sync_etag", "")

    fun syncInterval() = preferenceStore.getInt("sync_interval", 0)
    fun syncService() = preferenceStore.getInt("sync_service", 0)

    fun uniqueDeviceID(): String {
        val uniqueIDPreference = preferenceStore.getString(Preference.appStateKey("unique_device_id"), "")

        // Retrieve the current value of the preference
        var uniqueID = uniqueIDPreference.get()
        if (uniqueID.isBlank()) {
            uniqueID = UUID.randomUUID().toString()
            uniqueIDPreference.set(uniqueID)
        }

        return uniqueID
    }

    fun isSyncEnabled(): Boolean {
        return syncService().get() != SyncManager.SyncService.NONE.value
    }

    // Set-based preference for sync triggers picker
    fun syncTriggerKeys(): Preference<Set<String>> = preferenceStore.getStringSet(
        "sync_trigger_keys",
        emptySet(),
    )

    // Set-based preference for what-to-sync picker
    fun syncSettingsKeys(): Preference<Set<String>> = preferenceStore.getStringSet(
        "sync_settings_keys",
        DEFAULT_SYNC_SETTINGS_KEYS,
    )

    fun getSyncSettings(): SyncSettings {
        val keys = syncSettingsKeys().get()
        return SyncSettings(
            libraryEntries = SYNC_KEY_LIBRARY_ENTRIES in keys,
            categories = SYNC_KEY_CATEGORIES in keys,
            chapters = SYNC_KEY_CHAPTERS in keys,
            tracking = SYNC_KEY_TRACKING in keys,
            history = SYNC_KEY_HISTORY in keys,
            appPrefs = SYNC_KEY_APP_PREFS in keys,
            sourcePrefs = SYNC_KEY_SOURCE_PREFS in keys,
            customInfo = SYNC_KEY_CUSTOM_INFO in keys,
            readManga = SYNC_KEY_READ_MANGA in keys,
            includePrivate = SYNC_KEY_INCLUDE_PRIVATE in keys,
        )
    }

    fun getSyncTriggerOptions(): SyncTriggerOptions {
        val keys = syncTriggerKeys().get()
        return SyncTriggerOptions(
            syncOnChapterRead = TRIGGER_KEY_CHAPTER_READ in keys,
            syncOnChapterOpen = TRIGGER_KEY_CHAPTER_OPEN in keys,
            syncOnAppStart = TRIGGER_KEY_APP_START in keys,
            syncOnAppResume = TRIGGER_KEY_APP_RESUME in keys,
        )
    }

    companion object {
        // Sync trigger keys
        const val TRIGGER_KEY_CHAPTER_READ = "chapter_read"
        const val TRIGGER_KEY_CHAPTER_OPEN = "chapter_open"
        const val TRIGGER_KEY_APP_START = "app_start"
        const val TRIGGER_KEY_APP_RESUME = "app_resume"

        // Sync settings keys
        const val SYNC_KEY_LIBRARY_ENTRIES = "library_entries"
        const val SYNC_KEY_CATEGORIES = "categories"
        const val SYNC_KEY_CHAPTERS = "chapters"
        const val SYNC_KEY_TRACKING = "tracking"
        const val SYNC_KEY_HISTORY = "history"
        const val SYNC_KEY_APP_PREFS = "app_prefs"
        const val SYNC_KEY_SOURCE_PREFS = "source_prefs"
        const val SYNC_KEY_CUSTOM_INFO = "custom_info"
        const val SYNC_KEY_READ_MANGA = "read_manga"
        const val SYNC_KEY_INCLUDE_PRIVATE = "include_private"

        // Default sync settings (everything except private)
        val DEFAULT_SYNC_SETTINGS_KEYS = setOf(
            SYNC_KEY_LIBRARY_ENTRIES,
            SYNC_KEY_CATEGORIES,
            SYNC_KEY_CHAPTERS,
            SYNC_KEY_TRACKING,
            SYNC_KEY_HISTORY,
            SYNC_KEY_APP_PREFS,
            SYNC_KEY_SOURCE_PREFS,
            SYNC_KEY_CUSTOM_INFO,
            SYNC_KEY_READ_MANGA,
        )
    }
}




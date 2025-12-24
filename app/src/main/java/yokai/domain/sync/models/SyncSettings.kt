package yokai.domain.sync.models

/**
 * Settings for what data should be included in sync operations.
 * Mirrors the BackupOptions structure for consistency.
 */
data class SyncSettings(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val appPrefs: Boolean = true,
    val sourcePrefs: Boolean = true,
    val customInfo: Boolean = true,
    val readManga: Boolean = true,
    val includePrivate: Boolean = false,
)



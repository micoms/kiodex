package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class KirooSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,
) : SyncService(context, json, syncPreferences) {

    override suspend fun doSync(syncData: SyncData): Backup? {
        try {
            val remoteData = pullSyncData()

            val finalSyncData = if (remoteData != null) {
                logcat(LogPriority.DEBUG) { "Merging local and remote sync data" }
                mergeSyncData(syncData, remoteData)
            } else {
                logcat(LogPriority.DEBUG) { "Initializing remote data with local data" }
                syncData
            }

            pushSyncData(finalSyncData)
            return finalSyncData.backup
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Error syncing: ${e.message}" }
            notifier.showSyncError(e.message)
            return null
        }
    }

    private suspend fun pullSyncData(): SyncData? {
        val host = syncPreferences.clientHost().get()
        val apiKey = syncPreferences.clientAPIKey().get()
        val downloadUrl = "$host/api/sync"

        // Ensure host is configured
        if (host.isBlank()) {
            throw Exception("Sync host not configured")
        }

        val headers = Headers.Builder()
            .add("X-API-Key", apiKey)
            .build()

        val request = GET(
            url = downloadUrl,
            headers = headers,
        )

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).await()

        if (response.isSuccessful) {
            val bodyString = response.body.string()
            return try {
                // Server returns the Backup object directly
                val backup = json.decodeFromString(Backup.serializer(), bodyString)
                SyncData(backup = backup)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to decode sync data: ${e.message}" }
                throw Exception("Failed to decode server response")
            }
        } else if (response.code == 404) {
             // No data on server yet
             return null
        } else {
            val errorBody = response.body.string()
            throw Exception("Sync failed: ${response.code} - $errorBody")
        }
    }

    private suspend fun pushSyncData(syncData: SyncData) {
        val host = syncPreferences.clientHost().get()
        val apiKey = syncPreferences.clientAPIKey().get()
        val uploadUrl = "$host/api/sync"

        if (host.isBlank()) return

        val headers = Headers.Builder()
            .add("X-API-Key", apiKey)
            .build()
        
        // Serialize SyncData (which wraps Backup + DeviceID)
        val jsonString = json.encodeToString(SyncData.serializer(), syncData)
        val body = jsonString.toRequestBody("application/json".toMediaType())

        val request = POST(
            url = uploadUrl,
            headers = headers,
            body = body
        )

        val client = OkHttpClient.Builder()
            .connectTimeout(300, TimeUnit.SECONDS) // 5 minutes for large libraries
            .writeTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).await()

        if (!response.isSuccessful) {
            val errorBody = response.body.string()
            throw Exception("Push failed: ${response.code} - $errorBody")
        }
    }
}

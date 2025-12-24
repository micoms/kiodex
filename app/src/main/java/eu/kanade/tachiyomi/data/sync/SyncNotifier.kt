package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString

class SyncNotifier(private val context: Context) {

    private val preferences: PreferencesHelper by injectLazy()

    private val progressNotificationBuilder = context.notificationBuilder(Notifications.CHANNEL_SYNC_PROGRESS) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_yokai)
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(Notifications.CHANNEL_SYNC_PROGRESS) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_yokai)
        setAutoCancel(false)
    }

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notificationManager.notify(id, build())
    }

    fun showSyncProgress(
        content: String = "",
        progress: Int = 0,
        maxAmount: Int = 100,
    ): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            setContentTitle(context.getString(MR.strings.syncing_library))

            if (!preferences.hideNotificationContent().get()) {
                setContentText(content)
            }

            setProgress(maxAmount, progress, true)
            setOnlyAlertOnce(true)

            clearActions()
            addAction(
                R.drawable.ic_close_24dp,
                context.getString(MR.strings.cancel),
                NotificationReceiver.cancelSyncPendingBroadcast(context, Notifications.ID_SYNC_PROGRESS),
            )
        }

        builder.show(Notifications.ID_SYNC_PROGRESS)
        return builder
    }

    fun showSyncError(error: String?) {
        context.notificationManager.cancel(Notifications.ID_SYNC_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.getString(MR.strings.sync_error))
            setContentText(error)

            show(Notifications.ID_SYNC_COMPLETE)
        }
    }

    fun showSyncSuccess(message: String?) {
        context.notificationManager.cancel(Notifications.ID_SYNC_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.getString(MR.strings.sync_complete))
            setContentText(message)

            show(Notifications.ID_SYNC_COMPLETE)
        }
    }
}

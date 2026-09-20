package eu.kanade.tachiyomi.data.community

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.BuildConfig
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Stops old installs resuming social work, without opening or deleting their identity/store. */
internal object CommunityDormancy {
    fun onDatabaseOpen(database: SupportSQLiteDatabase) {
        if (!BuildConfig.COMMUNITY_ENABLED) {
            // A previous process may have persisted enabled=1. Reset before any library write.
            val preferences: BasePreferences = Injekt.get()
            val enabled = PersonalSyncPolicy.capture(
                preferences.personalSyncEnabled().get() && CommunityManager.hasPersonalIdentity(preferences.context),
                preferences.incognitoMode().get(),
            )
            database.execSQL("UPDATE community_capture SET enabled = ?", arrayOf(if (enabled) 1 else 0))
        }
    }

    fun stopBackgroundWork(context: Context) {
        if (BuildConfig.COMMUNITY_ENABLED) return
        runCatching { context.stopService(Intent(context, CommunityConnectionService::class.java)) }
            .onFailure { logcat(LogPriority.WARN, it) { "Unable to stop dormant community service" } }
        runCatching {
            val notifications = context.getSystemService(NotificationManager::class.java) ?: return@runCatching
            notifications.activeNotifications.filter {
                ownsNotificationChannel(it.notification.channelId)
            }.forEach { notifications.cancel(it.tag, it.id) }
        }.onFailure { logcat(LogPriority.WARN, it) { "Unable to clear dormant community notifications" } }
    }

    internal fun ownsNotificationChannel(channel: String?) =
        channel == "community_messages" || channel == "community_sync"
}

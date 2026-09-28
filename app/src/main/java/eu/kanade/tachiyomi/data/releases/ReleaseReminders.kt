package eu.kanade.tachiyomi.data.releases

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** One alarm for the nearest reminder, not an alarm per title or a network polling timer. */
object ReleaseReminders {
    const val CHANNEL = "release-reminders"
    private val mutex = Mutex()

    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun cancel(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(intent(context))

    suspend fun schedule(context: Context) = mutex.withLock {
        cancel(context)
        val preferences = ReleasePreferences()
        if (!preferences.enabled.get() ||
            !preferences.reminders.get() ||
            Injekt.get<BasePreferences>().incognitoMode().get()
        ) {
            return@withLock
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.release_reminder),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val store = ReleaseStore()
        val followed = store.monitoredIds(ReleaseMedium.ANIME).toSet()
        val now = System.currentTimeMillis()
        val events = AiringRepository().events().first().filter {
            it.remindedAt == 0L &&
                it.entryId in followed &&
                store.subscription(ReleaseMedium.ANIME, it.entryId).reminder &&
                ReleaseEligibility.source(ReleaseMedium.ANIME, it.entryId) != null
        }
        for (event in events.filter { it.airingAt <= now }) {
            // A missed reminder from an old backup/offline period must not produce a flood.
            if (now - event.airingAt <= 15 * ReleasePolicy.MINUTE && ReleaseNotifications.canPost(context, CHANNEL)) {
                val anime = Injekt.get<AnimeRepository>().getAnimeById(event.entryId)
                val seen = Injekt.get<EpisodeRepository>().getEpisodeByAnimeId(anime.id)
                    .any { it.seen && it.episodeNumber == event.episode.toDouble() }
                if (!seen) {
                    val hidden = Injekt.get<SecurityPreferences>().hideNotificationContent().get()
                    val notification = NotificationCompat.Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_ani)
                        .setContentTitle(if (hidden) context.getString(R.string.release_title) else anime.title)
                        .setContentText(
                            if (hidden) {
                                context.getString(R.string.release_reminder_description)
                            } else {
                                context.getString(R.string.release_broadcast_label, event.episode.toString())
                            },
                        )
                        .setContentIntent(
                            NotificationReceiver.openEpisodePendingActivity(
                                context,
                                anime,
                                Notifications.ID_NEW_EPISODES,
                            ),
                        )
                        .setAutoCancel(true).setOnlyAlertOnce(true).build()
                    NotificationManagerCompat.from(
                        context,
                    ).notify("airing-${event.entryId}", event.episode, notification)
                }
                AiringRepository().reminded(event)
            } else if (now - event.airingAt > 15 * ReleasePolicy.MINUTE) {
                AiringRepository().reminded(event)
            }
        }
        val next = events.firstOrNull { it.airingAt > now } ?: return@withLock
        val alarm = context.getSystemService(AlarmManager::class.java)
        try {
            if (exactAllowed(context)) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.airingAt, intent(context))
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.airingAt, intent(context))
            }
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.airingAt, intent(context))
        }
    }

    private fun intent(context: Context) = PendingIntent.getBroadcast(
        context,
        23061,
        Intent(context, ReleaseReminderReceiver::class.java).setAction("nyanime.release.REMIND"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class ReleaseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReleaseReminders.schedule(context.applicationContext)
                if (intent.action != "nyanime.release.REMIND") ReleaseMonitor.setup(context.applicationContext)
                ReleaseMonitor.enqueue(context.applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Recover from durable cache on the next foreground/periodic pass.
            } finally {
                pending.finish()
            }
        }
    }
}

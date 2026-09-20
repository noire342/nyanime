package eu.kanade.tachiyomi.data.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.watch.WatchTogetherActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** Keeps only the tiny room connection alive while browsing. Backgrounding still pauses playback. */
class WatchSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Insieme su Nyanime", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(ID, notification)
        }
        WatchTogetherManager.get(this).controller.state
            .map { it.active to it.message }.distinctUntilChanged().onEach {
                if (!it.first) stopSelf() else manager.notify(ID, notification())
            }.launchIn(scope)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) WatchTogetherManager.get(this).controller.leave()
        return START_NOT_STICKY
    }
    private fun notification(): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_play_arrow_24dp)
        .setContentTitle("Insieme su Nyanime")
        .setContentText(WatchTogetherManager.get(this).controller.state.value.message)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                ID,
                Intent(this, WatchTogetherActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOngoing(true).setOnlyAlertOnce(true)
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .addAction(
            R.drawable.ic_close_24dp,
            "Lascia la stanza",
            PendingIntent.getService(
                this,
                ID,
                Intent(this, WatchSessionService::class.java).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "watch_together"
        private const val ID = 4982
        private const val STOP = "nyanime.watch.STOP"
    }
}

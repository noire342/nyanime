package eu.kanade.tachiyomi.ui.tv

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

object TvDisplayRouter {
    fun availableDisplay(context: Context): Display? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)) {
            return null
        }
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
    }

    fun launch(activity: Activity, dedicated: Boolean) {
        val target = if (dedicated) availableDisplay(activity) else null
        activity.startActivity(
            Intent(
                activity,
                if (target == null) TvActivity::class.java else TvRemoteActivity::class.java,
            ),
        )
    }

    fun launchOnDisplay(activity: Activity, display: Display): Boolean = runCatching {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
        activity.startActivity(Intent(activity, TvActivity::class.java), options.toBundle())
        true
    }.getOrDefault(false)
}

/** Transient local remote target. No keys, media URLs or sessions are persisted. */
object TvRemoteBridge {
    private var tv = WeakReference<Activity>(null)
    private var player = WeakReference<Activity>(null)

    fun attachTv(activity: TvActivity) {
        tv = WeakReference(activity)
    }
    fun detachTv(activity: TvActivity) {
        if (tv.get() === activity) tv.clear()
    }
    fun attachPlayer(activity: Activity) {
        player = WeakReference(activity)
    }
    fun detachPlayer(activity: Activity) {
        if (player.get() === activity) player.clear()
    }

    fun send(keyCode: Int): Boolean {
        val target = player.get()?.takeUnless(Activity::isFinishing)
            ?: tv.get()?.takeUnless(Activity::isFinishing) ?: return false
        val now = android.os.SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        val view = target.window.decorView
        return view.dispatchKeyEvent(down).also { view.dispatchKeyEvent(up) }
    }

    fun closeTv() {
        tv.get()?.finish()
        tv.clear()
        player.clear()
    }
}

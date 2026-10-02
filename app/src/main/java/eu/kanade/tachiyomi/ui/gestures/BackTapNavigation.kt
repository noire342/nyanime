package eu.kanade.tachiyomi.ui.gestures

import android.content.Intent
import androidx.activity.ComponentActivity
import eu.kanade.tachiyomi.data.cast.CastController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.watch.WatchTogetherActivity

internal const val BACK_TAP_NAVIGATE = "nyanime.action.BACK_TAP_NAVIGATE"
internal const val BACK_TAP_DESTINATION = "destination"

internal fun performBackTapRemote(activity: ComponentActivity, action: BackTapAction): Boolean {
    val controller = CastController.get(activity)
    val state = controller.state.value
    if (!state.active || state.connecting) return false
    when (action) {
        BackTapAction.PlayPause -> controller.togglePause()
        BackTapAction.Forward, BackTapAction.Rewind -> {
            if (!state.playback.canSeek) return false
            controller.seek(state.playback.positionMs + if (action == BackTapAction.Forward) 10_000 else -10_000)
        }
        else -> return performBackTapNavigation(activity, action)
    }
    return true
}

internal fun performBackTapNavigation(activity: ComponentActivity, action: BackTapAction): Boolean {
    when (action) {
        BackTapAction.Back -> activity.onBackPressedDispatcher.onBackPressed()
        BackTapAction.Rooms -> activity.startActivity(Intent(activity, WatchTogetherActivity::class.java))
        BackTapAction.Search, BackTapAction.Library, BackTapAction.Releases -> activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .setAction(BACK_TAP_NAVIGATE)
                .putExtra(BACK_TAP_DESTINATION, action.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        else -> return false
    }
    return true
}

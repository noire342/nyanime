package eu.kanade.tachiyomi.ui.cast

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.data.cast.CastController
import eu.kanade.tachiyomi.ui.gestures.BackTapAction
import eu.kanade.tachiyomi.ui.gestures.BackTapContext
import eu.kanade.tachiyomi.ui.gestures.BackTapCoordinator
import eu.kanade.tachiyomi.ui.gestures.performBackTapRemote
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CastRemoteActivity : ComponentActivity() {
    private var backTapBinding: BackTapCoordinator.Binding? = null

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        backTapBinding?.focusChanged(hasFocus)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        backTapBinding?.touch(event)
        return super.dispatchTouchEvent(event)
    }

    private fun performBackTap(action: BackTapAction): Boolean {
        return performBackTapRemote(this, action)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val controller = CastController.get(this)
        if (controller.state.value.active &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            controller.adjustVolume(if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.05f else -0.05f)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backTapBinding = Injekt.get<BackTapCoordinator>().bind(
            this,
            { BackTapContext.Remote },
            { CastController.get(this).state.value.let { it.active && !it.connecting } },
            ::performBackTap,
        )
        enableEdgeToEdge()
        setContent { TachiyomiTheme { CastRemoteScreen(onBack = ::finish) } }
    }
}

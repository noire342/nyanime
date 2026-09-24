package eu.kanade.tachiyomi.ui.tv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.TvDisplayMode
import eu.kanade.tachiyomi.ui.main.MainActivity
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Default display becomes a dedicated local remote while the TV Activity owns the other display. */
class TvRemoteActivity : ComponentActivity(), DisplayManager.DisplayListener {
    private val manager by lazy { getSystemService(DISPLAY_SERVICE) as DisplayManager }
    private var targetId = Display.INVALID_DISPLAY
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        manager.registerDisplayListener(this, null)
        val display = TvDisplayRouter.availableDisplay(this)
        if (display == null || !TvDisplayRouter.launchOnDisplay(this, display)) {
            error = "Il secondo schermo non è disponibile su questo dispositivo."
        } else {
            targetId = display.displayId
        }
        setContent { TvRemoteScreen(error, ::mirror, ::close) }
    }

    private fun mirror() {
        Injekt.get<UiPreferences>().tvDisplayMode().set(TvDisplayMode.MIRROR)
        TvRemoteBridge.closeTv()
        startActivity(Intent(this, TvActivity::class.java))
        finish()
    }

    private fun close() {
        TvRemoteBridge.closeTv()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayChanged(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) {
        if (displayId == targetId) {
            targetId = Display.INVALID_DISPLAY
            TvRemoteBridge.closeTv()
            error = "Schermo scollegato. Puoi continuare in modalità Specchio."
        }
    }

    override fun onDestroy() {
        manager.unregisterDisplayListener(this)
        super.onDestroy()
    }
}

@Composable
private fun TvRemoteScreen(error: String?, mirror: () -> Unit, close: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(TvColors.background).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text("NYANIME", color = TvColors.accent, fontSize = 26.sp)
        Text("Telecomando", color = TvColors.text, fontSize = 24.sp)
        if (error != null) {
            Text(error, color = TvColors.muted, fontSize = 16.sp)
            TvAction("Passa a Specchio", mirror, Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(10.dp))
            remoteRow("↑" to KeyEvent.KEYCODE_DPAD_UP)
            remoteRow(
                "←" to KeyEvent.KEYCODE_DPAD_LEFT,
                "OK" to KeyEvent.KEYCODE_DPAD_CENTER,
                "→" to KeyEvent.KEYCODE_DPAD_RIGHT,
            )
            remoteRow("↓" to KeyEvent.KEYCODE_DPAD_DOWN)
            Spacer(Modifier.height(9.dp))
            remoteRow("Rosso" to KeyEvent.KEYCODE_PROG_RED, "Verde" to KeyEvent.KEYCODE_PROG_GREEN)
            remoteRow("Giallo" to KeyEvent.KEYCODE_PROG_YELLOW, "Blu" to KeyEvent.KEYCODE_PROG_BLUE)
            remoteRow(
                "▶" to KeyEvent.KEYCODE_MEDIA_PLAY,
                "Ⅱ" to KeyEvent.KEYCODE_MEDIA_PAUSE,
                "◀" to KeyEvent.KEYCODE_BACK,
            )
            for (row in 0..2) {
                remoteRow(
                    *(1..3).map { column ->
                        val digit = row * 3 + column
                        digit.toString() to (KeyEvent.KEYCODE_0 + digit)
                    }.toTypedArray(),
                )
            }
            remoteRow("0" to KeyEvent.KEYCODE_0)
        }
        Spacer(Modifier.weight(1f))
        TvAction("Chiudi modalità TV", close, Modifier.fillMaxWidth())
    }
}

@Composable
private fun remoteRow(vararg buttons: Pair<String, Int>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            8.dp,
            Alignment.CenterHorizontally,
        ),
    ) {
        buttons.forEach { (label, code) ->
            TvAction(
                label,
                { TvRemoteBridge.send(code) },
                Modifier.weight(1f),
                cue = when (code) {
                    KeyEvent.KEYCODE_PROG_RED -> TvColors.red
                    KeyEvent.KEYCODE_PROG_GREEN -> TvColors.green
                    KeyEvent.KEYCODE_PROG_YELLOW -> TvColors.yellow
                    KeyEvent.KEYCODE_PROG_BLUE -> TvColors.blue
                    else -> null
                },
            )
        }
    }
}

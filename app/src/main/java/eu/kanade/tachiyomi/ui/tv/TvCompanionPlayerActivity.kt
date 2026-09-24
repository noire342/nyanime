package eu.kanade.tachiyomi.ui.tv

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.databinding.PlayerLayoutBinding
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.lang.ref.WeakReference
import kotlin.math.roundToLong

data class TvCompanionPlaybackState(
    val mediaId: String = "",
    val title: String = "",
    val episode: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val paused: Boolean = true,
    val buffering: Boolean = false,
    val finished: Boolean = false,
    val canSeek: Boolean = false,
    val volume: Double = 1.0,
    val brightness: Double = 1.0,
    val subtitleIndex: Int = -1,
    val stopped: Boolean = true,
    val error: String = "",
) {
    fun json(): JsonObject = buildJsonObject {
        put("mediaId", mediaId)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("paused", paused)
        put("buffering", buffering)
        put("finished", finished)
        put("canSeek", canSeek)
        put("volume", volume)
        put("brightness", brightness)
        put("subtitleIndex", subtitleIndex)
        put("stopped", stopped)
        if (error.isNotEmpty()) put("error", error)
    }
}

internal object TvCompanionSessionRegistry {
    var bridge: TvCompanionPlaybackBridge? = null
}

/** Uses the app's existing libmpv decoder; phone owns media resolution and progress. */
class TvCompanionPlaybackBridge(private val tv: TvActivity) : TvCompanionPlayback {
    private val mutable = MutableStateFlow(TvCompanionPlaybackState())
    val state: StateFlow<TvCompanionPlaybackState> = mutable
    private var player = WeakReference<TvCompanionPlayerActivity>(null)
    private var pending: Pair<TvCompanionMedia, CompletableDeferred<Boolean>>? = null

    fun attach(activity: TvCompanionPlayerActivity) {
        player = WeakReference(activity)
        pending?.let { (media, ack) -> activity.load(media, ack) }
    }

    fun detach(activity: TvCompanionPlayerActivity) {
        if (player.get() === activity) player.clear()
        pending?.second?.complete(false)
        pending = null
    }

    fun report(state: TvCompanionPlaybackState) {
        mutable.value = state
    }

    override suspend fun execute(type: String, media: TvCompanionMedia?, value: JsonObject): Boolean {
        if (type == "load") {
            val selected = media ?: return false
            val ack = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                pending?.second?.complete(false)
                pending = selected to ack
                val current = player.get()?.takeUnless { it.isFinishing || it.isDestroyed }
                if (current != null) {
                    current.load(selected, ack)
                } else {
                    val intent = Intent(tv, TvCompanionPlayerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    val options = tv.display?.let { display ->
                        ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId).toBundle()
                    }
                    tv.startActivity(intent, options)
                }
            }
            return try {
                ack.await()
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (pending?.second === ack) pending = null
                }
            }
        }
        return withContext(Dispatchers.Main) {
            val current = player.get() ?: return@withContext false
            when (type) {
                "pause" -> current.pause(value.getValue("value").jsonPrimitive.content.toBoolean())
                "seek" -> current.seek(value.getValue("value").jsonPrimitive.content.toLong())
                "volume" -> current.volume(value.getValue("value").jsonPrimitive.content.toDouble())
                "brightness" -> current.brightness(value.getValue("value").jsonPrimitive.content.toDouble())
                "subtitle" -> current.subtitle(value.getValue("value").jsonPrimitive.content.toInt())
                "stop" -> {
                    current.stopAndFinish()
                    true
                }
                else -> false
            }
        }
    }

    override fun status(): JsonObject = mutable.value.json()

    override fun stop() {
        tv.runOnUiThread {
            player.get()?.stopAndFinish()
            mutable.value = TvCompanionPlaybackState()
        }
    }
}

class TvCompanionPlayerActivity : ComponentActivity(), MPVLib.EventObserver {
    private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }
    private val bridge get() = TvCompanionSessionRegistry.bridge
    private var media: TvCompanionMedia? = null
    private var pendingAck: CompletableDeferred<Boolean>? = null
    private var ticker: Job? = null
    private var visibleControls by mutableStateOf(true)
    private var brightnessLevel by mutableStateOf(1.0)
    private var volumeLevel = 1.0
    private var subtitleIndex = -1
    private var lastPosition = 0L
    private var lastDuration = 0L
    private var lastError = ""
    private var loading = false
    private var reachedEnd = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContentView(binding.root)
        val config = filesDir.resolve(PlayerActivity.MPV_DIR).apply { mkdirs() }
        binding.player.initialize(configDir = config.absolutePath, cacheDir = cacheDir.path, logLvl = "warn")
        MPVLib.addObserver(this)
        binding.controls.setContent { Controls() }
        TvRemoteBridge.attachPlayer(this)
        bridge?.attach(this) ?: finish()
        ticker = lifecycleScope.launch {
            while (isActive) {
                report()
                delay(500)
            }
        }
    }

    fun load(next: TvCompanionMedia, ack: CompletableDeferred<Boolean>) {
        pendingAck?.complete(false)
        pendingAck = ack
        media = next
        lastPosition = next.positionMs
        lastDuration = next.durationMs
        lastError = ""
        loading = true
        reachedEnd = false
        visibleControls = true
        val accepted = runCatching { MPVLib.command(arrayOf("loadfile", next.url, "replace")) }.isSuccess
        pendingAck?.complete(accepted)
        pendingAck = null
        if (!accepted) {
            loading = false
            lastError = "Impossibile aprire il video sullo schermo TV"
        }
        report(buffering = accepted)
    }

    fun pause(value: Boolean): Boolean = runCatching {
        MPVLib.setPropertyBoolean("pause", value)
        report()
        true
    }.getOrDefault(false)

    fun seek(value: Long): Boolean = runCatching {
        MPVLib.setPropertyDouble("time-pos", value.coerceAtLeast(0).toDouble() / 1000.0)
        lastPosition = value
        report()
        true
    }.getOrDefault(false)

    fun volume(value: Double): Boolean = runCatching {
        volumeLevel = value.coerceIn(0.0, 1.0)
        MPVLib.setPropertyDouble("volume", volumeLevel * 100.0)
        report()
        true
    }.getOrDefault(false)

    fun brightness(value: Double): Boolean {
        brightnessLevel = value.coerceIn(0.0, 1.0)
        report()
        return true
    }

    fun subtitle(index: Int): Boolean = runCatching {
        val selected = media?.subtitles.orEmpty()
        require(index in -1 until selected.size)
        subtitleIndex = index
        MPVLib.command(arrayOf("sub-remove", "all"))
        if (index >= 0) MPVLib.command(arrayOf("sub-add", selected[index].url, "select", selected[index].name))
        report()
        true
    }.getOrDefault(false)

    fun stopAndFinish() {
        pendingAck?.complete(false)
        pendingAck = null
        runCatching { MPVLib.command(arrayOf("stop")) }
        media = null
        loading = false
        bridge?.report(TvCompanionPlaybackState())
        finish()
    }

    private fun report(buffering: Boolean = false, finished: Boolean = false) {
        val current = media ?: return
        val position = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull()
            ?.takeIf { it.isFinite() && it >= 0 }?.times(1000)?.roundToLong() ?: lastPosition
        val duration = runCatching { MPVLib.getPropertyDouble("duration") }.getOrNull()
            ?.takeIf { it.isFinite() && it > 0 }?.times(1000)?.roundToLong() ?: lastDuration
        lastPosition = position
        lastDuration = duration
        bridge?.report(
            TvCompanionPlaybackState(
                current.id, current.title, current.episode,
                position, duration, MPVLib.getPropertyBoolean("pause") ?: false,
                buffering || loading || MPVLib.getPropertyBoolean("paused-for-cache") == true,
                finished || reachedEnd, duration > 0, volumeLevel, brightnessLevel, subtitleIndex,
                stopped = false, error = lastError,
            ),
        )
    }

    @androidx.compose.runtime.Composable
    private fun Controls() {
        val state by (bridge?.state ?: MutableStateFlow(TvCompanionPlaybackState())).collectAsState()
        Box(Modifier.fillMaxSize()) {
            if (brightnessLevel < 1.0) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = ((1.0 - brightnessLevel) * .75).toFloat())),
                )
            }
            if (visibleControls) {
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = .88f),
                                ),
                            ),
                        )
                        .padding(start = 40.dp, end = 40.dp, bottom = 30.dp, top = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(state.title, color = TvColors.text, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text(state.episode, color = TvColors.muted, fontSize = 18.sp)
                    Text(
                        "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                        color = TvColors.muted,
                        fontSize = 17.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvAction(
                            if (state.paused) "Riprendi" else "Pausa",
                            { pause(!state.paused) },
                            cue = TvColors.green,
                        )
                        TvAction("−10 s", { seek((state.positionMs - 10_000).coerceAtLeast(0)) })
                        TvAction("+10 s", { seek(state.positionMs + 10_000) })
                        TvAction("Torna alla Home", ::stopAndFinish)
                    }
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return "%02d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (!visibleControls) {
            visibleControls = true
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
        KeyEvent.KEYCODE_MENU -> {
            visibleControls = !visibleControls
            true
        }
        KeyEvent.KEYCODE_MEDIA_PLAY -> pause(false)
        KeyEvent.KEYCODE_MEDIA_PAUSE -> pause(true)
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_PROG_GREEN ->
            pause(!(MPVLib.getPropertyBoolean("pause") ?: false))
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> seek(lastPosition - 10_000)
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(lastPosition + 10_000)
        KeyEvent.KEYCODE_MEDIA_STOP -> {
            stopAndFinish()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun event(eventId: Int) {
        runOnUiThread {
            when (eventId) {
                MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                    loading = false
                    val start = media?.positionMs ?: 0
                    if (start > 0) seek(start)
                    MPVLib.setPropertyBoolean("pause", false)
                    pendingAck?.complete(true)
                    pendingAck = null
                    report()
                }
                MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {
                    loading = false
                    reachedEnd = true
                    report(finished = true)
                }
            }
        }
    }
    override fun efEvent(err: String?) {
        runOnUiThread {
            lastError = err?.take(180) ?: "Riproduzione non riuscita"
            loading = false
            pendingAck?.complete(false)
            pendingAck = null
            report()
        }
    }
    override fun eventProperty(property: String) = Unit
    override fun eventProperty(property: String, value: Long) = Unit
    override fun eventProperty(property: String, value: Boolean) = Unit
    override fun eventProperty(property: String, value: String) = Unit
    override fun eventProperty(property: String, value: Double) = Unit

    override fun onDestroy() {
        ticker?.cancel()
        TvRemoteBridge.detachPlayer(this)
        bridge?.detach(this)
        if (!isChangingConfigurations) bridge?.report(TvCompanionPlaybackState())
        MPVLib.removeObserver(this)
        binding.player.destroy()
        super.onDestroy()
    }
}

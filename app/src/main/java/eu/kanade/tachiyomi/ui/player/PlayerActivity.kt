/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Code is a mix between PlayerActivity from mpvKt and the former
 * PlayerActivity from Aniyomi.
 */

package eu.kanade.tachiyomi.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import com.hippo.unifile.UniFile
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.HttpServer
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.cast.CastController
import eu.kanade.tachiyomi.data.cast.CastHandoffPolicy
import eu.kanade.tachiyomi.data.cast.CastRequest
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import eu.kanade.tachiyomi.databinding.PlayerLayoutBinding
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.player.controls.PlayerControls
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils.Companion.getStringRes
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.seconds

class PlayerActivity : BaseActivity() {
    val castController by lazy { CastController.get(applicationContext) }

    fun castRequest(video: Video? = viewModel.currentVideo.value): CastRequest? {
        val anime = viewModel.currentAnime.value ?: return null
        val episode = viewModel.currentEpisode.value ?: return null
        val episodeId = episode.id ?: return null
        val media = castController.state.value.media?.takeIf { it.episodeId == episodeId }
        return CastRequest(
            anime.id, episodeId, anime.source, anime.title, episode.name, video ?: return null,
            CastHandoffPolicy.startPosition(
                remotePositionMs = media?.let { castController.state.value.playback.positionMs },
                loadingEpisode = viewModel.isLoadingEpisode.value,
                savedPositionMs = episode.last_second_seen,
                seen = episode.seen,
                preserveSeenPosition = playerPreferences.preserveWatchingPosition().get(),
                localPositionMs = (viewModel.pos.value * 1000).toLong(),
            ),
            when {
                media != null -> castController.state.value.playback.durationMs
                viewModel.isLoadingEpisode.value -> episode.total_seconds
                else -> (viewModel.duration.value * 1000).toLong()
            },
            viewModel.currentPlaylist.value.mapNotNull { it.id },
            playerPreferences.autoplayEnabled().get(),
        )
    }

    fun resumeAfterCast() {
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return
        onNewIntent(newIntent(this, anime.id, episode.id))
    }
    private val viewModel by viewModels<PlayerViewModel>(factoryProducer = { PlayerViewModelProviderFactory(this) })
    private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }
    private val playerObserver by lazy { PlayerObserver(this) }
    val player by lazy { binding.player }
    val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }
    val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var mediaSession: MediaSession? = null
    private val gesturePreferences: GesturePreferences by lazy { viewModel.gesturePreferences }
    private val playerPreferences: PlayerPreferences by lazy { viewModel.playerPreferences }
    private val audioPreferences: AudioPreferences = Injekt.get()
    private val advancedPlayerPreferences: AdvancedPlayerPreferences = Injekt.get()
    private val networkPreferences: NetworkPreferences = Injekt.get()
    private val storageManager: StorageManager = Injekt.get()
    private val torrentServerApi: TorrentServerApi = Injekt.get()
    private val torrentServerUtils: TorrentServerUtils = Injekt.get()
    private val torrentPreferences: TorrentPreferences = Injekt.get()

    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var restoreAudioFocus: () -> Unit = {}

    private var pipRect: Rect? = null
    val isPipSupportedAndEnabled by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            playerPreferences.enablePip().get()
    }

    private var pipReceiver: BroadcastReceiver? = null
    private var httpServer: HttpServer? = null
    private val anime4kShaderPipeline by lazy { Anime4KShaderPipeline(this) }
    private var anime4kSmartController: Anime4KSmartController? = null
    private var anime4kSmartNeedsMediaResolution = true
    private var anime4kSmartResolutionRetryCount = 0
    private var anime4kSmartRetryPending = false
    private var anime4kMediaResolutionGeneration = 0L
    private var anime4kTelemetryGeneration = 0L
    private var anime4kTelemetrySession: Anime4KTelemetrySession? = null
    private var anime4kCalibrationKey: String? = null
    private var anime4kCalibrationMediaInfo: Anime4KMediaInfo? = null
    private var anime4kLastSavedCalibration: Anime4KCalibration? = null
    private val anime4kMediaRefresh by lazy {
        Anime4KMediaRefresh(
            schedule = { delay, refresh ->
                binding.root.postDelayed({ if (!isFinishing && !isDestroyed && !player.isExiting) refresh() }, delay)
            },
            refresh = ::applySmartAnime4KForLoadedMedia,
        )
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        var initialized = false
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                viewModel.pause()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    companion object {
        fun newIntent(
            context: Context,
            animeId: Long?,
            episodeId: Long?,
            hostList: List<Hoster>? = null,
            hostIndex: Int? = null,
            vidIndex: Int? = null,
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra("animeId", animeId)
                putExtra("episodeId", episodeId)
                hostIndex?.let { putExtra("hostIndex", it) }
                vidIndex?.let { putExtra("vidIndex", it) }
                hostList?.let { putExtra("hostList", it.serialize()) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        internal const val MPV_DIR = "mpv"
        private const val MPV_FONTS_DIR = "fonts"
        private const val MPV_SCRIPTS_DIR = "scripts"
        private const val MPV_SCRIPTS_OPTS_DIR = "script-opts"
        private const val MPV_SHADERS_DIR = "shaders"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val animeId = intent.extras?.getLong("animeId") ?: -1
        val episodeId = intent.extras?.getLong("episodeId") ?: -1
        val hostList = intent.extras?.getString("hostList") ?: ""
        val hostIndex = intent.extras?.getInt("hostIndex") ?: -1
        val vidIndex = intent.extras?.getInt("vidIndex") ?: -1
        if (animeId == -1L || episodeId == -1L) {
            finish()
            return
        }
        NotificationReceiver.dismissNotification(
            this,
            animeId.hashCode(),
            Notifications.ID_NEW_EPISODES,
        )

        viewModel.saveCurrentEpisodeWatchingProgress()

        lifecycleScope.launchNonCancellable {
            viewModel.updateIsLoadingEpisode(true)
            viewModel.updateIsLoadingHosters(true)

            val initResult = viewModel.init(animeId, episodeId, hostList, hostIndex, vidIndex)
            if (!initResult.second.getOrDefault(false)) {
                val exception = initResult.second.exceptionOrNull() ?: IllegalStateException(
                    "Unknown error",
                )
                withUIContext {
                    setInitialEpisodeError(exception)
                }
            }

            viewModel.updateIsLoadingHosters(false)

            lifecycleScope.launch {
                viewModel.loadHosters(
                    source = viewModel.currentSource.value!!,
                    hosterList = initResult.first.hosterList ?: emptyList(),
                    hosterIndex = initResult.first.videoIndex.first,
                    videoIndex = initResult.first.videoIndex.second,
                )
            }
        }

        setIntent(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        registerSecureActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupPlayerMPV()
        setupPlayerAudio()
        setupMediaSession()
        setupPlayerOrientation()

        castController.state.distinctUntilChangedBy { it.active || it.connecting }.onEach { cast ->
            if (cast.active || cast.connecting) {
                viewModel.remoteProgressOwned = true
                mediaSession?.isActive = false
                player.paused = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }.launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    is PlayerViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is PlayerViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.seconds)
                    }
                    is PlayerViewModel.Event.SetArtResult -> {
                        onSetAsArtResult(event.result, event.artType)
                    }
                }
            }
            .launchIn(lifecycleScope)

        binding.controls.setContent {
            TachiyomiTheme {
                PlayerControls(
                    viewModel = viewModel,
                    onBackPress = {
                        if (isPipSupportedAndEnabled && player.paused == false && playerPreferences.pipOnExit().get()) {
                            enterPictureInPictureMode(createPipParams())
                        } else {
                            finish()
                        }
                    },
                    onToggleAnime4KSmart = ::toggleAnime4KSmart,
                    onToggleAnime4KMaximum = ::toggleAnime4KMaximum,
                    onSelectAnime4KCustom = ::selectAnime4KCustom,
                    modifier = Modifier.onGloballyPositioned {
                        pipRect = run {
                            val boundsInWindow = it.boundsInWindow()
                            Rect(
                                boundsInWindow.left.toInt(),
                                boundsInWindow.top.toInt(),
                                boundsInWindow.right.toInt(),
                                boundsInWindow.bottom.toInt(),
                            )
                        }
                    },
                )
            }
        }

        onNewIntent(this.intent)
    }

    override fun onDestroy() {
        player.isExiting = true

        httpServer?.stop()
        httpServer = null

        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
        }
        audioFocusRequest = null

        pipReceiver?.let {
            unregisterReceiver(it)
            pipReceiver = null
        }

        mediaSession?.let {
            it.isActive = false
            it.release()
        }

        if (noisyReceiver.initialized) {
            unregisterReceiver(noisyReceiver)
            noisyReceiver.initialized = false
        }

        MPVLib.removeLogObserver(playerObserver)
        MPVLib.removeObserver(playerObserver)
        player.destroy()

        super.onDestroy()
    }

    override fun onPause() {
        viewModel.saveCurrentEpisodeWatchingProgress()

        if (isInPictureInPictureMode) {
            super.onPause()
            return
        }

        player.isExiting = true
        if (isFinishing) {
            viewModel.deletePendingEpisodes()
            MPVLib.command(arrayOf("stop"))
        } else {
            viewModel.pause()
        }

        super.onPause()
    }

    override fun onStop() {
        window.attributes.screenBrightness.let {
            if (playerPreferences.rememberPlayerBrightness().get() && it != -1f) {
                playerPreferences.playerBrightnessValue().set(it)
            }
        }

        if (isInPictureInPictureMode && powerManager.isInteractive) {
            viewModel.deletePendingEpisodes()
        }

        super.onStop()
    }

    override fun onUserLeaveHint() {
        if (isPipSupportedAndEnabled && player.paused == false && playerPreferences.pipOnExit().get()) {
            enterPictureInPictureMode()
        }
        super.onUserLeaveHint()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isPipSupportedAndEnabled && player.paused == false && playerPreferences.pipOnExit().get()) {
            if (viewModel.sheetShown.value == Sheets.None &&
                viewModel.panelShown.value == Panels.None &&
                viewModel.dialogShown.value == Dialogs.None
            ) {
                enterPictureInPictureMode()
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        if (isPipSupportedAndEnabled) setPictureInPictureParams(createPipParams())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LOW_PROFILE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = if (playerPreferences.playerFullscreen().get()) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        if (playerPreferences.rememberPlayerBrightness().get()) {
            playerPreferences.playerBrightnessValue().get().let {
                if (it != -1f) viewModel.changeBrightnessTo(it)
            }
        }
    }

    private fun executeMPVCommand(commands: Array<String>) {
        if (!player.isExiting) {
            MPVLib.command(commands)
        }
    }

    private fun currentAnime4KEpisodeIds(): Pair<Long, Long>? {
        val animeId = viewModel.currentAnime.value?.id ?: return null
        val episodeId = viewModel.currentEpisode.value?.id ?: return null
        return animeId to episodeId
    }

    private fun anime4KRuntimeCalibrationKey(mediaInfo: Anime4KMediaInfo): String {
        return Anime4K.calibrationKey(
            mediaInfo = mediaInfo,
            renderer = getAnime4KPropertyString("gpu-renderer")
                ?: getAnime4KPropertyString("gpu-context")
                ?: Build.HARDWARE,
            api = getAnime4KPropertyString("gpu-api") ?: "unknown",
            driver = getAnime4KPropertyString("gpu-driver") ?: Build.VERSION.SDK_INT.toString(),
            hwdec = getAnime4KPropertyString("hwdec-current"),
            calibrationRevision = "${Anime4K.CALIBRATION_REVISION}:${BuildConfig.VERSION_CODE}",
        )
    }

    internal fun onAnime4KShaderError(message: String? = null) {
        runOnUiThread {
            if (player.isExiting) return@runOnUiThread

            val now = SystemClock.elapsedRealtime()

            val currentSelection = currentAnime4KSelection()
            if (currentSelection.profile == Anime4KProfile.Off || currentSelection.mode == Anime4KMode.Off) {
                return@runOnUiThread
            }

            if (currentSelection.profile == Anime4KProfile.Smart) {
                val controller = anime4kSmartController
                if (controller == null) {
                    applyAnime4KSelection(
                        profile = Anime4KProfile.Smart,
                        mode = Anime4KMode.Off,
                        resetSmartController = false,
                        persistEpisodeProfile = false,
                        fallbackToOffOnFailure = false,
                    )
                } else {
                    val adjustment = controller.onShaderError(
                        nowMillis = now,
                        shaderName = message?.let(Anime4K::shaderNameInError),
                    )
                    adjustment?.let { applySmartAdjustment(it, now) }
                }
                return@runOnUiThread
            }

            applyAnime4KSelection(Anime4KProfile.Off, Anime4KMode.Off)
            toast(AYMR.strings.pref_anime4k_fallback)
        }
    }

    internal fun toggleAnime4KSmart() {
        val mediaInfo = resolveAnime4KMediaInfo()
        val current = currentAnime4KSelection()
        if (current.profile == Anime4KProfile.Smart && anime4kSmartController?.isSuspended == true) {
            val now = SystemClock.elapsedRealtime()
            val controller = anime4kSmartController ?: return
            controller.resume(now)
            advancedPlayerPreferences.setAnime4kActiveSelection(
                Anime4KSelection(Anime4KProfile.Smart, Anime4KMode.Off),
            )
            advancedPlayerPreferences.anime4kProfile().set(Anime4KProfile.Smart)
            advancedPlayerPreferences.anime4kMode().set(Anime4KMode.Off)
            currentAnime4KEpisodeIds()?.let { (animeId, episodeId) ->
                advancedPlayerPreferences.saveAnime4kEpisodeProfile(
                    animeId,
                    episodeId,
                    Anime4KEpisodeProfile(Anime4KProfile.Smart),
                )
            }
            if (applySmartMode(controller, controller.currentMode, now)) {
                applySmartAnime4KForLoadedMedia()
            }
            return
        }

        val target = Anime4KSelectionRules.toggleSmart(
            current,
            mediaInfo?.let(Anime4K::resolveSmartMode) ?: Anime4KMode.ModeA,
        )
        if (target.profile == Anime4KProfile.Smart) {
            if (applyAnime4KSelection(
                    profile = Anime4KProfile.Smart,
                    mode = Anime4KMode.Off,
                    resetSmartController = true,
                    persistEpisodeProfile = true,
                    fallbackToOffOnFailure = false,
                )
            ) {
                applySmartAnime4KForLoadedMedia()
            }
        } else {
            applyAnime4KSelection(Anime4KProfile.Off, Anime4KMode.Off)
        }
    }

    internal fun toggleAnime4KMaximum() {
        val target = Anime4KSelectionRules.toggleMaximum(currentAnime4KSelection())
        applyAnime4KSelection(target.profile, target.mode)
    }

    internal fun selectAnime4KCustom(mode: Anime4KMode) {
        val target = Anime4KSelectionRules.selectCustom(currentAnime4KSelection(), mode)
        applyAnime4KSelection(target.profile, target.mode)
    }

    private fun currentAnime4KSelection(): Anime4KSelection =
        advancedPlayerPreferences.anime4kActiveSelection().value

    private fun applyAnime4KSelection(
        profile: Anime4KProfile,
        mode: Anime4KMode,
        resetSmartController: Boolean = true,
        persistEpisodeProfile: Boolean = true,
        fallbackToOffOnFailure: Boolean = true,
    ): Boolean {
        if (anime4kShaderPipeline.apply(mode)) {
            val selection = Anime4KSelection(profile, mode)
            advancedPlayerPreferences.setAnime4kActiveSelection(selection)
            advancedPlayerPreferences.anime4kProfile().set(profile)
            advancedPlayerPreferences.anime4kMode().set(mode)
            if (persistEpisodeProfile) {
                currentAnime4KEpisodeIds()?.let { (animeId, episodeId) ->
                    advancedPlayerPreferences.saveAnime4kEpisodeProfile(
                        animeId,
                        episodeId,
                        Anime4KEpisodeProfile(
                            profile = profile,
                            customMode = if (profile == Anime4KProfile.Custom) mode else Anime4KMode.Off,
                        ),
                    )
                }
            }
            advancedPlayerPreferences.setAnime4kDiagnostics(
                Anime4KSmartDiagnostics(
                    profile = profile,
                    mode = mode,
                ),
            )
            if (profile != Anime4KProfile.Smart) {
                resetAnime4KRuntime()
            } else if (resetSmartController) {
                resetAnime4KRuntime()
            } else {
                anime4kSmartNeedsMediaResolution = anime4kSmartController == null
                startAnime4KTelemetry()
            }
            return true
        }

        if (!fallbackToOffOnFailure) return false

        if (!anime4kShaderPipeline.apply(Anime4KMode.Off)) {
            toast(AYMR.strings.pref_anime4k_fallback)
            return false
        }
        advancedPlayerPreferences.setAnime4kActiveSelection(
            Anime4KSelection(Anime4KProfile.Off, Anime4KMode.Off),
        )
        advancedPlayerPreferences.anime4kProfile().set(Anime4KProfile.Off)
        advancedPlayerPreferences.anime4kMode().set(Anime4KMode.Off)
        if (persistEpisodeProfile) {
            currentAnime4KEpisodeIds()?.let { (animeId, episodeId) ->
                advancedPlayerPreferences.saveAnime4kEpisodeProfile(
                    animeId,
                    episodeId,
                    Anime4KEpisodeProfile(Anime4KProfile.Off),
                )
            }
        }
        resetAnime4KRuntime()
        toast(AYMR.strings.pref_anime4k_fallback)
        return false
    }

    private fun applyAnime4K() {
        // The episode profile is loaded after MPV has identified the current episode. Never
        // apply a previous episode's profile during MPV initialization.
        anime4kShaderPipeline.apply(Anime4KMode.Off)
        advancedPlayerPreferences.setAnime4kActiveSelection(
            Anime4KSelection(Anime4KProfile.Off, Anime4KMode.Off),
        )
        advancedPlayerPreferences.setAnime4kDiagnostics(Anime4KSmartDiagnostics())
        resetAnime4KRuntime()
    }

    private fun loadAnime4KForCurrentEpisode() {
        resetAnime4KRuntime()

        val ids = currentAnime4KEpisodeIds() ?: run {
            anime4kShaderPipeline.apply(Anime4KMode.Off)
            advancedPlayerPreferences.setAnime4kActiveSelection(
                Anime4KSelection(Anime4KProfile.Off, Anime4KMode.Off),
            )
            advancedPlayerPreferences.setAnime4kDiagnostics(Anime4KSmartDiagnostics())
            return
        }
        val profile = advancedPlayerPreferences.loadAnime4kEpisodeProfile(ids.first, ids.second)
        when (profile.profile) {
            Anime4KProfile.Off -> applyAnime4KSelection(
                Anime4KProfile.Off,
                Anime4KMode.Off,
                persistEpisodeProfile = false,
            )
            Anime4KProfile.Smart -> {
                if (!applyAnime4KSelection(
                        Anime4KProfile.Smart,
                        Anime4KMode.Off,
                        persistEpisodeProfile = false,
                        fallbackToOffOnFailure = false,
                    )
                ) {
                    return
                }
                applySmartAnime4KForLoadedMedia()
            }
            Anime4KProfile.Maximum -> applyAnime4KSelection(
                Anime4KProfile.Maximum,
                Anime4KMode.ModeAPlusHq,
                persistEpisodeProfile = false,
            )
            Anime4KProfile.Custom -> applyAnime4KSelection(
                Anime4KProfile.Custom,
                profile.customMode,
                persistEpisodeProfile = false,
            )
        }
    }

    private fun requestAnime4KMediaRefresh() {
        if (currentAnime4KSelection().profile == Anime4KProfile.Smart) anime4kMediaRefresh.request()
    }

    private fun applySmartAnime4KForLoadedMedia() {
        if (currentAnime4KSelection().profile != Anime4KProfile.Smart) return

        val mediaInfo = resolveAnime4KMediaInfo() ?: run {
            scheduleAnime4KMediaResolutionRetry()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val controller = anime4kSmartController
        if (
            controller == null ||
            anime4kSmartNeedsMediaResolution
        ) {
            val calibrationKey = anime4KRuntimeCalibrationKey(mediaInfo)
            val calibration = advancedPlayerPreferences.anime4kCalibration(calibrationKey)
            val newController = Anime4KSmartController(mediaInfo, now, calibration)
            anime4kSmartController = newController
            anime4kSmartNeedsMediaResolution = false
            anime4kSmartResolutionRetryCount = 0
            anime4kCalibrationKey = calibrationKey
            anime4kCalibrationMediaInfo = mediaInfo
            anime4kLastSavedCalibration = calibration
            if (applySmartMode(newController, newController.currentMode, now)) {
                logcat(LogPriority.INFO) {
                    "Anime4K Smart started at ${newController.currentMode.name} for " +
                        "${mediaInfo.sourceWidth}x${mediaInfo.sourceHeight} -> " +
                        "${mediaInfo.targetWidth}x${mediaInfo.targetHeight} @ " +
                        "${mediaInfo.framesPerSecond}fps"
                }
            }
            publishAnime4KSmartState(newController)
            return
        }

        if (anime4kCalibrationMediaInfo?.let { Anime4K.sameWorkload(it, mediaInfo) } != true) {
            anime4kCalibrationKey = anime4KRuntimeCalibrationKey(mediaInfo)
            anime4kCalibrationMediaInfo = mediaInfo
            anime4kLastSavedCalibration = null
        }

        controller.updateMediaInfo(mediaInfo, now)?.let { adjustment ->
            applySmartAdjustment(adjustment, now)
        }
        publishAnime4KSmartState(controller)
        saveAnime4KCalibrationIfChanged(controller)
    }

    private fun applySmartMode(
        controller: Anime4KSmartController,
        mode: Anime4KMode,
        nowMillis: Long,
    ): Boolean {
        var candidate = mode
        // Every failure removes a candidate. Even a broken installation terminates at Off.
        repeat(Anime4KMode.entries.size) {
            if (applyAnime4KSelection(
                    profile = Anime4KProfile.Smart,
                    mode = candidate,
                    resetSmartController = false,
                    persistEpisodeProfile = false,
                    fallbackToOffOnFailure = false,
                )
            ) {
                publishAnime4KSmartState(controller)
                saveAnime4KCalibrationIfChanged(controller)
                return candidate == mode
            }
            candidate = controller.onModeApplyFailure(nowMillis)?.mode ?: return forceSmartOff(controller)
        }
        return forceSmartOff(controller)
    }

    private fun applySmartAdjustment(
        adjustment: Anime4KSmartAdjustment,
        nowMillis: Long,
    ) {
        val controller = anime4kSmartController ?: return
        val previousMode = currentAnime4KSelection().mode
        if (adjustment.mode != previousMode) {
            applySmartMode(controller, adjustment.mode, nowMillis)
        }
        publishAnime4KSmartState(controller)
        saveAnime4KCalibrationIfChanged(controller)
        if (previousMode != adjustment.mode) {
            logcat(LogPriority.INFO) {
                "Anime4K Smart changed $previousMode to ${currentAnime4KSelection().mode.name}: ${adjustment.reason}"
            }
        }
    }

    private fun forceSmartOff(controller: Anime4KSmartController): Boolean {
        controller.suspend(SystemClock.elapsedRealtime())
        stopAnime4KTelemetry()
        if (!anime4kShaderPipeline.apply(Anime4KMode.Off)) {
            // MPV rejected even clearing our shaders; report the last applied selection honestly.
            advancedPlayerPreferences.setAnime4kDiagnostics(
                controller.diagnostics.copy(mode = currentAnime4KSelection().mode, reason = "shader update rejected"),
            )
            toast(AYMR.strings.pref_anime4k_fallback)
            return false
        }
        advancedPlayerPreferences.setAnime4kActiveSelection(
            Anime4KSelection(Anime4KProfile.Smart, Anime4KMode.Off),
        )
        advancedPlayerPreferences.anime4kProfile().set(Anime4KProfile.Smart)
        advancedPlayerPreferences.anime4kMode().set(Anime4KMode.Off)
        publishAnime4KSmartState(controller)
        saveAnime4KCalibrationIfChanged(controller)
        return false
    }

    private fun resetAnime4KRuntime() {
        anime4kMediaRefresh.reset()
        stopAnime4KTelemetry()
        anime4kSmartController = null
        anime4kSmartNeedsMediaResolution = true
        anime4kSmartResolutionRetryCount = 0
        anime4kSmartRetryPending = false
        anime4kMediaResolutionGeneration++
        anime4kCalibrationKey = null
        anime4kCalibrationMediaInfo = null
        anime4kLastSavedCalibration = null
    }

    private fun publishAnime4KSmartState(controller: Anime4KSmartController) {
        advancedPlayerPreferences.setAnime4kDiagnostics(
            controller.diagnostics.copy(mode = currentAnime4KSelection().mode),
        )
    }

    private fun saveAnime4KCalibrationIfChanged(controller: Anime4KSmartController) {
        if (!controller.hasStableMeasurement) return
        val key = anime4kCalibrationKey ?: return
        val calibration = controller.calibration
        if (calibration != anime4kLastSavedCalibration) {
            advancedPlayerPreferences.saveAnime4kCalibration(
                key,
                calibration.copy(updatedAtMillis = System.currentTimeMillis()),
            )
            anime4kLastSavedCalibration = calibration
        }
    }

    /** MPV can publish FILE_LOADED before video-params is available. Retry only briefly. */
    private fun scheduleAnime4KMediaResolutionRetry() {
        if (
            anime4kSmartRetryPending ||
            currentAnime4KSelection().profile != Anime4KProfile.Smart ||
            !anime4kSmartNeedsMediaResolution ||
            anime4kSmartResolutionRetryCount >= 4
        ) {
            return
        }

        val generation = anime4kMediaResolutionGeneration
        anime4kSmartResolutionRetryCount++
        anime4kSmartRetryPending = true
        binding.root.postDelayed({
            if (generation != anime4kMediaResolutionGeneration) return@postDelayed
            anime4kSmartRetryPending = false
            if (isFinishing || isDestroyed) return@postDelayed
            if (currentAnime4KSelection().profile == Anime4KProfile.Smart) {
                applySmartAnime4KForLoadedMedia()
            }
        }, anime4kSmartResolutionRetryCount * 250L)
    }

    private fun resolveAnime4KMediaInfo(): Anime4KMediaInfo? {
        // video-out-params describes the shader input after video filters, not the window.
        val parameters = if ((getAnime4KPropertyInt("video-out-params/w") ?: 0) >
            0
        ) {
            "video-out-params"
        } else {
            "video-params"
        }
        val geometry = Anime4KGeometry.resolve(
            sourceWidth = getAnime4KPropertyInt("$parameters/w") ?: return null,
            sourceHeight = getAnime4KPropertyInt("$parameters/h") ?: return null,
            rotation = getAnime4KPropertyInt("$parameters/rotate") ?: 0,
            displayAspect = getAnime4KPropertyDouble("$parameters/aspect"),
            osdWidth = getAnime4KPropertyInt("osd-dimensions/w"),
            osdHeight = getAnime4KPropertyInt("osd-dimensions/h"),
            marginLeft = getAnime4KPropertyInt("osd-dimensions/ml"),
            marginRight = getAnime4KPropertyInt("osd-dimensions/mr"),
            marginTop = getAnime4KPropertyInt("osd-dimensions/mt"),
            marginBottom = getAnime4KPropertyInt("osd-dimensions/mb"),
            surfaceWidth = player.width,
            surfaceHeight = player.height,
        ) ?: return null
        val containerFps = getAnime4KPropertyDouble("container-fps")
        val displayRefreshRate = getAnime4KPropertyDouble("display-fps")
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: getAnime4KPropertyDouble("estimated-display-fps")?.takeIf { it.isFinite() && it > 0.0 }

        return Anime4KMediaInfo(
            sourceWidth = geometry.sourceWidth,
            sourceHeight = geometry.sourceHeight,
            targetWidth = geometry.targetWidth,
            targetHeight = geometry.targetHeight,
            framesPerSecond = Anime4K.resolveFramesPerSecond(null, containerFps),
            displayRefreshRate = displayRefreshRate,
            frameRateKnown = containerFps?.let { it.isFinite() && it > 0.0 } == true,
            playbackSpeed = getAnime4KPropertyDouble("speed")
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: 1.0,
        )
    }

    private fun sampleAnime4KSmart() {
        if (currentAnime4KSelection().profile != Anime4KProfile.Smart) return
        val controller = anime4kSmartController ?: return
        if (controller.isSuspended) return
        val now = SystemClock.elapsedRealtime()
        if (anime4kTelemetrySession?.shouldSampleFallback(now) != true) return
        // Older/custom MPV builds may not load Lua. Drop counters still permit safe degradation,
        // but no measured GPU capacity is invented when render-pass timings are unavailable.
        acceptAnime4KSample(
            Anime4KPerformanceSample(
                timestampMillis = now,
                outputDroppedFrames = getAnime4KPropertyString("frame-drop-count")?.toLongOrNull(),
                decoderDroppedFrames = getAnime4KPropertyString("decoder-frame-drop-count")?.toLongOrNull(),
                delayedFrames = getAnime4KPropertyString("vo-delayed-frame-count")?.toLongOrNull(),
                renderTimeMillis = null,
                mistimedFrames = if (getAnime4KPropertyString("display-sync-active") == "yes") {
                    getAnime4KPropertyString("mistimed-frame-count")?.toLongOrNull()
                } else {
                    null
                },
                playing = player.paused == false &&
                    getAnime4KPropertyString("paused-for-cache") == "no" &&
                    getAnime4KPropertyString("seeking") == "no",
            ),
        )
    }

    private fun startAnime4KTelemetry() {
        if (anime4kSmartController?.isSuspended != false) {
            stopAnime4KTelemetry()
            return
        }
        val session = Anime4KTelemetrySession((++anime4kTelemetryGeneration).toString(), SystemClock.elapsedRealtime())
        anime4kTelemetrySession = session
        runCatching {
            MPVLib.command(
                arrayOf("script-message-to", Anime4KTelemetry.SCRIPT_NAME, "set-active", "yes", session.token),
            )
        }
    }

    private fun stopAnime4KTelemetry() {
        if (anime4kTelemetrySession == null) return
        anime4kTelemetrySession = null
        runCatching {
            MPVLib.command(arrayOf("script-message-to", Anime4KTelemetry.SCRIPT_NAME, "set-active", "no", ""))
        }
    }

    private fun acceptAnime4KTelemetry(value: String) {
        if (currentAnime4KSelection().profile != Anime4KProfile.Smart) return
        val telemetry = Anime4KTelemetry.parse(value) ?: return
        val sample = anime4kTelemetrySession?.accept(telemetry, SystemClock.elapsedRealtime()) ?: return
        acceptAnime4KSample(sample)
    }

    private fun acceptAnime4KSample(sample: Anime4KPerformanceSample) {
        val controller = anime4kSmartController ?: return
        controller.onSample(sample)?.let { adjustment ->
            applySmartAdjustment(adjustment, sample.timestampMillis)
        }
        publishAnime4KSmartState(controller)
        saveAnime4KCalibrationIfChanged(controller)
    }

    private fun getAnime4KPropertyInt(property: String): Int? =
        runCatching { MPVLib.getPropertyInt(property) }.getOrNull()

    private fun getAnime4KPropertyDouble(property: String): Double? =
        runCatching { MPVLib.getPropertyDouble(property) }.getOrNull()

    private fun getAnime4KPropertyString(property: String): String? =
        runCatching { MPVLib.getPropertyString(property) }.getOrNull()

    private fun UniFile.writeText(text: String) {
        this.openOutputStream().use {
            it.write(text.toByteArray())
        }
    }

    private fun setupPlayerMPV() {
        val logLevel = if (networkPreferences.verboseLogging().get()) "info" else "warn"

        val mpvDir = UniFile.fromFile(applicationContext.filesDir)!!.createDirectory(MPV_DIR)!!

        val mpvConfFile = mpvDir.createFile("mpv.conf")!!
        advancedPlayerPreferences.mpvConf().get().let { mpvConfFile.writeText(it) }
        val mpvInputFile = mpvDir.createFile("input.conf")!!
        advancedPlayerPreferences.mpvInput().get().let { mpvInputFile.writeText(it) }

        copyUserFiles(mpvDir)
        anime4kShaderPipeline.copyAssets()
        copyAssets(mpvDir)
        copyFontsDirectory(mpvDir)

        MPVLib.setOptionString("sub-ass-force-margins", "yes")
        MPVLib.setOptionString("sub-use-margins", "yes")

        player.initialize(
            configDir = mpvDir.filePath!!,
            cacheDir = applicationContext.cacheDir.path,
            logLvl = logLevel,
        )
        MPVLib.addLogObserver(playerObserver)
        MPVLib.addObserver(playerObserver)
        applyAnime4K()
    }

    private fun copyUserFiles(mpvDir: UniFile) {
        // First, delete all present scripts
        val scriptsDir = { mpvDir.createDirectory(MPV_SCRIPTS_DIR) }
        val scriptOptsDir = { mpvDir.createDirectory(MPV_SCRIPTS_OPTS_DIR) }
        val shadersDir = { mpvDir.createDirectory(MPV_SHADERS_DIR) }

        scriptsDir()?.delete()
        scriptOptsDir()?.delete()
        shadersDir()?.delete()

        // Then, copy the user files from the Aniyomi directory
        if (advancedPlayerPreferences.mpvUserFiles().get()) {
            storageManager.getScriptsDirectory()?.listFiles()?.forEach { file ->
                val outFile = scriptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
            storageManager.getScriptOptsDirectory()?.listFiles()?.forEach { file ->
                val outFile = scriptOptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
            storageManager.getShadersDirectory()?.listFiles()?.forEach { file ->
                val outFile = shadersDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
        }

        // Bundled bridges are refreshed on every initialization, independently of user scripts.
        for (filename in listOf("aniyomi.lua", "${Anime4KTelemetry.SCRIPT_NAME}.lua")) {
            scriptsDir()?.createFile(filename)?.openOutputStream()?.use { output ->
                assets.open(filename).use { input -> input.copyTo(output) }
            }
        }
    }

    private fun copyAssets(mpvDir: UniFile) {
        val assetManager = this.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        for (filename in files) {
            var ins: InputStream? = null
            var out: OutputStream? = null
            try {
                ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
                val outFile = mpvDir.createFile(filename)!!
                // Note that .available() officially returns an *estimated* number of bytes available
                // this is only true for generic streams, asset streams return the full file size
                if (outFile.length() == ins.available().toLong()) {
                    logcat(LogPriority.VERBOSE) { "Skipping copy of asset file (exists same size): $filename" }
                    continue
                }
                out = outFile.openOutputStream()
                ins.copyTo(out)
                logcat(LogPriority.WARN) { "Copied asset file: $filename" }
            } catch (e: IOException) {
                logcat(LogPriority.ERROR, e) { "Failed to copy asset file: $filename" }
            } finally {
                ins?.close()
                out?.close()
            }
        }
    }

    private fun copyFontsDirectory(mpvDir: UniFile) {
        // TODO: I think this is a bad hack.
        //  We need to find a way to let MPV directly access our fonts directory.
        CoroutineScope(Dispatchers.IO).launchIO {
            val fontsDirectory = mpvDir.createDirectory(MPV_FONTS_DIR)!!

            storageManager.getFontsDirectory()?.listFiles()?.forEach { font ->
                val outFile = fontsDirectory.createFile(font.name)
                outFile?.let {
                    font.openInputStream().copyTo(it.openOutputStream())
                }
            }

            MPVLib.setPropertyString("sub-fonts-dir", fontsDirectory.filePath!!)
            MPVLib.setPropertyString("osd-fonts-dir", fontsDirectory.filePath!!)
        }
    }

    fun setupCustomButtons(buttons: List<CustomButton>) {
        CoroutineScope(Dispatchers.IO).launchIO {
            val scriptsDir = {
                UniFile.fromFile(applicationContext.filesDir)
                    ?.createDirectory(MPV_DIR)
                    ?.createDirectory(MPV_SCRIPTS_DIR)
            }

            val primaryButtonId = viewModel.primaryButton.value?.id ?: 0L

            val customButtonsContent = buildString {
                append(
                    """
                        local lua_modules = mp.find_config_file('scripts')
                        if lua_modules then
                            package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua;' .. '${scriptsDir()!!.filePath}' .. '/?.lua'
                        end
                        local aniyomi = require 'aniyomi'
                    """.trimIndent(),
                )

                buttons.forEach { button ->
                    append(
                        """
                            ${button.getButtonOnStartup(primaryButtonId)}
                            function button${button.id}()
                                ${button.getButtonContent(primaryButtonId)}
                            end
                            mp.register_script_message('call_button_${button.id}', button${button.id})
                            function button${button.id}long()
                                ${button.getButtonLongPressContent(primaryButtonId)}
                            end
                            mp.register_script_message('call_button_${button.id}_long', button${button.id}long)
                        """.trimIndent(),
                    )
                }
            }

            val file = scriptsDir()?.createFile("custombuttons.lua")
            file?.openOutputStream()?.bufferedWriter()?.use {
                it.write(customButtonsContent)
            }

            file?.let {
                MPVLib.command(arrayOf("load-script", it.filePath))
            }
        }
    }

    private fun setupPlayerAudio() {
        with(audioPreferences) {
            applyAudioChannels(audioChannels().get())

            val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
                it.setAudioAttributes(
                    AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
                )
                it.setOnAudioFocusChangeListener(audioFocusChangeListener)
            }.build()
            AudioManagerCompat.requestAudioFocus(audioManager, request).let {
                if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
                audioFocusRequest = request
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
        when (it) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                val oldRestore = restoreAudioFocus
                val wasPlayerPaused = player.paused ?: false
                viewModel.pause()
                restoreAudioFocus = {
                    oldRestore()
                    if (!wasPlayerPaused) viewModel.unpause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", "0.5"))
                restoreAudioFocus = {
                    MPVLib.command(arrayOf("multiply", "volume", "2"))
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                restoreAudioFocus()
                restoreAudioFocus = {}
            }

            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                logcat(LogPriority.DEBUG) { "didn't get audio focus" }
            }
        }
    }

    override fun onResume() {
        if (!player.isExiting) {
            super.onResume()
            return
        }

        player.isExiting = false
        super.onResume()

        viewModel.currentVolume.update {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
                if (it < viewModel.maxVolume) viewModel.changeMPVVolumeTo(100)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            viewModel.changeVideoAspect(playerPreferences.aspectState().get())
        } else {
            viewModel.hideControls()
        }
        super.onConfigurationChanged(newConfig)
    }

    fun showToast(message: String) {
        runOnUiThread { toast(message) }
    }

    // A bunch of observers

    internal fun onObserverEvent(property: String, value: Long) {
        if (player.isExiting) return
        when (property) {
            "time-pos" -> {
                viewModel.updatePlayBackPos(value.toFloat())
                viewModel.setChapter(value.toFloat())
                sampleAnime4KSmart()
            }
            "demuxer-cache-time" -> viewModel.updateReadAhead(value = value)
            "volume" -> viewModel.setMPVVolume(value.toInt())
            "volume-max" -> viewModel.volumeBoostCap = value.toInt() - 100
            // "chapter" -> viewModel.updateChapter(value)
            "duration" -> viewModel.duration.update { value.toFloat() }
            "user-data/current-anime/intro-length" -> viewModel.setAnimeSkipIntroLength(value)
        }
    }

    internal fun onObserverEvent(property: String) {
        if (player.isExiting) return
        when (property) {
            "chapter-list" -> {
                viewModel.loadChapters()
                viewModel.updateChapter(0)
            }
            "track-list" -> viewModel.loadTracks()
            "osd-dimensions" -> requestAnime4KMediaRefresh()
        }
    }

    internal fun onObserverEvent(property: String, value: Boolean) {
        if (player.isExiting) return
        if (property in listOf("pause", "paused-for-cache", "seeking")) {
            anime4kSmartController?.resetTelemetry(SystemClock.elapsedRealtime())
        }
        when (property) {
            "pause" -> {
                if (value && player.paused == true) {
                    viewModel.pause()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else if (!value && player.paused == false) {
                    viewModel.unpause()
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                runCatching {
                    setPictureInPictureParams(createPipParams())
                }
            }

            "paused-for-cache" -> {
                viewModel.isLoading.update { value }
            }

            "seeking" -> {
                viewModel.isLoading.update { value }
            }

            "eof-reached" -> {
                endFile(value)
            }
        }
    }

    val trackId: (String) -> Int? = {
        when (it) {
            "auto" -> null
            "no" -> -1
            else -> it.toInt()
        }
    }

    internal fun onObserverEvent(property: String, value: String) {
        if (player.isExiting) return
        if (property == Anime4KTelemetry.PROPERTY) {
            acceptAnime4KTelemetry(value)
            return
        }
        when (property.substringBeforeLast("/")) {
            "aid" -> trackId(value)?.let { viewModel.updateAudio(it) }
            "sid" -> trackId(value)?.let { viewModel.updateSubtitle(it, viewModel.selectedSubtitles.value.second) }
            "secondary-sid" -> trackId(value)?.let {
                viewModel.updateSubtitle(viewModel.selectedSubtitles.value.first, it)
            }
            "hwdec", "hwdec-current" -> {
                viewModel.getDecoder()
                anime4kCalibrationMediaInfo = null
                anime4kSmartController?.invalidateCalibration(SystemClock.elapsedRealtime())
                applySmartAnime4KForLoadedMedia()
            }
            "user-data/aniyomi" -> viewModel.handleLuaInvocation(property, value)
        }
    }

    @SuppressLint("NewApi")
    internal fun onObserverEvent(property: String, value: Double) {
        if (player.isExiting) return
        when (property) {
            "speed" -> {
                viewModel.playbackSpeed.update { value.toFloat() }
                anime4kSmartController?.resetTelemetry(SystemClock.elapsedRealtime())
                requestAnime4KMediaRefresh()
            }
            "video-params/aspect" -> if (isPipSupportedAndEnabled) setPictureInPictureParams(createPipParams())
            "container-fps",
            "display-fps",
            "estimated-display-fps",
            -> requestAnime4KMediaRefresh()
        }
    }

    internal fun event(eventId: Int) {
        if (player.isExiting) return
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                loadAnime4KForCurrentEpisode()
                viewModel.viewModelScope.launchIO { fileLoaded() }
            }
            MPVLib.mpvEventId.MPV_EVENT_VIDEO_RECONFIG -> requestAnime4KMediaRefresh()
            MPVLib.mpvEventId.MPV_EVENT_SEEK -> {
                anime4kSmartController?.resetTelemetry(SystemClock.elapsedRealtime())
                viewModel.isLoading.update { true }
            }
            MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> player.isExiting = false
        }
    }

    fun createPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        val paused = if (player.isExiting) true else player.paused ?: true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val anime = viewModel.currentAnime.value
            val episode = viewModel.currentEpisode.value

            if (anime != null && episode != null) {
                builder.setTitle(anime.title).setSubtitle(episode.name)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = playerPreferences.pipOnExit().get()
            builder.setAutoEnterEnabled(!paused && autoEnter)
            builder.setSeamlessResizeEnabled(!paused && autoEnter)
        }
        builder.setActions(
            createPipActions(
                context = this,
                isPaused = paused,
                replaceWithPrevious = playerPreferences.pipReplaceWithPrevious().get(),
                playlistCount = viewModel.currentPlaylist.value.size,
                playlistPosition = viewModel.getCurrentEpisodeIndex(),
            ),
        )
        pipRect?.takeUnless { it.isEmpty }?.let(builder::setSourceRectHint)
        // MPV can expose height before aspect, or clear aspect during a transition.
        // Missing geometry is valid: omit it and let Android use its default.
        val aspect = if (player.isExiting) null else player.getVideoOutAspect()
        PipVideoGeometry.fromAspect(aspect)?.let {
            builder.setAspectRatio(Rational(it.width, it.height))
        }
        return builder.build()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            pipReceiver?.let {
                unregisterReceiver(pipReceiver)
                pipReceiver = null
            }
        } else {
            setPictureInPictureParams(createPipParams())
            viewModel.hideControls()
            viewModel.hideSeekBar()
            viewModel.isBrightnessSliderShown.update { false }
            viewModel.isVolumeSliderShown.update { false }
            viewModel.sheetShown.update { Sheets.None }
            if (pipReceiver != null) {
                super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
                return
            }
            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || intent.action != PIP_INTENTS_FILTER) return
                    when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
                        PIP_PAUSE -> viewModel.pause()
                        PIP_PLAY -> viewModel.unpause()
                        PIP_NEXT -> viewModel.changeEpisode(false)
                        PIP_PREVIOUS -> viewModel.changeEpisode(true)
                        PIP_SKIP -> viewModel.seekBy(10)
                    }
                    setPictureInPictureParams(createPipParams())
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
            }
        }

        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun setupPlayerOrientation() {
        if (player.isExiting) return
        requestedOrientation = when (playerPreferences.defaultPlayerOrientationType().get()) {
            PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            PlayerOrientation.Video -> if ((player.getVideoOutAspect() ?: 0.0) > 1.0) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (castController.state.value.active &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            castController.adjustVolume(if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.05f else -0.05f)
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.changeVolumeBy(1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.changeVolumeBy(-1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleRightDoubleTap()
            KeyEvent.KEYCODE_SPACE -> viewModel.pauseUnpause()
            KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

            KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

            // other keys should be bound by the user in input.conf ig
            else -> {
                event?.let { player.onKey(it) }
                super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (player.onKey(event!!)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun setupMediaSession() {
        val previousAction = gesturePreferences.mediaPreviousGesture().get()
        val playAction = gesturePreferences.mediaPlayPauseGesture().get()
        val nextAction = gesturePreferences.mediaNextGesture().get()

        mediaSession = MediaSession(this, "PlayerActivity").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPlay()
                                viewModel.unpause()
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPlay.keyCode))
                            }

                            SingleActionGesture.Switch -> {}
                        }
                    }

                    override fun onPause() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPause()
                                viewModel.pause()
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPlay.keyCode))
                            }

                            SingleActionGesture.Switch -> {}
                        }
                    }

                    override fun onSkipToPrevious() {
                        when (previousAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.leftSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPrevious.keyCode))
                            }

                            SingleActionGesture.Switch -> viewModel.changeEpisode(true)
                        }
                    }

                    override fun onSkipToNext() {
                        when (nextAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.rightSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaNext.keyCode))
                            }

                            SingleActionGesture.Switch -> viewModel.changeEpisode(false)
                        }
                    }

                    override fun onStop() {
                        super.onStop()
                        isActive = false
                        this@PlayerActivity.onStop()
                    }
                },
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_STOP or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_SKIP_TO_NEXT,
                    )
                    .build(),
            )
            isActive = true
        }

        val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
        registerReceiver(noisyReceiver, filter)
        noisyReceiver.initialized = true
    }

    // ==== END MPVKT ====

    override fun onSaveInstanceState(outState: Bundle) {
        if (!isChangingConfigurations) {
            viewModel.onSaveInstanceStateNonConfigurationChange()
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * Switches to the episode based on [episodeId],
     * @param episodeId id of the episode to switch the player to
     * @param autoPlay whether the episode is switching due to auto play
     */
    internal fun changeEpisode(episodeId: Long?, autoPlay: Boolean = false) {
        viewModel.sheetShown.update { _ -> Sheets.None }
        viewModel.panelShown.update { _ -> Panels.None }
        viewModel.pause()
        viewModel.isLoading.update { _ -> true }
        viewModel.resetState()

        lifecycleScope.launch {
            viewModel.updateIsLoadingEpisode(true)
            viewModel.updateIsLoadingHosters(true)
            viewModel.cancelHosterVideoLinksJob()

            val pipEpisodeToasts = playerPreferences.pipEpisodeToasts().get()
            val switchMethod = viewModel.loadEpisode(episodeId)

            viewModel.updateIsLoadingHosters(false)

            when (switchMethod) {
                null -> {
                    if (viewModel.currentAnime.value != null && !autoPlay) {
                        launchUI { toast(AYMR.strings.no_next_episode) }
                    }
                    viewModel.isLoading.update { _ -> false }
                }

                else -> {
                    if (switchMethod.hosterList != null) {
                        when {
                            switchMethod.hosterList.isEmpty() -> setInitialEpisodeError(
                                PlayerViewModel.ExceptionWithStringResource(
                                    "Hoster list is empty",
                                    AYMR.strings.no_hosters,
                                ),
                            )
                            else -> {
                                viewModel.loadHosters(
                                    source = switchMethod.source,
                                    hosterList = switchMethod.hosterList,
                                    hosterIndex = -1,
                                    videoIndex = -1,
                                )
                            }
                        }
                    } else {
                        logcat(LogPriority.ERROR) { "Error getting links" }
                    }

                    if (isInPictureInPictureMode && pipEpisodeToasts) {
                        launchUI { toast(switchMethod.episodeTitle) }
                    }
                }
            }
        }

        viewModel.updateHasPreviousEpisode(
            viewModel.getCurrentEpisodeIndex() != 0,
        )
        viewModel.updateHasNextEpisode(
            viewModel.getCurrentEpisodeIndex() != viewModel.currentPlaylist.value.size - 1,
        )
    }

    fun setVideo(video: Video?, position: Long? = null) {
        if (player.isExiting) return
        if (video == null) return
        if (castController.state.value.active || castController.state.value.connecting) {
            lifecycleScope.launch {
                castRequest(video)?.let { castController.play(it) }
                viewModel.updateIsLoadingEpisode(false)
            }
            return
        }
        httpServer?.stop()
        httpServer = null

        setHttpOptions(video)

        if (viewModel.isLoadingEpisode.value) {
            viewModel.currentEpisode.value?.let { episode ->
                val preservePos = playerPreferences.preserveWatchingPosition().get()
                val resumePosition = castController.takePhoneResume(episode.id ?: -1) ?: position
                    ?: if (episode.seen && !preservePos) {
                        0L
                    } else {
                        episode.last_second_seen
                    }
                MPVLib.command(arrayOf("set", "start", "${resumePosition / 1000F}"))
            }
        } else {
            player.timePos?.let {
                MPVLib.command(arrayOf("set", "start", "${player.timePos}"))
            }
        }

        val videoOptions = video.mpvArgs.joinToString(",") { (option, value) ->
            "$option=\"$value\""
        }

        if (torrentPreferences.torrServerEnable().get() &&
            (
                video.videoUrl.startsWith(torrentServerApi.hostUrl) ||
                    video.videoUrl.startsWith("magnet") ||
                    video.videoUrl.endsWith("torrent")
                )
        ) {
            launchIO {
                TorrentServerService.start()
                torrentLinkHandler(video.videoUrl, video.videoTitle, videoOptions)
            }
        } else {
            launchIO {
                val httpSource = viewModel.currentSource.value as? AnimeHttpSource
                var videoUrl: String = video.videoUrl
                if (video.usesHttpServer() && httpSource != null) {
                    val port = try {
                        httpServer = httpSource.createHttpServer()
                        httpServer?.start()
                        httpServer?.listeningPort ?: 0
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to start http server" }
                        launchUI {
                            toast(AYMR.strings.http_server_start_failure)
                        }
                        return@launchIO
                    }

                    val newVideo = video.copyHttpServer(port)
                    videoUrl = newVideo.videoUrl
                    viewModel.updateVideo(newVideo)
                }

                MPVLib.command(
                    arrayOf(
                        "loadfile",
                        parseVideoUrl(videoUrl),
                        "replace",
                        "0",
                        videoOptions,
                    ),
                )
            }
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the videos of the episode. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialEpisodeError(error: Throwable) {
        if (error is PlayerViewModel.ExceptionWithStringResource) {
            toast(error.stringResource)
        } else {
            toast(error.message)
        }
        logcat(LogPriority.ERROR, error)
        finish()
    }

    private suspend fun torrentLinkHandler(videoUrl: String, title: String, videoOptions: String) {
        var index = 0

        // check if link is from localSource
        if (videoUrl.startsWith("content://")) {
            val videoInputStream = applicationContext.contentResolver.openInputStream(videoUrl.toUri())
            val torrent = torrentServerApi.uploadTorrent(videoInputStream!!, title, false)
            val torrentUrl = torrentServerUtils.getTorrentPlayLink(torrent, 0)

            MPVLib.command(
                arrayOf(
                    "loadfile",
                    torrentUrl,
                    "replace",
                    "0",
                    videoOptions,
                ),
            )
            return
        }

        // check if link is from magnet, in that check if index is present
        if (videoUrl.startsWith("magnet")) {
            if (videoUrl.contains("index=")) {
                index = try {
                    videoUrl.substringAfter("index=").substringBefore("&").toInt()
                } catch (_: NumberFormatException) {
                    0
                }
            }
        }

        val currentTorrent = torrentServerApi.addTorrent(videoUrl, title, "", "", false)
        val videoTorrentUrl = torrentServerUtils.getTorrentPlayLink(currentTorrent, index)

        MPVLib.command(
            arrayOf(
                "loadfile",
                videoTorrentUrl,
                "replace",
                "0",
                videoOptions,
            ),
        )
    }

    fun parseVideoUrl(videoUrl: String?): String? {
        return videoUrl?.toUri()?.resolveUri(this)
            ?: videoUrl
    }

    fun setHttpOptions(video: Video) {
        if (viewModel.isEpisodeOnline() != true) return
        val source = viewModel.currentSource.value as? AnimeHttpSource ?: return

        val headers = (video.headers ?: source.headers)
            .toMultimap()
            .mapValues { it.value.firstOrNull() ?: "" }
            .toMutableMap()

        val httpHeaderString = headers.map {
            it.key + ": " + it.value.replace(",", "\\,")
        }.joinToString(",")

        MPVLib.setOptionString("http-header-fields", httpHeaderString)

        // need to fix the cache
        // MPVLib.setOptionString("cache-on-disk", "yes")
        // val cacheDir = File(applicationContext.filesDir, "media").path
        // MPVLib.setOptionString("cache-dir", cacheDir)
    }

    /**
     * Called from the presenter when a screenshot is ready to be shared. It shows Android's
     * default sharing tool.
     */
    private fun onShareImageResult(uri: Uri, seconds: String) {
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(AYMR.strings.share_screenshot_info, anime.title, episode.name, seconds),
        )
        startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
    }

    /**
     * Called from the presenter when a screenshot is saved or fails. It shows a message
     * or logs the event depending on the [result].
     */
    private fun onSaveImageResult(result: PlayerViewModel.SaveImageResult) {
        when (result) {
            is PlayerViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is PlayerViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a screenshot is set as art or fails.
     * It shows a different message depending on the [result].
     */
    private fun onSetAsArtResult(result: SetAsArt, artType: ArtType) {
        toast(
            when (result) {
                SetAsArt.Success ->
                    when (artType) {
                        ArtType.Cover -> MR.strings.cover_updated
                        ArtType.Background -> AYMR.strings.background_updated
                        ArtType.Thumbnail -> AYMR.strings.thumbnail_updated
                    }
                SetAsArt.AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                SetAsArt.Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    // TODO: exception java.util.ConcurrentModificationException:
    //  UPDATE: MAY HAVE BEEN FIXED
    // at java.lang.Object java.util.ArrayList$Itr.next() (ArrayList.java:860)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.fileLoaded() (PlayerActivity.kt:1874)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.event(int) (PlayerActivity.kt:1566)
    // at void is.xyz.mpv.MPVLib.event(int) (MPVLib.java:86)
    private fun fileLoaded() {
        if (player.isExiting) return
        if (castController.state.value.active || castController.state.value.connecting) {
            player.paused = true
            return
        }
        viewModel.remoteProgressOwned = false
        mediaSession?.isActive = true
        setMpvOptions()
        setMpvMediaTitle()
        setupPlayerOrientation()
        setupChapters()
        setupTracks()

        // aniSkip stuff
        viewModel.waitingSkipIntro = playerPreferences.waitingTimeIntroSkip().get()
        runBlocking {
            if (
                viewModel.introSkipEnabled &&
                playerPreferences.aniSkipEnabled().get() &&
                !(playerPreferences.disableAniSkipOnChapters().get() && viewModel.chapters.value.isNotEmpty())
            ) {
                viewModel.aniSkipResponse(player.duration)?.let {
                    viewModel.updateChapters(
                        ChapterUtils.mergeChapters(
                            currentChapters = viewModel.chapters.value,
                            stamps = it,
                            duration = player.duration,
                        ),
                    )
                    viewModel.setChapter(viewModel.pos.value)
                }
            }
        }
    }

    private fun setMpvOptions() {
        if (player.isExiting) return
        val video = viewModel.currentVideo.value ?: return

        // Only check for `MPV_ARGS_TAG` on downloaded videos
        if (listOf("file", "content", "data").none { video.videoUrl.startsWith(it) }) {
            return
        }

        try {
            val metadata = Json.decodeFromString<Map<String, String>>(
                MPVLib.getPropertyString("metadata"),
            )

            val opts = metadata[Video.MPV_ARGS_TAG]
                ?.split(";")
                ?.map { it.split("=", limit = 2) }
                ?: return

            opts.forEach { (option, value) ->
                MPVLib.setPropertyString(option, value)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to read video metadata" }
        }
    }

    private fun setupTracks() {
        if (player.isExiting) return
        viewModel.isLoadingTracks.update { _ -> true }

        val audioTracks = viewModel.currentVideo.value?.audioTracks?.takeIf { it.isNotEmpty() }
        val subtitleTracks = viewModel.currentVideo.value?.subtitleTracks?.takeIf { it.isNotEmpty() }

        // If no external audio or subtitle tracks are present, loadTracks() won't be
        // called and we need to call onFinishLoadingTracks() manually
        if (audioTracks == null && subtitleTracks == null) {
            viewModel.onFinishLoadingTracks()
            return
        }

        audioTracks?.forEach { audio ->
            executeMPVCommand(arrayOf("audio-add", audio.url, "auto", audio.lang))
        }
        subtitleTracks?.forEach { sub ->
            executeMPVCommand(arrayOf("sub-add", sub.url, "auto", sub.lang))
        }

        viewModel.isLoadingTracks.update { _ -> false }
    }

    private fun setupChapters() {
        if (player.isExiting) return

        val timestamps = viewModel.currentVideo.value?.timestamps?.takeIf { it.isNotEmpty() }
            ?.map { timestamp ->
                if (timestamp.name.isEmpty() && timestamp.type != ChapterType.Other) {
                    timestamp.copy(
                        name = timestamp.type.getStringRes()?.let(::stringResource) ?: "",
                    )
                } else {
                    timestamp
                }
            }
            ?: return

        viewModel.updateChapters(
            ChapterUtils.mergeChapters(
                currentChapters = viewModel.chapters.value,
                stamps = timestamps,
                duration = player.duration,
            ),
        )
        viewModel.setChapter(viewModel.pos.value)
    }

    private fun setMpvMediaTitle() {
        if (player.isExiting) return
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return

        // Write to mpv table
        MPVLib.setPropertyString("user-data/current-anime/episode-title", episode.name)

        val epNumber = episode.episode_number.let { number ->
            if (ceil(number) == floor(number)) number.toInt() else number
        }.toString().padStart(2, '0')

        val title = stringResource(
            AYMR.strings.mpv_media_title,
            anime.title,
            epNumber,
            episode.name,
        )

        MPVLib.setPropertyString("force-media-title", title)
    }

    private fun endFile(eofReached: Boolean) {
        if (eofReached && playerPreferences.autoplayEnabled().get()) {
            viewModel.changeEpisode(previous = false, autoPlay = true)
        }
    }
}

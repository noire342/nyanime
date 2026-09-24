package eu.kanade.tachiyomi.ui.tv

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.TvUiMode
import eu.kanade.tachiyomi.data.watch.WatchRoomVideoLauncher
import eu.kanade.tachiyomi.data.watch.WatchTogetherManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.watch.WatchTogetherActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TvActivity : ComponentActivity(), WatchRoomVideoLauncher {
    private lateinit var ui: TvUiController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = Injekt.get<UiPreferences>()
        if (!TvModeResolver.useTv(this, preferences.tvUiMode().get())) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        ui = TvUiController(this, lifecycleScope)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    ui.back()
                }
            },
        )
        TvRemoteBridge.attachTv(this)
        setContent { TvRoot(ui) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (::ui.isInitialized) {
            // Android dispatches BACK through OnBackPressedDispatcher on key up.
            // Handling key down as well would pop two TV screens for one press.
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) return ui.back()
            ui.noteRemoteActivity()
            if (ui.onKey(keyCode)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        WatchTogetherManager.get(this).present(this)
        if (::ui.isInitialized) ui.catalog.revalidate()
    }

    override fun launchWatchRoomVideo(animeId: Long, episodeId: Long) {
        if (::ui.isInitialized) ui.playIds(animeId, episodeId)
    }

    override fun onDestroy() {
        if (::ui.isInitialized) ui.close()
        TvRemoteBridge.detachTv(this)
        super.onDestroy()
    }
}

enum class TvScreen { PICKER, HOME, SEARCH, DETAIL, SETTINGS, PROFILES, ROOMS, COMPANION }
enum class TvHomeGreenAction { RESUME, HERO, NONE }
data class TvScrollRequest(val id: Int, val screen: TvScreen, val key: String, val index: Int)

/** Screen transitions and remote actions share one owner, separate from each renderer. */
class TvUiController(
    private val activity: TvActivity,
    private val scope: CoroutineScope,
) {
    val profileStore = TvProfileRepository.get(activity)
    val catalog = TvCatalogController(scope, profileStore)
    val watch = WatchTogetherManager.get(activity)
    val companionPlayback = TvCompanionPlaybackBridge(activity)
    val companion = TvCompanionReceiver(activity, scope, companionPlayback)
    val preferences: UiPreferences = Injekt.get()
    private val scrollStates = mutableMapOf<String, LazyListState>()
    val selectedSections = mutableStateMapOf<String, String>()
    fun scrollState(key: String): LazyListState = scrollStates.getOrPut(key) { LazyListState() }
    private var nextScrollRequestId = 0
    var scrollRequest by androidx.compose.runtime.mutableStateOf<TvScrollRequest?>(null)
        private set
    fun requestScroll(key: String, index: Int) {
        scrollRequest = TvScrollRequest(++nextScrollRequestId, screen, key, index)
    }
    fun finishScrollRequest(id: Int) {
        if (scrollRequest?.id == id) scrollRequest = null
    }
    var screen by androidx.compose.runtime.mutableStateOf(TvScreen.PICKER)
        private set
    private val backStack = ArrayDeque<TvScreen>()
    private fun navigate(target: TvScreen) {
        if (screen == target) return
        backStack.addLast(screen)
        screen = target
    }
    var profiles by androidx.compose.runtime.mutableStateOf<List<TvProfile>>(emptyList())
        private set
    var activeProfiles by androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
        private set
    var teamSelection by androidx.compose.runtime.mutableStateOf<List<String>?>(null)
        private set
    var pinProfile by androidx.compose.runtime.mutableStateOf<TvProfile?>(null)
        private set
    var pinBuffer by androidx.compose.runtime.mutableStateOf("")
        private set
    var pinError by androidx.compose.runtime.mutableStateOf(false)
        private set
    var pinEditProfile by androidx.compose.runtime.mutableStateOf<TvProfile?>(null)
        private set
    var pinEditBuffer by androidx.compose.runtime.mutableStateOf("")
        private set
    var pinEditConfirmation by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var pinEditError by androidx.compose.runtime.mutableStateOf(false)
        private set
    var searchQuery by androidx.compose.runtime.mutableStateOf("")
    var browseCategoryId by androidx.compose.runtime.mutableStateOf<String?>(null)
    var reduceMotion by androidx.compose.runtime.mutableStateOf(preferences.tvReduceMotion().get())
        private set
    var legendTick by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    fun noteRemoteActivity() {
        legendTick++
    }
    var pendingDelete by androidx.compose.runtime.mutableStateOf<TvProfile?>(null)
        private set
    var statusMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    private var statusJob: Job? = null

    private fun showStatus(message: String) {
        statusJob?.cancel()
        statusMessage = message
        statusJob = scope.launch {
            delay(3_500)
            if (statusMessage == message) statusMessage = null
        }
    }

    private fun clearStatus() {
        statusJob?.cancel()
        statusMessage = null
    }
    var roomJoining by androidx.compose.runtime.mutableStateOf(false)
        private set
    var roomCodeInput by androidx.compose.runtime.mutableStateOf("")
        private set

    init {
        TvCompanionSessionRegistry.bridge = companionPlayback
        reloadProfiles()
    }

    fun close() {
        companion.close()
        if (TvCompanionSessionRegistry.bridge === companionPlayback) {
            TvCompanionSessionRegistry.bridge = null
        }
    }

    fun reloadProfiles() {
        scope.launch { profiles = profileStore.profiles() }
    }

    fun selectProfile(profile: TvProfile) {
        if (profile.hasPin) {
            pinProfile = profile
            pinBuffer = ""
            pinError = false
        } else {
            acceptProfile(profile.id)
        }
    }

    private fun acceptProfile(id: String) {
        clearStatus()
        pinProfile = null
        pinBuffer = ""
        val selecting = teamSelection
        if (selecting != null) {
            if (id in selecting) return
            val next = selecting + id
            if (next.size < 2) {
                teamSelection = next
            } else {
                teamSelection = null
                activeProfiles = next
                catalog.selectProfile(next.first())
                backStack.clear()
                screen = TvScreen.HOME
            }
        } else {
            activeProfiles = listOf(id)
            catalog.selectProfile(id)
            backStack.clear()
            screen = TvScreen.HOME
        }
    }

    fun anonymous() {
        clearStatus()
        teamSelection = null
        activeProfiles = listOf(TvCatalogController.ANONYMOUS_ID)
        catalog.selectProfile(TvCatalogController.ANONYMOUS_ID)
        backStack.clear()
        screen = TvScreen.HOME
    }

    fun startTeam() {
        if (profiles.size < 2) {
            showStatus("Crea un secondo profilo per guardare in due.")
            navigate(TvScreen.PROFILES)
            return
        }
        clearStatus()
        teamSelection = emptyList()
        screen = TvScreen.PICKER
    }

    fun cancelTeam() {
        teamSelection = null
    }

    fun changeProfile() {
        clearStatus()
        teamSelection = null
        activeProfiles = emptyList()
        backStack.clear()
        screen = TvScreen.PICKER
    }

    fun digit(value: Int): Boolean {
        if (pinEditProfile != null) {
            if (pinEditBuffer.length < 4) pinEditBuffer += value
            pinEditError = false
            if (pinEditBuffer.length == 4) submitPinEdit()
            return true
        }
        val pending = pinProfile
        if (pending != null) {
            if (pinBuffer.length >= 8) return true
            pinBuffer += value
            pinError = false
            scope.launch {
                val length = profileStore.pinLength(pending.id)
                if (pinBuffer.length == length) submitPin()
            }
            return true
        }
        if (screen == TvScreen.PICKER) {
            val target = when {
                value == 0 && profiles.size > 2 -> {
                    anonymous()
                    return true
                }
                profiles.size > 2 -> profiles.getOrNull(value - 1)
                else -> null
            }
            target?.let(::selectProfile)
            return target != null
        }
        if (screen == TvScreen.ROOMS && roomJoining) {
            if (roomCodeInput.length < 8) roomCodeInput += value
            return true
        }
        return false
    }

    fun submitPin() {
        val pending = pinProfile ?: return
        val entered = pinBuffer
        scope.launch {
            if (profileStore.verifyPin(pending.id, entered)) {
                acceptProfile(pending.id)
            } else {
                pinBuffer = ""
                pinError = true
            }
        }
    }

    fun clearPin() {
        pinProfile = null
        pinBuffer = ""
        pinError = false
    }

    fun editPin(profile: TvProfile) {
        pinEditProfile = profile
        pinEditBuffer = ""
        pinEditConfirmation = null
        pinEditError = false
    }

    fun submitPinEdit() {
        val profile = pinEditProfile ?: return
        if (pinEditBuffer.length != 4) return
        val previous = pinEditConfirmation
        if (previous == null) {
            pinEditConfirmation = pinEditBuffer
            pinEditBuffer = ""
            return
        }
        if (previous != pinEditBuffer) {
            pinEditConfirmation = null
            pinEditBuffer = ""
            pinEditError = true
            return
        }
        scope.launch {
            runCatching { profileStore.setPin(profile.id, pinEditBuffer) }
                .onSuccess {
                    clearPinEdit()
                    reloadProfiles()
                }
                .onFailure {
                    pinEditError = true
                    pinEditBuffer = ""
                }
        }
    }

    fun clearPinEdit() {
        pinEditProfile = null
        pinEditBuffer = ""
        pinEditConfirmation = null
        pinEditError = false
    }

    fun removePin(profile: TvProfile) {
        scope.launch {
            runCatching { profileStore.setPin(profile.id, null) }
                .onSuccess { reloadProfiles() }
                .onFailure { showStatus("Impossibile rimuovere il PIN") }
        }
    }

    fun renameProfile(profile: TvProfile, name: String, artwork: Int) {
        scope.launch {
            runCatching { profileStore.rename(profile.id, name, artwork) }
                .onSuccess {
                    reloadProfiles()
                    clearStatus()
                }
                .onFailure { showStatus(it.message ?: "Impossibile modificare il profilo") }
        }
    }

    fun open(anime: Anime) {
        catalog.open(anime)
        navigate(TvScreen.DETAIL)
    }

    fun openPersonalContinue(item: TvContinueItem) {
        scope.launch {
            val anime = Injekt.get<AnimeRepository>().getAnimeByUrlAndSourceId(item.titleUrl, item.source)
            if (anime != null) open(anime) else showStatus("Questo titolo non è più disponibile nella fonte.")
        }
    }

    fun playPersonalContinue(item: TvContinueItem) {
        scope.launch {
            val anime = Injekt.get<AnimeRepository>().getAnimeByUrlAndSourceId(item.titleUrl, item.source)
            val episode = anime?.let { selected ->
                Injekt.get<EpisodeRepository>().getEpisodeByAnimeId(selected.id)
                    .singleOrNull { it.url == item.episodeUrl }
            }
            if (anime != null && episode != null) {
                play(anime, episode)
            } else if (anime != null) {
                open(anime)
            } else {
                showStatus("Questo titolo non è più disponibile nella fonte.")
            }
        }
    }

    fun play(anime: Anime, episode: Episode) {
        playIds(anime.id, episode.id)
    }

    fun playIds(animeId: Long, episodeId: Long) {
        val session = TvPlaybackAudience(activeProfiles.filter { it != TvCatalogController.ANONYMOUS_ID })
        val intent = PlayerActivity.newIntent(activity, animeId, episodeId).apply { session.writeTo(this) }
        val options = runCatching {
            ActivityOptions.makeBasic().setLaunchDisplayId(
                activity.display?.displayId
                    ?: android.view.Display.DEFAULT_DISPLAY,
            ).toBundle()
        }.getOrNull()
        activity.startActivity(intent, options)
    }

    fun openSearch() {
        searchQuery = ""
        browseCategoryId = null
        navigate(TvScreen.SEARCH)
    }
    fun openCategory(id: String) {
        searchQuery = ""
        browseCategoryId = id
        navigate(TvScreen.SEARCH)
    }

    fun openSettings() {
        navigate(TvScreen.SETTINGS)
    }

    fun showProfileManager() {
        navigate(TvScreen.PROFILES)
    }

    fun backToHome() {
        clearStatus()
        backStack.clear()
        screen = TvScreen.HOME
    }

    fun openRooms() {
        navigate(TvScreen.ROOMS)
    }
    fun openCompanion() {
        companion.start()
        navigate(TvScreen.COMPANION)
    }

    private fun viewingName(): String = activeProfiles.mapNotNull { id ->
        profiles.firstOrNull { it.id == id }?.name
    }.joinToString(" + ").ifBlank { "Spettatore" }

    fun createRoom() {
        watch.displayName = viewingName()
        watch.createRoom(watch.displayName)
        roomJoining = false
        roomCodeInput = ""
    }

    fun beginJoinRoom() {
        roomJoining = true
        roomCodeInput = ""
    }

    fun cancelJoinRoom() {
        roomJoining = false
        roomCodeInput = ""
    }

    fun removeRoomDigit() {
        roomCodeInput = roomCodeInput.dropLast(1)
    }

    fun joinRoom() {
        if (roomCodeInput.length != 8) return
        watch.displayName = viewingName()
        watch.joinRoom(roomCodeInput, watch.displayName)
        roomJoining = false
    }

    fun leaveRoom() {
        watch.controller.leave()
        watch.shortRooms.close()
    }

    fun launchWatchTogether() {
        activity.startActivity(Intent(activity, WatchTogetherActivity::class.java))
    }

    fun createProfile(name: String) {
        scope.launch {
            runCatching { profileStore.create(name, profiles.size) }
                .onSuccess {
                    reloadProfiles()
                    clearStatus()
                }
                .onFailure { showStatus(it.message ?: "Impossibile creare il profilo") }
        }
    }

    fun deleteProfile(profile: TvProfile) {
        pendingDelete = profile
    }

    fun confirmDeleteProfile() {
        val profile = pendingDelete ?: return
        pendingDelete = null
        scope.launch {
            runCatching { profileStore.delete(profile.id) }
                .onSuccess { reloadProfiles() }
                .onFailure { showStatus(it.message ?: "Impossibile eliminare il profilo") }
        }
    }

    fun cancelDeleteProfile() {
        pendingDelete = null
    }

    fun updateReduceMotion(value: Boolean) {
        preferences.tvReduceMotion().set(value)
        reduceMotion = value
    }

    fun useNormalUi() {
        preferences.tvUiMode().set(TvUiMode.NORMAL)
        activity.startActivity(Intent(activity, MainActivity::class.java))
        activity.finish()
    }

    fun back(): Boolean {
        if (pendingDelete != null) {
            cancelDeleteProfile()
            return true
        }
        if (pinEditProfile != null) {
            clearPinEdit()
            return true
        }
        if (pinProfile != null) {
            clearPin()
            return true
        }
        if (screen == TvScreen.ROOMS && roomJoining) {
            cancelJoinRoom()
            return true
        }
        when (screen) {
            TvScreen.PICKER -> {
                if (teamSelection != null) cancelTeam()
                return true
            }
            TvScreen.HOME -> {
                changeProfile()
                return true
            }
            TvScreen.DETAIL -> catalog.closeDetail()
            else -> Unit
        }
        screen = backStack.removeLastOrNull() ?: TvScreen.HOME
        return true
    }

    private fun scrollHomeTo(destination: HomeDestination) {
        val group = catalog.state.value.access.group ?: return
        val hasHero = group.rows.any { row -> row.sections.any { it.layout == "featured" } } ||
            group.rows.firstOrNull()?.sections?.firstOrNull() != null
        val beforeCategories = if (hasHero) 1 else 0
        val compact = activity.resources.configuration.screenHeightDp < 500
        val categoryIndex = beforeCategories + if (compact) 1 else 0
        val continueIndex = categoryIndex + if (group.categories.isNotEmpty()) 1 else 0
        val target = when (destination) {
            HomeDestination.CATEGORIES -> categoryIndex
            HomeDestination.SECTIONS -> continueIndex + 1 + if (catalog.state.value.savedTitles.isNotEmpty()) 1 else 0
        }
        screen = TvScreen.HOME
        requestScroll("home:${group.id}", target)
    }

    private enum class HomeDestination { CATEGORIES, SECTIONS }

    private fun homeHero(): Anime? {
        val state = catalog.state.value
        val group = state.access.group ?: return null
        val section = group.rows.asSequence().flatMap { it.sections.asSequence() }
            .firstOrNull { it.layout == "featured" }
            ?: group.rows.firstOrNull()?.sections?.firstOrNull()
        return section?.let { state.sections[it.id]?.data?.items?.firstOrNull() }
    }

    val homeGreenAction: TvHomeGreenAction
        get() {
            val state = catalog.state.value
            return when {
                state.resume.data.orEmpty().isNotEmpty() || state.personalResume.isNotEmpty() ->
                    TvHomeGreenAction.RESUME
                homeHero() != null -> TvHomeGreenAction.HERO
                else -> TvHomeGreenAction.NONE
            }
        }

    val homeGreenLabel: String?
        get() = when (homeGreenAction) {
            TvHomeGreenAction.RESUME -> "Riprendi"
            TvHomeGreenAction.HERO -> "Apri episodi"
            TvHomeGreenAction.NONE -> null
        }

    val homeYellowLabel: String?
        get() = catalog.state.value.access.group?.let { group ->
            when {
                group.rows.isNotEmpty() -> "Sezioni"
                group.categories.isNotEmpty() -> "Categorie"
                else -> null
            }
        }

    fun playHomeRecommendation(): Boolean {
        val state = catalog.state.value
        state.resume.data.orEmpty().firstOrNull()?.let {
            play(it.anime, it.episode)
            return true
        }
        state.personalResume.firstOrNull()?.let {
            playPersonalContinue(it)
            return true
        }
        homeHero()?.let {
            open(it)
            return true
        }
        return false
    }

    private fun nextHome(): Boolean {
        val state = catalog.state.value
        if (state.homes.size < 2) return false
        val current = state.homes.indexOfFirst { it.id == state.selectedHome }
        val next = state.homes[(current + 1).mod(state.homes.size)]
        catalog.selectHome(next.id)
        backToHome()
        return true
    }

    private fun playDetailSelection() {
        val state = catalog.state.value
        val anime = state.detail ?: return
        val episode = recommendedTvEpisode(
            state.detailEpisodes,
            state.detailProfileStates,
            state.detailUsesMainState,
        )
        if (episode != null) play(anime, episode)
    }

    fun onKey(keyCode: Int): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) return digit(keyCode - KeyEvent.KEYCODE_0)
        if (screen == TvScreen.ROOMS && roomJoining) {
            return when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    joinRoom()
                    true
                }
                KeyEvent.KEYCODE_DEL -> {
                    removeRoomDigit()
                    true
                }
                else -> false
            }
        }
        if (pinEditProfile != null) {
            return when (keyCode) {
                KeyEvent.KEYCODE_PROG_RED -> digit(1)
                KeyEvent.KEYCODE_PROG_GREEN -> digit(2)
                KeyEvent.KEYCODE_PROG_YELLOW -> digit(3)
                KeyEvent.KEYCODE_PROG_BLUE -> digit(4)
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    submitPinEdit()
                    true
                }
                else -> false
            }
        }
        if (pinProfile != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_PROG_RED -> return digit(1)
                KeyEvent.KEYCODE_PROG_GREEN -> return digit(2)
                KeyEvent.KEYCODE_PROG_YELLOW -> return digit(3)
                KeyEvent.KEYCODE_PROG_BLUE -> return digit(4)
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    submitPin()
                    return true
                }
            }
            return false
        }
        if (screen == TvScreen.PICKER) {
            val colors = profiles.size <= 2
            return when (keyCode) {
                KeyEvent.KEYCODE_PROG_RED -> if (colors) {
                    profiles.getOrNull(0)?.let(::selectProfile)
                    true
                } else {
                    false
                }
                KeyEvent.KEYCODE_PROG_GREEN -> if (colors) {
                    profiles.getOrNull(1)?.let(::selectProfile)
                    true
                } else {
                    false
                }
                KeyEvent.KEYCODE_PROG_YELLOW -> if (colors) {
                    anonymous()
                    true
                } else {
                    false
                }
                KeyEvent.KEYCODE_PROG_BLUE -> {
                    startTeam()
                    true
                }
                else -> false
            }
        }
        if (screen == TvScreen.DETAIL) {
            return when (keyCode) {
                KeyEvent.KEYCODE_PROG_RED -> {
                    openSearch()
                    true
                }
                KeyEvent.KEYCODE_PROG_GREEN -> {
                    if (catalog.state.value.detailEpisodes.isEmpty()) {
                        false
                    } else {
                        playDetailSelection()
                        true
                    }
                }
                KeyEvent.KEYCODE_PROG_YELLOW -> {
                    catalog.state.value.detail?.id?.let { id ->
                        requestScroll("detail:$id", 1)
                    }
                    true
                }
                KeyEvent.KEYCODE_PROG_BLUE -> nextHome()
                else -> false
            }
        }
        if (screen != TvScreen.HOME) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_PROG_RED -> {
                openSearch()
                true
            }
            KeyEvent.KEYCODE_PROG_GREEN -> playHomeRecommendation()
            KeyEvent.KEYCODE_PROG_YELLOW -> when (homeYellowLabel) {
                "Categorie" -> {
                    scrollHomeTo(HomeDestination.CATEGORIES)
                    true
                }
                "Sezioni" -> {
                    scrollHomeTo(HomeDestination.SECTIONS)
                    true
                }
                else -> false
            }
            KeyEvent.KEYCODE_PROG_BLUE -> nextHome()
            else -> false
        }
    }
}

package eu.kanade.tachiyomi.data.reading

import android.app.Activity
import android.content.Context
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.tachiyomi.data.watch.WatchCatalogReference
import eu.kanade.tachiyomi.data.watch.WatchTogetherManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.ref.WeakReference

@Serializable
data class ReadingBookmark(val mangaId: Long, val chapterId: Long, val position: ReadingPosition) {
    fun valid(): Boolean = mangaId > 0 &&
        chapterId > 0 &&
        position.pages in 1..5000 &&
        position.page in 0 until position.pages &&
        position.title.isNotBlank()
}
data class ReadingTools(
    val visible: Boolean = true,
    val drawing: Boolean = false,
    val color: Int = 0,
    val width: Int = 2,
    val opening: Boolean = false,
    val error: String? = null,
    val returnTo: ReadingBookmark? = null,
    val navigation: Long = 0,
)

/** Android catalog/navigation adapter. The room itself never calls into a reader or native player. */
class ReadingTogetherManager private constructor(context: Context) {
    val watch = WatchTogetherManager.get(context)
    val controller get() = watch.reading
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = context.getSharedPreferences("reading_together", Context.MODE_PRIVATE)
    private val savedReturn = runCatching {
        eu.kanade.tachiyomi.data.watch.watchJson.decodeFromString<ReadingBookmark>(
            preferences.getString("return", "").orEmpty(),
        ).takeIf { it.valid() }
    }.getOrNull()
    private val mutableTools = MutableStateFlow(
        ReadingTools(visible = preferences.getBoolean("visible", true), returnTo = savedReturn),
    )
    val tools = mutableTools.asStateFlow()
    private var owner = WeakReference<ReaderActivity>(null)
    private var lastReader = WeakReference<ReaderActivity>(null)
    private var current: ReadingBookmark? = null
    private var resolution: Job? = null
    private var target: ReadingBookmark? = null
    private var returning = false
    private var privacyJob: Job? = null
    private var observedSource: Long? = null

    init {
        scope.launch {
            controller.state.map { it.localId to it.active }.distinctUntilChanged().collect { (_, active) ->
                if (!active) {
                    resolution?.cancel()
                    target = null
                    mutableTools.value = tools.value.copy(drawing = false, opening = false)
                }
            }
        }
    }

    fun attach(activity: ReaderActivity) {
        owner = WeakReference(activity)
        lastReader = WeakReference(activity)
        watch.controller.setReadingMode(true)
        current?.takeIf { it.mangaId == activity.viewModel.manga?.id }?.let { publish(it) }
    }

    fun detach(activity: ReaderActivity) {
        if (owner.get() !== activity) return
        owner.clear()
        controller.suspendReading()
        setDrawing(false)
    }

    fun selected(activity: ReaderActivity, bookmark: ReadingBookmark?) {
        if (owner.get() !== activity) return
        current = bookmark
        if (bookmark?.position?.source != observedSource) {
            privacyJob?.cancel()
            observedSource = bookmark?.position?.source
            privacyJob = scope.launch {
                Injekt.get<GetMangaIncognitoState>().subscribe(observedSource).collect { private ->
                    if (private) {
                        controller.position(null, false)
                        setDrawing(false)
                        persistReturn()
                    } else if (owner.get() != null) {
                        current?.let(::publish)
                    }
                }
            }
        }
        if (bookmark != null) publish(bookmark) else controller.position(null, false)
    }

    private fun publish(bookmark: ReadingBookmark) {
        val private = Injekt.get<GetMangaIncognitoState>().await(bookmark.position.source)
        val shared = bookmark.position.takeIf {
            !private && it.valid() && Injekt.get<MangaSourceManager>().get(it.source) is HttpSource
        }
        controller.position(shared, shared != null)
    }

    fun currentPage(): ReadingPosition? = current?.position?.takeIf {
        owner.get() != null && it.valid() && Injekt.get<MangaSourceManager>().get(it.source) is HttpSource
    }?.takeUnless {
        Injekt.get<GetMangaIncognitoState>().await(it.source)
    }

    fun setVisible(value: Boolean) {
        preferences.edit().putBoolean("visible", value).apply()
        mutableTools.value =
            tools.value.copy(visible = value, drawing = tools.value.drawing && value)
    }
    fun setDrawing(value: Boolean) {
        if (value &&
            (!controller.state.value.active || !controller.state.value.supported || currentPage() == null)
        ) {
            return
        }
        mutableTools.value =
            tools.value.copy(drawing = value, visible = if (value) true else tools.value.visible)
    }
    fun setColor(value: Int) {
        if (value in 0..4) mutableTools.value = tools.value.copy(color = value)
    }
    fun setWidth(value: Int) {
        if (value in 1..3) mutableTools.value = tools.value.copy(width = value)
    }
    fun dismissError() {
        mutableTools.value = tools.value.copy(error = null)
    }

    fun jump(activity: Activity, position: ReadingPosition) {
        if (!controller.state.value.active || !position.valid() || tools.value.opening) return
        resolve(activity, position, null)
    }

    fun returnToOwn(activity: Activity) {
        val bookmark = tools.value.returnTo ?: return
        if (tools.value.opening) return
        resolve(activity, bookmark.position, bookmark)
    }

    private fun resolve(activity: Activity, position: ReadingPosition, saved: ReadingBookmark?) {
        resolution?.cancel()
        val activityRef = WeakReference(activity)
        val roomId = controller.state.value.localId
        mutableTools.value = tools.value.copy(opening = true, error = null, drawing = false)
        resolution = scope.launch {
            try {
                val bookmark = withTimeout(45000) {
                    if (saved != null && validLocalBookmark(saved)) saved else resolveCatalog(position)
                }
                val screen = activityRef.get() ?: return@launch
                if (screen.isFinishing || screen.isDestroyed || roomId != controller.state.value.localId) return@launch
                if (saved == null && tools.value.returnTo == null) {
                    mutableTools.value = tools.value.copy(returnTo = current)
                    persistReturn()
                }
                target = bookmark
                returning = saved != null
                if (screen is ReaderActivity &&
                    screen.viewModel.manga?.id == bookmark.mangaId &&
                    screen.viewModel.state.value.currentChapter?.chapter?.id == bookmark.chapterId
                ) {
                    screen.openReadingPage(bookmark)
                } else {
                    // ReaderActivity is singleTask: finish its old ViewModel before opening another title.
                    lastReader.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.finish()
                    screen.startActivity(ReaderActivity.newIntent(screen, bookmark.mangaId, bookmark.chapterId))
                }
            } catch (e: CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    fail(
                        "La fonte non risponde. Il tuo punto è al sicuro: riprova.",
                    )
                } else {
                    throw e
                }
            } catch (e: Exception) {
                fail(
                    (e as? IllegalArgumentException)?.message
                        ?: "Non riesco ad aprire questa pagina. Riprova dalla stanza.",
                )
            } finally {
                mutableTools.value = tools.value.copy(opening = false)
            }
        }
    }

    fun requested(mangaId: Long, chapterId: Long): ReadingBookmark? = target?.takeIf {
        it.mangaId == mangaId &&
            it.chapterId == chapterId
    }

    fun arrived(bookmark: ReadingBookmark) {
        if (target != bookmark) return
        target = null
        mutableTools.value = tools.value.copy(navigation = tools.value.navigation + 1, error = null)
        if (returning) {
            mutableTools.value = tools.value.copy(returnTo = null)
            persistReturn()
        }
        returning = false
    }

    fun fail(message: String) {
        target = null
        mutableTools.value = tools.value.copy(error = message, opening = false)
    }

    private fun persistReturn() {
        preferences.edit().putString(
            "return",
            tools.value.returnTo?.takeUnless {
                Injekt.get<GetMangaIncognitoState>().await(it.position.source)
            }?.let {
                eu.kanade.tachiyomi.data.watch.watchJson.encodeToString(it)
            },
        ).apply()
    }

    private suspend fun validLocalBookmark(bookmark: ReadingBookmark): Boolean = withContext(Dispatchers.IO) {
        val manga = Injekt.get<GetManga>().await(bookmark.mangaId) ?: return@withContext false
        manga.source == bookmark.position.source &&
            manga.url == bookmark.position.manga &&
            Injekt.get<GetChaptersByMangaId>().await(manga.id).any {
                it.id == bookmark.chapterId && it.url == bookmark.position.chapter
            }
    }

    private suspend fun resolveCatalog(position: ReadingPosition): ReadingBookmark = withContext(Dispatchers.IO) {
        require(!Injekt.get<GetMangaIncognitoState>().await(position.source)) {
            "Disattiva la modalità incognito per condividere questa lettura."
        }
        val sources = Injekt.get<MangaSourceManager>()
        sources.isInitialized.first { it }
        val source = sources.get(position.source) as? HttpSource
            ?: throw IllegalArgumentException(
                "Per raggiungere questa pagina serve la stessa estensione, installata e attendibile.",
            )
        require(
            WatchCatalogReference.isAllowed(position.manga, source.baseUrl) &&
                WatchCatalogReference.isAllowed(position.chapter, source.baseUrl),
        ) {
            "Il riferimento non è compatibile con la tua estensione. Aggiornatela su entrambi i telefoni."
        }
        val manga = Injekt.get<NetworkToLocalManga>().await(
            SManga.create().apply {
                url = position.manga
                title = position.title
            }.toDomainManga(source.id),
        )
        val chapters = Injekt.get<GetChaptersByMangaId>()
        var chapter = chapters.await(manga.id).singleOrNull { it.url == position.chapter }
        if (chapter == null) {
            Injekt.get<SyncChaptersWithSource>().await(source.getChapterList(manga.toSManga()), manga, source)
            chapter = chapters.await(manga.id).singleOrNull { it.url == position.chapter }
        }
        requireNotNull(chapter) {
            "Questo capitolo non è disponibile nella stessa edizione. Aprilo dalla tua libreria: non sceglierò un capitolo diverso."
        }
        ReadingBookmark(manga.id, chapter.id, position)
    }

    companion object {
        @Volatile private var instance: ReadingTogetherManager? = null
        fun existing(): ReadingTogetherManager? = instance
        fun get(context: Context): ReadingTogetherManager = instance ?: synchronized(this) {
            instance ?: ReadingTogetherManager(context.applicationContext).also { instance = it }
        }
    }
}

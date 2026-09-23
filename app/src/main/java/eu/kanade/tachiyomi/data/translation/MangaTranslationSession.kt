package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Reader-scoped orchestration. Each finished region/page is cached before the next starts. */
class MangaTranslationSession(
    context: Context,
    scope: CoroutineScope,
    private val page: ReaderPage,
    preferences: ReaderPreferences,
) {
    data class State(
        val language: String = "eng",
        val showInReader: Boolean = true,
        val mode: TranslationViewMode = TranslationViewMode.OVERLAY,
        val original: Bitmap? = null,
        val preview: Bitmap? = null,
        val document: TranslationPage? = null,
        val phase: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        val downloaded: Long = 0,
        val downloadTotal: Long = 0,
        val chapterPage: Int = 0,
        val chapterTotal: Int = 0,
        val glossaryRevision: Int = 0,
    )

    private val ocrPacks = MangaOcrPacks(context.applicationContext)
    private val textPack = OfflineTranslationPack(context.applicationContext)
    private val ocr = MangaOcrEngine(ocrPacks)
    private val cache = MangaTranslationCache(context.applicationContext)
    private val renderer = MangaTranslationRenderer()
    private val glossary = MangaTranslationGlossary(preferences)
    private val showInReaderPreference = preferences.mangaTranslatorShowInReader()
    private val mutableState = MutableStateFlow(
        State(showInReader = showInReaderPreference.get()),
    )
    val state = mutableState.asStateFlow()
    private val sessionScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private var task: Job? = null
    private var previewTask: Job? = null

    init {
        sessionScope.launch { openPage() }
    }

    fun ocrReady(language: String) = ocrPacks.isInstalled(language)
    fun textReady() = textPack.ready()
    fun modelStorageBytes() = textPack.usedBytes() + ocrPacks.usedBytes()
    fun canResumeModelDownload() = textPack.hasPartialDownload()
    fun glossaryEntries() = glossary.entries(state.value.language)

    fun addGlossaryEntry(original: String, italian: String) {
        try {
            glossary.put(state.value.language, original, italian)
            mutableState.update { it.copy(glossaryRevision = it.glossaryRevision + 1, error = null) }
        } catch (e: Exception) {
            mutableState.update { it.copy(error = e.message) }
        }
    }

    fun removeGlossaryEntry(original: String) {
        glossary.remove(state.value.language, original)
        mutableState.update { it.copy(glossaryRevision = it.glossaryRevision + 1) }
    }

    fun removeModels() = start {
        mutableState.update { it.copy(phase = "Elimino i modelli") }
        textPack.remove()
        ocrPacks.remove()
        mutableState.update { it.copy(phase = "Modelli eliminati") }
    }

    fun selectMode(mode: TranslationViewMode) {
        mutableState.update { it.copy(mode = mode, error = null) }
        previewTask?.cancel()
        previewTask = sessionScope.launch { updatePreview() }
    }

    fun setShowInReader(enabled: Boolean) {
        showInReaderPreference.set(enabled)
        mutableState.update { it.copy(showInReader = enabled) }
    }

    fun installModels() = start {
        val language = state.value.language
        mutableState.update { it.copy(phase = "Scarico il modello OCR", error = null) }
        ocrPacks.install(language) { done, total ->
            mutableState.update { it.copy(downloaded = done, downloadTotal = total) }
        }
        mutableState.update {
            it.copy(
                phase = "Scarico il motore di traduzione",
                downloaded = 0,
                downloadTotal = OfflineTranslationPack.totalBytes,
            )
        }
        textPack.install { done, total ->
            mutableState.update { it.copy(downloaded = done, downloadTotal = total) }
        }
        mutableState.update { it.copy(phase = "Modelli pronti", downloaded = 0, downloadTotal = 0) }
    }

    fun translatePage() = start {
        check(ocrPacks.isInstalled(state.value.language) && textPack.ready()) { "Scarica prima i modelli offline" }
        withContext(Dispatchers.Default) {
            OfflineTextTranslator(textPack).use { translate(page, show = true, translator = it) }
        }
    }

    fun translateChapter() = start {
        val pages = page.chapter.pages ?: error("Il capitolo non è ancora disponibile")
        val loader = page.chapter.pageLoader ?: error("Il caricatore del capitolo non è disponibile")
        check(ocrPacks.isInstalled(state.value.language) && textPack.ready()) { "Scarica prima i modelli offline" }
        mutableState.update { it.copy(chapterPage = 0, chapterTotal = pages.size) }
        withContext(Dispatchers.Default) {
            OfflineTextTranslator(textPack).use { translator ->
                pages.forEachIndexed { index, entry ->
                    mutableState.update {
                        it.copy(chapterPage = index + 1, phase = "Pagina ${index + 1} di ${pages.size}")
                    }
                    awaitPage(entry, loader)
                    translate(entry, show = entry === page, translator = translator)
                }
            }
        }
        mutableState.update { it.copy(phase = "Capitolo tradotto", chapterPage = 0, chapterTotal = 0) }
    }

    fun correctRegion(index: Int, translation: String, saveToGlossary: Boolean = false) {
        val current = state.value.document ?: return
        if (index !in current.regions.indices || translation.length > 2000) return
        if (saveToGlossary && translation.isNotBlank()) addGlossaryEntry(current.regions[index].original, translation)
        val revised = current.copy(
            regions = current.regions.toMutableList().apply {
                this[index] = this[index].copy(translated = translation.trim())
            },
        )
        mutableState.update { it.copy(document = revised) }
        sessionScope.launch {
            cache.write(revised)
            cache.writeForPage(page, revised)
            updatePreview()
        }
    }

    fun cancel() {
        task?.cancel()
    }
    fun close() {
        sessionScope.cancel()
    }

    private fun start(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null, downloaded = 0, downloadTotal = 0) }
        task = sessionScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                mutableState.update { it.copy(phase = "In pausa", error = null) }
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = e.message ?: "Traduzione non riuscita", phase = "") }
            } finally {
                mutableState.update { it.copy(busy = false, chapterPage = 0, chapterTotal = 0) }
            }
        }
    }

    private suspend fun openPage() {
        try {
            if (page.stream == null) {
                mutableState.update { it.copy(phase = "Carico la pagina") }
                val loader = page.chapter.pageLoader ?: error("Il caricatore della pagina non è disponibile")
                awaitPage(page, loader)
            }
            val image = loadMangaPageBitmap(page)
            mutableState.update { it.copy(original = image.bitmap, preview = image.bitmap, phase = "") }
            loadCache(image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableState.update { it.copy(error = e.message ?: "Pagina non leggibile") }
        }
    }

    private suspend fun loadCache(image: MangaPageBitmap? = null) {
        val source = image ?: loadMangaPageBitmap(page)
        try {
            cache.read(source.imageHash, state.value.language)?.let { cached ->
                val adjusted = applyGlossary(cached)
                mutableState.update { it.copy(document = adjusted) }
                updatePreview()
            }
        } finally {
            if (image == null) source.bitmap.recycle()
        }
    }

    private suspend fun translate(entry: ReaderPage, show: Boolean, translator: OfflineTextTranslator) {
        val language = state.value.language
        mutableState.update { it.copy(phase = "Leggo la pagina") }
        val image = loadMangaPageBitmap(entry)
        try {
            val cached = cache.read(image.imageHash, language)
            var document = cached ?: run {
                mutableState.update { it.copy(phase = "Riconosco il testo") }
                val recognized = ocr.recognize(image, language)
                recognized.copy(regions = groupTranslationLines(recognized.regions, false)).also {
                    cache.write(it)
                }
            }
            document = applyGlossary(document)
            if (document.regions.isEmpty()) {
                if (show) {
                    mutableState.update {
                        it.copy(document = document, phase = "Nessun testo rilevato. Prova un'altra lingua OCR.")
                    }
                }
                return
            }
            if (show) mutableState.update { it.copy(original = image.bitmap, document = document) }
            if (document.regions.any { it.translated.isBlank() }) {
                document.regions.forEachIndexed { index, region ->
                    if (region.translated.isNotBlank()) return@forEachIndexed
                    mutableState.update { it.copy(phase = "Traduco ${index + 1}/${document.regions.size}") }
                    val italian = glossary.lookup(language, region.original) ?: translator.translate(region.original)
                    document = document.copy(
                        regions = document.regions.toMutableList().apply {
                            this[index] = region.copy(translated = italian)
                        },
                    )
                    cache.write(document)
                    if (show) {
                        mutableState.update { it.copy(document = document) }
                    }
                }
            }
            if (document.regions.any { it.translated.isNotBlank() }) {
                cache.writeForPage(entry, document)
            }
            if (show) {
                mutableState.update { it.copy(document = document, phase = "Pronto") }
                updatePreview()
            }
        } finally {
            if (!show) image.bitmap.recycle()
        }
    }

    private fun applyGlossary(document: TranslationPage): TranslationPage = document.copy(
        regions = document.regions.map { region ->
            glossary.lookup(document.language, region.original)?.let { region.copy(translated = it) } ?: region
        },
    )

    private suspend fun awaitPage(entry: ReaderPage, loader: PageLoader) {
        if (entry.status == Page.State.READY && entry.stream != null) return
        val loading = sessionScope.launch { loader.loadPage(entry) }
        try {
            val status = withTimeout(90_000) {
                entry.statusFlow.first { it == Page.State.READY || it == Page.State.ERROR }
            }
            check(status == Page.State.READY && entry.stream != null) { "Pagina ${entry.number} non disponibile" }
        } finally {
            loading.cancelAndJoin()
        }
    }

    private suspend fun updatePreview() {
        val snapshot = state.value
        val bitmap = snapshot.original ?: return
        val regions = snapshot.document?.regions.orEmpty()
        val preview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            renderer.render(bitmap, regions, snapshot.mode)
        }
        mutableState.update { it.copy(preview = preview) }
    }
}

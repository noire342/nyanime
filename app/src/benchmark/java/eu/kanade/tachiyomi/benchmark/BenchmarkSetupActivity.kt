package eu.kanade.tachiyomi.benchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.core.migration.Migrator
import tachiyomi.domain.discovery.AnimeCatalogCache
import tachiyomi.domain.discovery.CatalogAnime
import tachiyomi.domain.discovery.CatalogCacheEntry
import tachiyomi.domain.discovery.CatalogFeed
import tachiyomi.domain.discovery.CatalogId
import tachiyomi.domain.discovery.CatalogPage
import tachiyomi.domain.discovery.CatalogRequest
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.storage.service.StoragePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant

/** Compiled exclusively into the isolated .benchmark application, never production APKs. */
class BenchmarkSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName.endsWith(".benchmark"))
        val label = TextView(this).apply { text = "Preparing benchmark fixtures" }
        setContentView(label)
        lifecycleScope.launch {
            try {
                if (intent.getBooleanExtra("reader", false)) {
                    val manga = Injekt.get<MangaRepository>().getMangaByUrlAndSourceId("Benchmark reader", 0)!!
                    val chapter = Injekt.get<ChapterRepository>().getChapterByMangaId(manga.id).first()
                    startActivity(ReaderActivity.newIntent(this@BenchmarkSetupActivity, manga.id, chapter.id))
                    finish()
                } else {
                    android.util.Log.i("BenchmarkSetup", "Preparing fixtures")
                    withContext(Dispatchers.IO) { prepare() }
                    label.text = "BENCHMARK_READY"
                    android.util.Log.i("BenchmarkSetup", "BENCHMARK_READY")
                }
            } catch (e: Exception) {
                android.util.Log.e("BenchmarkSetup", "Fixture preparation failed", e)
                label.text = "BENCHMARK_FAILED: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    private suspend fun prepare() {
        android.util.Log.i("BenchmarkSetup", "Waiting for migrations")
        Migrator.await()
        android.util.Log.i("BenchmarkSetup", "Preparing storage and preferences")
        val base = Injekt.get<BasePreferences>()
        base.shownOnboardingFlow().set(true)
        base.incognitoMode().set(false)
        base.downloadedOnly().set(false)
        Injekt.get<ReaderPreferences>().apply {
            showNavigationOverlayNewUser().set(false)
            showNavigationOverlayOnStart().set(false)
        }
        Injekt.get<UiPreferences>().apply {
            installDiscoveryNavigationOnce()
            startScreen().set(StartScreen.HOME)
            navStyle().set(NavStyle.DISCOVERY)
        }
        val storage = File(filesDir, "benchmark-storage").apply { mkdirs() }
        Injekt.get<StoragePreferences>().baseStorageDirectory().set(storage.toUri().toString())
        val pageDirectory = File(storage, "local/Benchmark reader/Chapter 1").apply { mkdirs() }
        val image = Bitmap.createBitmap(720, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 38f
            strokeWidth = 4f
        }
        repeat(8) { page ->
            canvas.drawColor(Color.WHITE)
            canvas.drawText("Synthetic benchmark page ${page + 1}", 30f, 70f, paint)
            paint.style = Paint.Style.STROKE
            repeat(6) { panel -> canvas.drawRect(25f, 100f + panel * 140f, 690f, 225f + panel * 140f, paint) }
            paint.style = Paint.Style.FILL
            File(pageDirectory, "${page + 1}.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        image.recycle()
        android.util.Log.i("BenchmarkSetup", "Preparing library entries")
        val cover = File(pageDirectory, "1.png").toUri().toString()
        val animeRepo = Injekt.get<AnimeRepository>()
        val mangaRepo = Injekt.get<MangaRepository>()
        repeat(500) { index ->
            if (index % 100 == 0) android.util.Log.i("BenchmarkSetup", "Library entries: $index/500")
            val title = "Benchmark ${index.toString().padStart(4, '0')} · Un titolo lungo per verificare la libreria"
            if (animeRepo.getAnimeByUrlAndSourceId("benchmark/$index", 0) == null) {
                animeRepo.insertAnime(
                    Anime.create().copy(
                        source = 0,
                        favorite = true,
                        initialized = true,
                        url = "benchmark/$index",
                        title = title,
                        thumbnailUrl = cover,
                    ),
                )
            }
            if (mangaRepo.getMangaByUrlAndSourceId("benchmark/$index", 0) == null) {
                mangaRepo.insertManga(
                    Manga.create().copy(
                        source = 0,
                        favorite = true,
                        initialized = true,
                        url = "benchmark/$index",
                        title = title,
                        thumbnailUrl = cover,
                    ),
                )
            }
        }
        val readerId = mangaRepo.getMangaByUrlAndSourceId("Benchmark reader", 0)?.id
            ?: mangaRepo.insertManga(
                Manga.create().copy(
                    source = 0,
                    favorite = true,
                    initialized = true,
                    url = "Benchmark reader",
                    title = "Benchmark reader",
                    thumbnailUrl = cover,
                ),
            )!!
        val chapters = Injekt.get<ChapterRepository>()
        if (chapters.getChapterByMangaId(readerId).isEmpty()) {
            chapters.addAllChapters(
                listOf(
                    Chapter.create().copy(
                        mangaId = readerId,
                        url = "Benchmark reader/Chapter 1",
                        name = "Chapter 1",
                        chapterNumber = 1.0,
                    ),
                ),
            )
        }
        val cache = Injekt.get<AnimeCatalogCache>()
        android.util.Log.i("BenchmarkSetup", "Preparing catalog cache")
        val json = Injekt.get<Json>()
        val items = List(30) {
            CatalogAnime(
                CatalogId(value = it.toLong() + 1),
                "Benchmark anime $it · Titolo completo",
                cover = cover,
                genres = listOf("Synthetic"),
            )
        }
        for (feed in CatalogFeed.entries) {
            val request = CatalogRequest(feed, query = if (feed == CatalogFeed.SEARCH) "Benchmark" else "")
            cache.write(
                request.cacheKey,
                CatalogCacheEntry(
                    json.encodeToString(
                        CatalogPage.serializer(),
                        CatalogPage(items),
                    ),
                    Instant.now().toEpochMilli(),
                ),
                false,
            )
        }
    }
}

package eu.kanade.tachiyomi.data.translation

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** Contains only derived OCR/translation data, never the original image or a source identifier. */
class MangaTranslationCache(context: Context) {
    companion object {
        val updates = MutableSharedFlow<String>(extraBufferCapacity = 32)

        fun pageKey(page: ReaderPage): String {
            val identity = "${page.chapter.chapter.manga_id}:${page.chapter.chapter.url}:${page.index}"
            return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }

    // Translated pages are user-created reading data. Android may evict cacheDir at any time.
    private val root = File(context.filesDir, "manga-translation-v2")
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(imageHash: String, language: String): File {
        require(imageHash.matches(Regex("[0-9a-f]{64}")))
        require(language in MangaOcrPacks.packs)
        return File(root, "$imageHash-$language.json")
    }

    suspend fun read(imageHash: String, language: String): TranslationPage? = withContext(Dispatchers.IO) {
        val file = file(imageHash, language)
        if (!file.isFile || file.length() > 256 * 1024) return@withContext null
        val page = runCatching { json.decodeFromString<TranslationPage>(file.readText()) }.getOrNull()
        if (page?.valid() == true && page.imageHash == imageHash && page.language == language) {
            file.setLastModified(System.currentTimeMillis())
            page
        } else {
            file.delete()
            null
        }
    }

    suspend fun write(page: TranslationPage) = withContext(Dispatchers.IO) {
        require(page.valid())
        check(root.isDirectory || root.mkdirs())
        val target = file(page.imageHash, page.language)
        val temp = File(root, "${target.name}.part")
        try {
            temp.writeText(json.encodeToString(page))
            check(temp.length() <= 256 * 1024) { "Risultato OCR troppo grande" }
            check(temp.renameTo(target)) { "Impossibile salvare la traduzione" }
        } finally {
            temp.delete()
        }
    }

    suspend fun readForPage(page: ReaderPage): TranslationPage? = withContext(Dispatchers.IO) {
        val target = File(root, "page-${pageKey(page)}.json")
        if (!target.isFile || target.length() > 256 * 1024) return@withContext null
        runCatching { json.decodeFromString<TranslationPage>(target.readText()) }
            .getOrNull()?.takeIf(TranslationPage::valid)
    }

    suspend fun writeForPage(page: ReaderPage, document: TranslationPage) = withContext(Dispatchers.IO) {
        require(document.valid())
        check(root.isDirectory || root.mkdirs())
        val key = pageKey(page)
        val target = File(root, "page-$key.json")
        val temp = File(root, "${target.name}.part")
        try {
            temp.writeText(json.encodeToString(document))
            check(temp.length() <= 256 * 1024) { "Traduzione troppo grande" }
            check(temp.renameTo(target)) { "Impossibile salvare la pagina tradotta" }
        } finally {
            temp.delete()
        }
        updates.tryEmit(key)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        root.listFiles()?.forEach(File::delete)
    }
}

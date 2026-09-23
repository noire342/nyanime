package eu.kanade.tachiyomi.data.translation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Contains only derived OCR/translation data, never the original image or a source identifier. */
class MangaTranslationCache(context: Context) {
    private val root = File(context.cacheDir, "manga-translation-v1")
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
        val files = root.listFiles { candidate -> candidate.extension == "json" }
            ?.sortedByDescending(File::lastModified).orEmpty()
        files.drop(300).forEach(File::delete)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        root.listFiles()?.forEach(File::delete)
    }
}

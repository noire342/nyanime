package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import org.json.JSONObject
import java.util.Locale

/** Explicit corrections only. Exact source matches are deterministic and never alter the page image. */
class MangaTranslationGlossary(private val preferences: ReaderPreferences) {
    private val preference = preferences.mangaTranslatorGlossary()

    @Synchronized
    fun lookup(language: String, original: String): String? =
        read().optJSONObject(language)?.optString(key(language, original))?.takeIf(String::isNotBlank)

    @Synchronized
    fun entries(language: String): List<Pair<String, String>> {
        val dictionary = read().optJSONObject(language) ?: return emptyList()
        return dictionary.keys().asSequence().map {
            it to dictionary.getString(it)
        }.sortedBy(Pair<String, String>::first).toList()
    }

    @Synchronized
    fun put(language: String, original: String, italian: String) {
        require(language in MangaOcrPacks.packs)
        val source = key(language, original)
        val translated = italian.trim()
        require(source.isNotBlank() && source.length <= 2000 && translated.isNotBlank() && translated.length <= 2000)
        val root = read()
        val dictionary = root.optJSONObject(language) ?: JSONObject().also { root.put(language, it) }
        check(dictionary.length() < 500 || dictionary.has(source)) { "Glossario pieno" }
        dictionary.put(source, translated)
        preference.set(root.toString())
    }

    @Synchronized
    fun remove(language: String, original: String) {
        val root = read()
        root.optJSONObject(language)?.remove(key(language, original))
        preference.set(root.toString())
    }

    private fun read() = runCatching { JSONObject(preference.get()) }.getOrElse { JSONObject() }

    private fun key(language: String, original: String): String = original.trim().replace(Regex("\\s+"), " ").let {
        if (language == "eng") it.lowercase(Locale.ROOT) else it
    }
}

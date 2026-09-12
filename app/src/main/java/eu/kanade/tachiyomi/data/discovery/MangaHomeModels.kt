package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaHomeMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.domain.entries.manga.model.Manga

@Serializable
data class MangaHomeChapter(val url: String, val label: String, val date: String? = null, val isNew: Boolean = false)

@Serializable
data class MangaHomePresentation(
    val version: Int = 1,
    val id: String? = null,
    val badges: List<String> = emptyList(),
    val details: List<String> = emptyList(),
    val rank: Int? = null,
    val chapters: List<MangaHomeChapter> = emptyList(),
    val sectionTitle: String? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun from(manga: SManga): MangaHomePresentation? =
            parse((manga as? SMangaHomeMetadata)?.homePresentation)

        fun parse(raw: String?): MangaHomePresentation? {
            if (raw == null || raw.length > 8192) return null
            val value = runCatching { json.decodeFromString<MangaHomePresentation>(raw) }.getOrNull() ?: return null
            if (value.version != 1) return null
            return value.copy(
                id = value.id?.takeIf { it.length <= 2048 },
                badges = value.badges.filter { it.isNotBlank() && it.length <= 100 }.take(6),
                details = value.details.filter { it.isNotBlank() && it.length <= 200 }.take(6),
                rank = value.rank?.takeIf { it in 1..1000 },
                chapters = value.chapters.filter {
                    it.label.isNotBlank() && it.label.length <= 200 && validChapterUrl(it.url)
                }.take(5).map { it.copy(date = it.date?.take(100)) },
                sectionTitle = value.sectionTitle?.takeIf { it.isNotBlank() && it.length <= 100 },
            )
        }

        private fun validChapterUrl(url: String) =
            url.startsWith("/") &&
                !url.startsWith("//") &&
                url.length <= 2048 &&
                url.none { it.isISOControl() || it == '\\' }
    }
}

data class MangaHomeItem(val manga: Manga, val presentation: MangaHomePresentation? = null) {
    val key: String get() = manga.source.toString() + ":" + manga.url + ":" + presentation?.id.orEmpty()
}

data class MangaHomePage(val items: List<MangaHomeItem>, val hasNextPage: Boolean)

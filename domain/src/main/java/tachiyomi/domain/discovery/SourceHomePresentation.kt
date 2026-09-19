package tachiyomi.domain.discovery

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import tachiyomi.domain.entries.anime.model.Anime

/** Public, source-owned card data; never a replacement for library identity or episode progress. */
@Serializable
data class SourceHomePresentation(
    val id: String? = null,
    val badges: List<String> = emptyList(),
    val details: List<String> = emptyList(),
    val sectionTitle: String? = null,
    val logoUrl: String? = null,
    val logoName: String? = null,
    val logoBackground: String? = null,
) {
    fun bounded() = copy(
        id = id?.takeIf { valid(it, 512) },
        badges = badges.filter { valid(it, 80) }.distinct().take(8),
        details = details.filter { valid(it, 300) }.distinct().take(8),
        sectionTitle = sectionTitle?.takeIf { valid(it, 100) },
        logoName = logoName?.takeIf { valid(it, 80) },
        logoBackground = logoBackground?.takeIf { it in listOf("light", "dark") },
        logoUrl = logoUrl?.takeIf { value ->
            valid(value, 2048) &&
                runCatching {
                    val uri = java.net.URI(value)
                    uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
                }.getOrDefault(false)
        },
    )

    fun attachTo(memo: JsonObject) = JsonObject(without(memo) + (KEY to json.encodeToJsonElement(bounded())))

    companion object {
        const val KEY = "aniyomi.home.v1"
        private val json = Json { ignoreUnknownKeys = true }
        private fun valid(value: String, max: Int) =
            value.isNotBlank() && value.length <= max && value.none(Char::isISOControl)

        fun without(memo: JsonObject) = JsonObject(memo - KEY)

        fun from(memo: JsonObject): SourceHomePresentation? = memo[KEY]?.let { value ->
            if (value.toString().length > 8192) return null
            runCatching { json.decodeFromJsonElement<SourceHomePresentation>(value).bounded() }.getOrNull()
        }
    }
}

val Anime.homePresentation get() = SourceHomePresentation.from(memo)

/** Length prefixes avoid collisions; Compose keys and pagination share the same concrete card identity. */
val Anime.homeItemKey: String get() {
    val entry = homePresentation?.id.orEmpty()
    return "$source:${url.length}:$url:${entry.length}:$entry"
}

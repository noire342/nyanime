package eu.kanade.tachiyomi.data.share

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

@Serializable
enum class SharedMedium { ANIME, MANGA }

/** Source references are opaque extension data, never database IDs or playable stream URLs. */
@Serializable
data class ContentLink(
    val version: Int = 1,
    val medium: SharedMedium,
    val sourceId: Long,
    val entryUrl: String,
    val title: String,
    val sourceName: String? = null,
    val itemUrl: String? = null,
    val itemTitle: String? = null,
    val positionMs: Long? = null,
    /** Human page number: page one is always 1, independently of reading direction. */
    val page: Int? = null,
) {
    fun entryOnly(): ContentLink = copy(itemUrl = null, itemTitle = null, positionMs = null, page = null)

    fun itemFromStart(): ContentLink = copy(
        positionMs = if (medium == SharedMedium.ANIME && itemUrl != null) 0 else null,
        page = if (medium == SharedMedium.MANGA && itemUrl != null) 1 else null,
    )
}

object ContentLinks {
    const val PREFIX = "nyanime://open/v1#"
    const val MAX_LINK_LENGTH = 24_000
    const val MAX_POSITION_MS = 30L * 24 * 60 * 60 * 1000
    const val MAX_PAGE = 100_000
    private val json = Json { ignoreUnknownKeys = true }
    private val linkInText = Regex("nyanime://open/[^\\s<>\"]+", RegexOption.IGNORE_CASE)
    private val encoded = Regex("[A-Za-z0-9_-]+")

    fun encode(link: ContentLink): String {
        require(valid(link)) { "Invalid content reference" }
        val result = PREFIX +
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.encodeToString(link).toByteArray(Charsets.UTF_8))
        require(result.length <= MAX_LINK_LENGTH)
        return result
    }

    /** Also accepts our share message sent back through Android's Share with Nyanime action. */
    fun extract(text: String): String? {
        if (text.length > MAX_LINK_LENGTH + 4096) return null
        val matches = linkInText.findAll(text).take(2).toList()
        return matches.singleOrNull()?.value?.takeIf { it.length <= MAX_LINK_LENGTH }
    }

    fun decode(text: String): ContentLink? = runCatching {
        val value = extract(text) ?: return null
        val uri = URI(value)
        if (!uri.scheme.equals("nyanime", true) ||
            !uri.host.equals("open", true) ||
            uri.rawPath != "/v1" ||
            uri.rawQuery != null ||
            uri.rawUserInfo != null ||
            uri.port != -1
        ) {
            return null
        }
        val fragment = uri.rawFragment ?: return null
        if (!encoded.matches(fragment)) return null
        val bytes = Base64.getUrlDecoder().decode(fragment)
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        json.decodeFromString<ContentLink>(utf8).takeIf(::valid)
    }.getOrNull()

    private fun valid(link: ContentLink): Boolean = with(link) {
        fun clean(value: String, max: Int): Boolean = value.isNotBlank() &&
            value.length <= max &&
            value.none { it.code < 32 || it.code == 127 }
        version == 1 &&
            sourceId != 0L &&
            clean(entryUrl, 4096) &&
            clean(title, 512) &&
            (sourceName == null || clean(sourceName, 128)) &&
            (itemUrl == null || clean(itemUrl, 4096)) &&
            (itemTitle == null || clean(itemTitle, 512)) &&
            (itemUrl != null || (itemTitle == null && positionMs == null && page == null)) &&
            (positionMs == null || (medium == SharedMedium.ANIME && positionMs in 0..MAX_POSITION_MS)) &&
            (page == null || (medium == SharedMedium.MANGA && page in 1..MAX_PAGE))
    }
}

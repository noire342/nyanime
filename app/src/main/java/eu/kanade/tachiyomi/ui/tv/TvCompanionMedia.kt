package eu.kanade.tachiyomi.ui.tv

import eu.kanade.tachiyomi.data.cast.CompanionClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class TvCompanionSubtitle(val name: String, val url: String)
data class TvCompanionMedia(
    val id: String,
    val title: String,
    val episode: String,
    val url: String,
    val mimeType: String,
    val positionMs: Long,
    val durationMs: Long,
    val subtitles: List<TvCompanionSubtitle>,
)

/** Keep the relay boundary identical to the portable Companion contract. */
internal object TvCompanionMediaValidator {
    private const val MAX_TIME = 7 * 24 * 60 * 60 * 1000L
    private val mimeTypes = setOf(
        "video/mp4",
        "video/webm",
        "video/x-matroska",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
    )

    fun parse(value: JsonObject, peer: String): TvCompanionMedia {
        fun label(name: String, max: Int): String = value.getValue(name).jsonPrimitive.content.also {
            require(it.length <= max && it.none { char -> char.code < 32 || char.code == 127 })
        }
        fun time(name: String): Long = value.getValue(name).jsonPrimitive.content.toLong().also {
            require(it in 0..MAX_TIME)
        }
        fun relay(raw: String): String {
            require(raw.length <= 8192 && raw.none { it.code <= 32 || it.code == 127 })
            val url = raw.toHttpUrlOrNull() ?: error("Indirizzo del video non valido")
            require(url.scheme == "http" && url.host == peer && CompanionClient.isLanIpv4(peer))
            require(url.username.isEmpty() && url.password.isEmpty() && url.fragment == null)
            require(raw.contains(Regex(":\\d{1,5}/")))
            return url.toString()
        }
        val id = label("id", 32)
        require(id.matches(Regex("[a-f0-9]{32}")))
        val mime = label("mimeType", 100)
        require(mime in mimeTypes)
        val subtitles = value.getValue("subtitles").jsonArray.also { require(it.size <= 16) }
            .map { element ->
                val subtitle = element.jsonObject
                val name = subtitle.getValue("name").jsonPrimitive.content
                require(name.length <= 100 && name.none { it.code < 32 || it.code == 127 })
                TvCompanionSubtitle(name, relay(subtitle.getValue("url").jsonPrimitive.content))
            }
        return TvCompanionMedia(
            id,
            label("title", 300),
            label("episode", 300),
            relay(value.getValue("url").jsonPrimitive.content),
            mime,
            time("positionMs"),
            time("durationMs"),
            subtitles,
        )
    }
}

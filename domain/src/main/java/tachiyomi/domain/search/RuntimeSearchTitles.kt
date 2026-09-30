package tachiyomi.domain.search

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaTrackingMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest

/** Optional, versioned metadata. Existing extensions do not have to implement anything new. */
fun searchAliases(memo: JsonObject): List<String> = listOf("nyanime.search.v1", "nyanime.tracking.v1")
    .flatMap { key ->
        val payload = memo[key] as? JsonObject
        listOf("aliases", "titles").flatMap { field ->
            (payload?.get(field) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        }
    }.filter { it.length in 1..256 }.distinct().take(15)

fun runtimeSearchTitle(
    source: Long,
    reference: String,
    title: String,
    medium: SearchMedium,
    aliases: List<String> = emptyList(),
): SearchTitle {
    // Cache keys identify a source result without storing its URL, cookies or credentials.
    val digest = MessageDigest.getInstance("SHA-256").digest("$source\u0000$reference".toByteArray())
    val key = digest.joinToString("") { "%02x".format(it) }
    return SearchTitle(key, title.take(256), medium, aliases.take(15), sourceId = source)
}

fun SAnime.searchTitle(source: Long) = runtimeSearchTitle(
    source,
    url,
    title,
    SearchMedium.VIDEO,
    runCatching { searchAliases(memo) }.getOrDefault(emptyList()),
)
fun SManga.searchTitle(source: Long): SearchTitle {
    val tracking = (this as? SMangaTrackingMetadata)?.trackingMetadata?.takeIf { it.length <= 8_192 }
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    val titles = (tracking?.get("titles") as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.length in 1..256 }
    val aliases = runCatching { searchAliases(memo) }.getOrDefault(emptyList())
    return runtimeSearchTitle(source, url, title, SearchMedium.MANGA, (aliases + titles).distinct())
}

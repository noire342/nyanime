package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Public metadata lookup; it does not access a user's tracker account or publish viewing history. */
internal object AniListMediaLookup {
    enum class Type { ANIME, MANGA }

    data class Match(val id: Long, val malId: Long?, val titles: List<String>, val episodes: Int?)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheMutex = Mutex()
    private data class CacheEntry(val expiresAt: Long, val match: Match?)
    private val cache = LinkedHashMap<Pair<Type, String>, CacheEntry>(128, 0.75f, true)

    suspend fun resolve(title: String, type: Type): Match? = cacheMutex.withLock {
        val key = type to TrackTitleMatcher.normalize(title)
        val cached = cache[key]
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) return@withLock cached.match
        val match = fetch(title, type)
        cache[key] = CacheEntry(System.currentTimeMillis() + 60 * 60 * 1000L, match)
        if (cache.size > 128) cache.remove(cache.keys.first())
        match
    }

    suspend fun resolveId(id: Long, type: Type, malId: Boolean): Match? = cacheMutex.withLock {
        if (id !in 1..Int.MAX_VALUE.toLong()) return@withLock null
        val key = type to "${if (malId) "mal" else "al"}:$id"
        val cached = cache[key]
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) return@withLock cached.match
        val match = fetchId(id, type, malId)
        cache[key] = CacheEntry(System.currentTimeMillis() + 60 * 60 * 1000L, match)
        if (cache.size > 128) cache.remove(cache.keys.first())
        match
    }

    private suspend fun fetch(title: String, type: Type): Match? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val query = """
            query Search(${'$'}title: String) {
              Page(perPage: 50) {
                pageInfo { hasNextPage }
                media(search: ${'$'}title, type: ${type.name}) {
                  id idMal episodes title { romaji english native } synonyms
                }
              }
            }
        """.trimIndent()
        val body = buildJsonObject {
            put("query", query)
            putJsonObject("variables") { put("title", title) }
        }.toString().toRequestBody(jsonMime)
        val response = client.newCall(POST("https://graphql.anilist.co", body = body)).awaitSuccess().use {
            json.decodeFromString<SearchResponse>(it.body.string())
        }
        val page = response.data?.page ?: return@withContext null
        if (page.pageInfo?.hasNextPage == true) return@withContext null
        val media = TrackTitleMatcher.choose(title, page.media, { item -> item.names }, { it.id })
            ?: return@withContext null
        media.toMatch()
    }

    private suspend fun fetchId(id: Long, type: Type, malId: Boolean): Match? = withContext(Dispatchers.IO) {
        val field = if (malId) "idMal" else "id"
        val query = """
            query Lookup(${'$'}id: Int) {
              Media($field: ${'$'}id, type: ${type.name}) {
                id idMal episodes title { romaji english native } synonyms
              }
            }
        """.trimIndent()
        val body = buildJsonObject {
            put("query", query)
            putJsonObject("variables") { put("id", id) }
        }.toString().toRequestBody(jsonMime)
        client.newCall(POST("https://graphql.anilist.co", body = body)).awaitSuccess().use {
            json.decodeFromString<IdResponse>(it.body.string()).data?.media?.toMatch()
        }
    }

    private fun Media.toMatch() = Match(id, idMal?.takeIf { it > 0 }, names, episodes?.takeIf { it > 0 })

    @Serializable
    private data class SearchResponse(val data: SearchData? = null)

    @Serializable
    private data class SearchData(@kotlinx.serialization.SerialName("Page") val page: SearchPage? = null)

    @Serializable
    private data class IdResponse(val data: IdData? = null)

    @Serializable
    private data class IdData(@kotlinx.serialization.SerialName("Media") val media: Media? = null)

    @Serializable
    private data class SearchPage(
        val pageInfo: PageInfo? = null,
        val media: List<Media> = emptyList(),
    )

    @Serializable
    private data class PageInfo(val hasNextPage: Boolean = false)

    @Serializable
    private data class Media(
        val id: Long,
        val idMal: Long? = null,
        val episodes: Int? = null,
        val title: MediaTitles? = null,
        val synonyms: List<String> = emptyList(),
    ) {
        val names: List<String> get() = listOfNotNull(title?.romaji, title?.english, title?.native) + synonyms
    }

    @Serializable
    private data class MediaTitles(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
    )
}

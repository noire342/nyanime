package eu.kanade.tachiyomi.data.share

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class ContentLinksTest {
    private val anime = ContentLink(
        medium = SharedMedium.ANIME,
        sourceId = Long.MIN_VALUE + 17,
        entryUrl = "/catalogue/title?edition=2&lang=en",
        title = "Titolo di prova — seconda parte",
        sourceName = "TestSource",
    )
    private val manga = anime.copy(medium = SharedMedium.MANGA, sourceId = Long.MAX_VALUE)

    @Test
    fun `all six targets round trip without database IDs`() {
        val cases = listOf(
            anime,
            anime.copy(itemUrl = "/episode/3", itemTitle = "Episode 3").itemFromStart(),
            anime.copy(itemUrl = "/episode/3", itemTitle = "Episode 3", positionMs = 123_456),
            manga,
            manga.copy(itemUrl = "/chapter/2", itemTitle = "Chapter 2").itemFromStart(),
            manga.copy(itemUrl = "/chapter/2", itemTitle = "Chapter 2", page = 17),
        )
        cases.forEach { assertEquals(it, ContentLinks.decode(ContentLinks.encode(it))) }
    }

    @Test
    fun `source references and unicode remain opaque`() {
        val link = manga.copy(
            entryUrl = "/entry/%E6%9C%AC?variant=a+b#part",
            title = "本 · Titolo à è",
            itemUrl = "/c/2?x=1&y=2",
        )
        assertEquals(link, ContentLinks.decode(ContentLinks.encode(link)))
        assertFalse(ContentLinks.encode(link).contains(" "))
    }

    @Test
    fun `shared text returns the same link and rejects ambiguous messages`() {
        val encoded = ContentLinks.encode(anime)
        assertEquals(anime, ContentLinks.decode("Titolo di prova\n$encoded\nApri con Nyanime"))
        assertNull(ContentLinks.decode("$encoded\n$encoded"))
        assertNull(ContentLinks.extract("nyanime://watch/v1#NY1.test"))
    }

    @Test
    fun `unsupported routes and malformed encoding are rejected`() {
        val encoded = ContentLinks.encode(anime)
        listOf(
            encoded.replace("/v1#", "/v2#"),
            encoded.replace("/v1#", "/v1?key=1#"),
            encoded.replace("open/", "open:443/"),
            encoded.replace("open/", "user@open/"),
            "${ContentLinks.PREFIX}%%%",
            "${ContentLinks.PREFIX}A",
            "${ContentLinks.PREFIX}___",
            ContentLinks.PREFIX,
        ).forEach { assertNull(ContentLinks.decode(it), it.take(60)) }
        assertNull(ContentLinks.decode(ContentLinks.PREFIX + "a".repeat(ContentLinks.MAX_LINK_LENGTH)))
    }

    @Test
    fun `invalid references cannot be emitted or accepted`() {
        val invalid = listOf(
            anime.copy(version = 2),
            anime.copy(sourceId = 0),
            anime.copy(entryUrl = ""),
            anime.copy(title = ""),
            anime.copy(title = "A\nB"),
            anime.copy(entryUrl = "x".repeat(4097)),
            anime.copy(itemTitle = "Episode 1"),
            anime.copy(positionMs = 0),
            anime.copy(itemUrl = "/1", positionMs = -1),
            anime.copy(itemUrl = "/1", positionMs = ContentLinks.MAX_POSITION_MS + 1),
            anime.copy(itemUrl = "/1", page = 1),
            manga.copy(itemUrl = "/1", positionMs = 1),
            manga.copy(itemUrl = "/1", page = 0),
            manga.copy(itemUrl = "/1", page = ContentLinks.MAX_PAGE + 1),
        )
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) { ContentLinks.encode(it) }
            assertNull(ContentLinks.decode(raw(Json.encodeToString(it))))
        }
    }

    @Test
    fun `future cosmetic fields do not change versioned target resolution`() {
        val json = Json.encodeToString(anime).dropLast(1) + ",\"cosmetic\":\"future value\"}"
        assertEquals(anime, ContentLinks.decode(raw(json)))
        val unsupported = Json.encodeToString(anime).replace("\"ANIME\"", "\"UNKNOWN\"")
        assertNull(ContentLinks.decode(raw(unsupported)))
    }

    @Test
    fun `title and start choices never leak a previous position`() {
        val timed = anime.copy(itemUrl = "/1", itemTitle = "Episode 1", positionMs = 88_000)
        assertEquals(anime, timed.entryOnly())
        assertEquals(0L, timed.itemFromStart().positionMs)
        val paged = manga.copy(itemUrl = "/2", itemTitle = "Chapter 2", page = 20)
        assertEquals(manga, paged.entryOnly())
        assertEquals(1, paged.itemFromStart().page)
        assertEquals(manga, manga.itemFromStart())
    }

    @Test
    fun `emitted payload contains only the public content contract`() {
        val encoded = ContentLinks.encode(anime.copy(itemUrl = "/1", positionMs = 42_000))
        val decoded = String(Base64.getUrlDecoder().decode(encoded.substringAfter('#')), Charsets.UTF_8)
        val keys = Json.parseToJsonElement(decoded).jsonObject.keys
        assertTrue(
            keys.all {
                it in setOf(
                    "version", "medium", "sourceId", "sourceName", "entryUrl", "title",
                    "itemUrl", "itemTitle", "positionMs", "page",
                )
            },
        )
        assertFalse(
            keys.any {
                it in setOf("animeId", "mangaId", "cookie", "headers", "videoUrl", "secret", "roomCode")
            },
        )
    }

    private fun raw(json: String): String = ContentLinks.PREFIX +
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
}

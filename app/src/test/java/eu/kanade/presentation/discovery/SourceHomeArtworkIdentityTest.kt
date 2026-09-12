package eu.kanade.presentation.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover

class SourceHomeArtworkIdentityTest {
    @Test
    fun `cover refresh preserves artwork identity while source and entry changes reset it`() {
        val anime = Anime.create().copy(source = 42, url = "/series/one", thumbnailUrl = "old.jpg")
        val refreshed = anime.copy(thumbnailUrl = "new.jpg", coverLastModified = 100, title = "New title")
        assertEquals(sourceHomeArtworkIdentity(anime), sourceHomeArtworkIdentity(refreshed))
        assertNotEquals(sourceHomeArtworkIdentity(anime), sourceHomeArtworkIdentity(anime.copy(source = 43)))
        assertNotEquals(sourceHomeArtworkIdentity(anime), sourceHomeArtworkIdentity(anime.copy(url = "/series/two")))
    }

    @Test
    fun `library artwork persists across cover revision but never crosses entries`() {
        val cover = AnimeCover(7, 42, true, "old.jpg", 0)
        assertEquals(
            sourceHomeArtworkIdentity(cover),
            sourceHomeArtworkIdentity(cover.copy(url = "new.jpg", lastModified = 1)),
        )
        assertNotEquals(sourceHomeArtworkIdentity(cover), sourceHomeArtworkIdentity(cover.copy(animeId = 8)))
        assertNotEquals(sourceHomeArtworkIdentity(cover), sourceHomeArtworkIdentity(cover.copy(sourceId = 43)))
    }

    @Test
    fun `anonymous covers cannot reuse an unrelated previous image`() {
        val cover = AnimeCover(-1, 42, false, "one.jpg", 0)
        assertNotEquals(sourceHomeArtworkIdentity(cover), sourceHomeArtworkIdentity(cover.copy(url = "two.jpg")))
    }
}

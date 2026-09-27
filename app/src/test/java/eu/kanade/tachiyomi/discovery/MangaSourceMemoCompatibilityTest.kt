package eu.kanade.tachiyomi.discovery

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MangaSourceMemoCompatibilityTest {
    @Test
    fun mangaAndChapterExposeTheGenericMemoAbiUsedByExtensions() {
        assertNotNull(SManga::class.java.getMethod("setMemo", JsonObject::class.java))
        assertNotNull(SChapter::class.java.getMethod("setMemo", JsonObject::class.java))

        val memo = JsonObject(mapOf("test.id" to JsonPrimitive("42")))
        val manga = SManga.create().apply {
            url = "/work"
            title = "Test work"
            this.memo = memo
        }
        assertEquals(memo, manga.copy().memo)

        val chapter = SChapter.create().apply {
            url = "/chapter"
            name = "Test chapter"
            this.memo = memo
        }
        val copied = SChapter.create().apply { copyFrom(chapter) }
        assertEquals(memo, copied.memo)
    }
}

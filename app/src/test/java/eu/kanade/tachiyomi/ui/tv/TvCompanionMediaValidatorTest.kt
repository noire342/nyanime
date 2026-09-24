package eu.kanade.tachiyomi.ui.tv

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TvCompanionMediaValidatorTest {
    private fun media(url: String) = buildJsonObject {
        put("id", "a".repeat(32))
        put("title", "Example title")
        put("episode", "Episode 1")
        put("url", url)
        put("mimeType", "video/mp4")
        put("positionMs", 12_000)
        put("durationMs", 60_000)
        put("subtitles", buildJsonArray { })
    }

    @Test
    fun `accepts the paired phone local relay`() {
        val value = TvCompanionMediaValidator.parse(
            media("http://192.168.1.25:4583/video"),
            "192.168.1.25",
        )
        assertEquals("http://192.168.1.25:4583/video", value.url)
    }

    @Test
    fun `rejects stream addresses outside the paired phone`() {
        assertThrows(IllegalArgumentException::class.java) {
            TvCompanionMediaValidator.parse(media("http://192.168.1.26:4583/video"), "192.168.1.25")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TvCompanionMediaValidator.parse(media("https://192.168.1.25:4583/video"), "192.168.1.25")
        }
    }
}

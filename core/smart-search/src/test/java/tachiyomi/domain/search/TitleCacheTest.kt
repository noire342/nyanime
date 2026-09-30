package tachiyomi.domain.search

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TitleCacheTest {
    private val codec = TitleCacheCodec()

    @Test
    fun `corrupt oversized and unsafe data are disposable`() {
        codec.decode("{broken") shouldBe emptyList()
        codec.decode("x".repeat(TitleCacheCodec.MAX_BYTES + 1)) shouldBe emptyList()
        codec.sanitize(SearchTitle("test", "scheme://opaque", SearchMedium.MANGA)) shouldBe null
        codec.sanitize(SearchTitle("test", "Synthetic Volume", SearchMedium.MANGA, listOf("Alias", "Alias", "x\n")))
            ?.aliases shouldBe listOf("Alias")
    }

    @Test
    fun `cache is bounded recoverable and rebuilds the runtime index`() {
        val records = (0..5_010).map { SearchTitle("test:$it", "Synthetic Volume $it", SearchMedium.MANGA) }
        val decoded = codec.decode(codec.encode(records))
        decoded.size shouldBe TitleCacheCodec.CAPACITY
        decoded.first().key shouldBe "test:11"
        val index = SymSpellTitleIndex()
        index.replace(decoded)
        LexicalTitleMatcher().rank("Synthetc Volume 4920", index.candidates("Synthetc Volume 4920", SearchMedium.MANGA))
            .first().item.key shouldBe "test:4920"
        index.clear()
        index.candidates("Synthetc Volume 4920", SearchMedium.MANGA) shouldBe emptyList()
    }
}

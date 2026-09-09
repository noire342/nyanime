package eu.kanade.tachiyomi.extension

import eu.kanade.domain.extension.ExtensionTrustPolicy
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.source.service.SourcePreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionTrustPolicyTest {
    @Test
    fun automaticTrustDoesNotReadOrWriteExplicitConsent() = runBlocking {
        assertTrue(ExtensionTrustPolicy.isTrusted(true, listOf("signature")) { error("No consent lookup expected") })
    }

    @Test
    fun unsignedOrMalformedPackagesRemainUntrustedInBothModes() = runBlocking {
        for (automatic in listOf(true, false)) {
            for (signatures in listOf(emptyList(), listOf(""), listOf("known", " "))) {
                assertFalse(ExtensionTrustPolicy.isTrusted(automatic, signatures) { error("Invalid signatures") })
            }
        }
    }

    @Test
    fun disablingAutomaticTrustRestoresExplicitDecision() = runBlocking {
        assertTrue(ExtensionTrustPolicy.isTrusted(true, listOf("new")) { false })
        assertFalse(ExtensionTrustPolicy.isTrusted(false, listOf("new")) { false })
        assertTrue(ExtensionTrustPolicy.isTrusted(false, listOf("known")) { true })
    }

    @Test
    fun bothAnimeAndMangaInteractorsUseAutomaticPolicyWithoutRepositoryAccess() = runBlocking {
        val preferences = mockk<SourcePreferences>()
        every { preferences.automaticallyTrustExtensions().get() } returns true
        val anime = TrustAnimeExtension(mockk(), preferences)
        val manga = TrustMangaExtension(mockk(), preferences)
        assertTrue(anime.isTrusted(mockk(), listOf("new-anime-key")))
        assertTrue(manga.isTrusted(mockk(), listOf("new-manga-key")))
        assertFalse(anime.isTrusted(mockk(), emptyList()))
        assertFalse(manga.isTrusted(mockk(), emptyList()))
    }
}

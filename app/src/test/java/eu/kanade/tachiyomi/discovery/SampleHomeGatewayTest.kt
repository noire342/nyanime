package eu.kanade.tachiyomi.discovery

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.discovery.DiscoverySourceService
import eu.kanade.tachiyomi.data.discovery.SampleHomeFilters
import eu.kanade.tachiyomi.data.discovery.SampleHomeGateway
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

class SampleHomeGatewayTest {
    private val manager = mockk<AnimeSourceManager>()
    private val extensions = mockk<AnimeExtensionManager>()
    private val visibility = mockk<DiscoverySourceService>()
    private val preferences = mockk<SourcePreferences>()
    private val base = mockk<BasePreferences>()
    private val incognito = mockk<GetAnimeIncognitoState>()
    private val toLocal = mockk<NetworkToLocalAnime>()
    private val engine = mockk<AnimeSource>()
    private val extension = mockk<AnimeExtension.Installed>()
    private val installed = MutableStateFlow(listOf(extension))
    private val gateway = SampleHomeGateway(manager, extensions, visibility, preferences, base, incognito, toLocal)

    @BeforeEach
    fun prepare() {
        every { manager.isInitialized } returns MutableStateFlow(true)
        every { extensions.installedExtensionsFlow } returns installed
        every { extension.pkgName } returns SampleHomeFilters.PACKAGE
        every { extension.versionCode } returns 1L
        every { extension.versionName } returns "16.1"
        every { extension.sources } returns listOf(engine)
        every { engine.id } returns 42L
        every { engine.lang } returns "it"
        every { engine.getFilterList() } answers { SampleHomeFiltersTest.filters() }
        every { manager.get(42) } returns engine
        every { visibility.isEnabled(engine) } returns true
        every { base.downloadedOnly().get() } returns false
        every { incognito.await(any()) } returns false
    }

    @Test
    fun matchingUsesInstalledPackageNotDisplayNameOrUntrustedEntries() {
        assertEquals(42L, gateway.currentAccess().source?.id)
        every { extension.pkgName } returns "another.extension"
        assertNull(gateway.currentAccess().source)
        installed.value = emptyList()
        assertNull(gateway.currentAccess().source)
    }

    @Test
    fun hiddenSourceOrDisabledLanguageCannotAppear() {
        every { visibility.isEnabled(engine) } returns false
        assertNull(gateway.currentAccess().source)
    }

    @Test
    fun ambiguousSourceIdentityIsNeverGuessed() {
        every { extension.sources } returns listOf(engine, engine)
        assertNull(gateway.currentAccess().source)
    }

    @Test
    fun accessReflectsIncognitoAndDownloadOnly() {
        every { incognito.await(42L) } returns true
        every { base.downloadedOnly().get() } returns true
        assertTrue(gateway.currentAccess().isPrivate)
        assertTrue(gateway.currentAccess().offline)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { gateway.fetch(gateway.currentAccess(), SourceHomeRequest("popular")) }
        }
        coVerify(exactly = 0) { engine.getSearchAnime(any(), any(), any()) }
    }

    @Test
    fun categoriesPaginationAndSearchUseExtensionApiAndPreserveLocalIdentity() = runBlocking {
        val sourceAnime = SAnime.create().apply {
            title = "Cartone"
            url = "/cartoni/index.php?cartone=test"
        }
        val local = Anime.create().copy(id = 123, source = 42, url = sourceAnime.url, title = sourceAnime.title)
        coEvery { toLocal.await(any()) } returns local
        coEvery { engine.getSearchAnime(2, "test", any()) } answers {
            assertEquals("Categoria A", SampleHomeFiltersTest.values(thirdArg())["Categoria"])
            AnimesPage(listOf(sourceAnime), false)
        }
        val page = gateway.fetch(gateway.currentAccess(), SourceHomeRequest("category:Categoria A", 2, " test "))
        assertEquals(local, page.items.single())
        assertEquals(123L, page.items.single().id)
        assertEquals(false, page.hasNextPage)
    }
}

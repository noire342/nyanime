package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tachiyomi.domain.discovery.SourceHomeAccess
import tachiyomi.domain.discovery.SourceHomeGateway
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.discovery.SourceHomeSource
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.io.IOException

class SampleHomeGateway(
    private val manager: AnimeSourceManager,
    private val extensions: AnimeExtensionManager,
    private val visibility: DiscoverySourceService,
    private val preferences: SourcePreferences,
    private val base: BasePreferences,
    private val incognito: GetAnimeIncognitoState,
    private val toLocal: NetworkToLocalAnime,
) : SourceHomeGateway {
    private val requests = Semaphore(2)

    override fun observeAccess() = combine(
        combine(manager.sources, manager.isInitialized, extensions.installedExtensionsFlow) { _, _, _ -> Unit },
        combine(
            preferences.disabledAnimeSources().changes(),
            preferences.enabledLanguages().changes(),
            preferences.showNsfwSource().changes(),
        ) { _, _, _ -> Unit },
        combine(
            base.downloadedOnly().changes(),
            base.incognitoMode().changes(),
            preferences.incognitoAnimeExtensions().changes(),
        ) { _, _, _ -> Unit },
    ) { _, _, _ -> currentAccess() }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun currentAccess(): SourceHomeAccess {
        val offline = base.downloadedOnly().get()
        val isPrivate = incognito.await(null)
        if (!manager.isInitialized.value) {
            return SourceHomeAccess(
                loading = true,
                offline = offline,
                isPrivate = isPrivate,
            )
        }
        val extension = extensions.installedExtensionsFlow.value.singleOrNull {
            it.pkgName == SampleHomeFilters.PACKAGE
        }
        val source = extension?.sources?.singleOrNull {
            it.lang == "it" && manager.get(it.id) != null && visibility.isEnabled(it)
        } ?: return SourceHomeAccess(offline = offline, isPrivate = isPrivate)
        return try {
            val filters = source.getFilterList()
            SourceHomeAccess(
                source = SourceHomeSource(
                    source.id,
                    "${extension.versionCode}:${extension.versionName}",
                    SampleHomeFilters.sections(filters),
                    SampleHomeFilters.categories(filters),
                ),
                offline = offline,
                isPrivate = incognito.await(source.id),
            )
        } catch (e: Exception) {
            SourceHomeAccess(offline = offline, isPrivate = isPrivate, error = "Aggiorna l’estensione TestSource")
        }
    }

    override suspend fun fetch(access: SourceHomeAccess, request: SourceHomeRequest) = withContext(Dispatchers.IO) {
        requests.withPermit {
            check(!access.offline && access == currentAccess()) { "Fonte non disponibile" }
            val definition = requireNotNull(access.source)
            val source = manager.get(definition.id) ?: error("TestSource non disponibile")
            val section = if (request.sectionId == SampleHomeFilters.SEARCH) {
                SampleHomeFilters.search()
            } else {
                (definition.sections + definition.categories).firstOrNull { it.id == request.sectionId }
                    ?: error("La sezione non è più supportata: aggiorna la Home")
            }
            // Fresh filter instances per request: concurrent shelves must never change each other's selection.
            val filters = SampleHomeFilters.apply(source.getFilterList(), section)
            try {
                withTimeout(30_000) {
                    val page = source.getSearchAnime(request.page, request.query.trim(), filters)
                    check(access == currentAccess()) { "La fonte è stata disabilitata" }
                    SourceHomePage(
                        page.animes.map { toLocal.await(it.toDomainAnime(source.id)) }.distinctBy { it.id },
                        page.hasNextPage,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                throw IOException("TestSource non ha risposto in tempo. Riprova.", e)
            }
        }
    }
}

package tachiyomi.domain.discovery

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.anime.model.Anime

/** Source identities stay separate from public catalogue identities. */
data class SourceHomeSection(
    val id: String,
    val title: String,
    val selections: Map<String, String>,
    val layout: String = "posters",
)

data class SourceHomeSource(
    val id: Long,
    val revision: String,
    val sections: List<SourceHomeSection>,
    val categories: List<SourceHomeSection>,
    val key: String = id.toString(),
    val title: String = "Home",
    val sourceName: String = "",
    val language: String = "",
    val search: SourceHomeSection? = null,
    val homeId: String = key,
    val primary: Boolean = false,
)

data class SourceHomeListing(val loading: Boolean = true, val homes: List<SourceHomeSource> = emptyList()) {
    val groups: List<SourceHomeGroup> get() = homes.groupBy { it.homeId }.map { (id, providers) ->
        val ordered = providers.sortedBy { it.key }
        SourceHomeGroup(id, ordered.first().title, ordered)
    }.sortedWith(compareBy({ it.title }, { it.id }))
}

/** A content kind can be provided by several extensions; concrete source identities never get merged. */
data class SourceHomeGroup(val id: String, val title: String, val providers: List<SourceHomeSource>) {
    data class Section(val id: String, val title: String, val layout: String = "posters")
    val primary get() = providers.any { it.primary }
    val sections get() = providers.flatMap {
        it.sections
    }.distinctBy { it.id }.map { Section(it.id, it.title, it.layout) }
    val categories get() = providers.flatMap { it.categories }.distinctBy { it.id }.map { Section(it.id, it.title) }
    val searchable get() = providers.any { it.search != null }
    val sourceIds get() = providers.map { it.id }.toSet()
    fun sourceLabel(sourceId: Long) = providers.firstOrNull { it.id == sourceId }?.let {
        "${it.sourceName} · ${it.language.uppercase()}"
    }.orEmpty()
}

data class SourceHomeGroupAccess(
    val group: SourceHomeGroup? = null,
    val providers: List<SourceHomeAccess> = emptyList(),
    val loading: Boolean = false,
    val offline: Boolean = false,
)

interface SourceHomeGroupRepository {
    fun observe(
        access: SourceHomeGroupAccess,
        request: SourceHomeRequest,
        refresh: Boolean = false,
    ): Flow<SectionState<SourceHomePage>>
}

data class SourceHomeAccess(
    val source: SourceHomeSource? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val isPrivate: Boolean = false,
    val error: String? = null,
)

data class SourceHomeRequest(val sectionId: String, val page: Int = 1, val query: String = "") {
    init {
        require(page > 0)
    }

    companion object {
        const val SEARCH = "search"
    }
}

data class SourceHomePage(val items: List<Anime>, val hasNextPage: Boolean)

interface SourceHomeGateway {
    fun observeAccess(): Flow<SourceHomeAccess>
    fun currentAccess(): SourceHomeAccess
    suspend fun fetch(access: SourceHomeAccess, request: SourceHomeRequest): SourceHomePage
}

interface SourceHomeRepository {
    fun observe(
        access: SourceHomeAccess,
        request: SourceHomeRequest,
        refresh: Boolean = false,
    ): Flow<SectionState<SourceHomePage>>
}

package tachiyomi.domain.discovery

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.anime.model.Anime

/** Source identities stay separate from public catalogue identities. */
data class SourceHomeSection(val id: String, val title: String, val selections: Map<String, String>)

data class SourceHomeSource(
    val id: Long,
    val revision: String,
    val sections: List<SourceHomeSection>,
    val categories: List<SourceHomeSection>,
)

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

package eu.kanade.presentation.discovery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.entries.anime.model.Anime
import androidx.compose.ui.res.stringResource as androidStringResource

/** One complete featured section: its browse action belongs to the carousel, never to an empty row. */
@Composable
fun SourceFeaturedSection(
    state: SectionState<SourceHomePage>,
    title: String,
    refreshKey: Int = 0,
    limit: Int? = null,
    onBrowse: (() -> Unit)? = null,
    onRetry: () -> Unit,
    onOpen: (Anime) -> Unit,
    onSources: ((Anime) -> Unit)? = null,
    autoplay: Boolean = false,
) {
    val items = state.data?.items.orEmpty().let { if (limit == null) it else it.take(limit) }
    Column {
        HomeLoadingTransition(
            loading = state.awaitingContent,
            placeholder = {
                HomeHeroSkeleton(withBrowseAction = onBrowse != null, withSourceAction = onSources != null)
            },
        ) {
            if (items.isNotEmpty()) {
                SourceFeaturedCarousel(
                    items,
                    refreshKey,
                    state.data?.title ?: title,
                    onBrowse,
                    onSources,
                    autoplay,
                    onOpen,
                )
            } else if (!state.loading && state.error == null) {
                Text(androidStringResource(R.string.home_featured_empty), Modifier.padding(16.dp))
            }
        }
        LoadNotice(state.loading, state.error, state.stale, showLoadingIndicator = false, retry = onRetry)
    }
}

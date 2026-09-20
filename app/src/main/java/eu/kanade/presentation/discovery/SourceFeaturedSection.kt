package eu.kanade.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.discovery.SectionState
import tachiyomi.domain.discovery.SourceHomePage
import tachiyomi.domain.entries.anime.model.Anime

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
) {
    val items = state.data?.items.orEmpty().let { if (limit == null) it else it.take(limit) }
    Column {
        if (items.isNotEmpty()) {
            SourceFeaturedCarousel(items, refreshKey, state.data?.title ?: title, onBrowse, onOpen)
        } else if (state.loading) {
            Box(Modifier.fillMaxWidth().height(320.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        } else if (state.error == null) {
            Text("Nessun titolo in evidenza al momento", Modifier.padding(16.dp))
        }
        LoadNotice(state.loading, state.error, state.stale, onRetry)
    }
}

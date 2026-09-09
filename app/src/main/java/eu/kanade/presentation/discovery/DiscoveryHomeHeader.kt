package eu.kanade.presentation.discovery

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.domain.discovery.SourceHomeGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryHomeHeader(
    selectedHome: String?,
    onSelect: (String?) -> Unit,
    onBack: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onRefresh: () -> Unit,
    homes: List<SourceHomeGroup>,
) {
    TopAppBar(
        title = {
            if (homes.isNotEmpty()) {
                HomeContentSwitch(selectedHome, homes, onSelect)
            } else {
                Text("Home", style = MaterialTheme.typography.headlineMedium)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro") }
            }
        },
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Outlined.Search,
                        homes.firstOrNull { it.id == selectedHome }?.let { "Cerca ${it.title}" } ?: "Cerca anime",
                    )
                }
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Aggiorna Home") }
        },
    )
}

/** Compact, single-tap navigation. State remains owned by the Home, not this visual control. */
@Composable
private fun HomeContentSwitch(selectedHome: String?, homes: List<SourceHomeGroup>, onSelect: (String?) -> Unit) {
    val shape = RoundedCornerShape(50)
    val choices = listOf(null to "Anime") +
        homes.map { home ->
            home.id to home.title
        }
    val scroll = rememberLazyListState()
    LaunchedEffect(selectedHome, choices) {
        scroll.animateScrollToItem(choices.indexOfFirst { it.first == selectedHome }.coerceAtLeast(0))
    }
    LazyRow(
        Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup(),
        state = scroll,
    ) {
        items(choices, key = { it.first ?: "anime" }) { (value, label) ->
            val selected = selectedHome == value
            val background by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                label = "Home selection background",
            )
            val foreground by animateColorAsState(
                if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "Home selection text",
            )
            Box(
                Modifier
                    .widthIn(min = 80.dp, max = 200.dp)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(background)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(value) })
                    .semantics { contentDescription = "Home $label" }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = foreground,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AnimeHomeHeaderPreview() {
    TachiyomiPreviewTheme {
        DiscoveryHomeHeader(null, {}, null, {}, {}, homes = previewHomes())
    }
}

@PreviewLightDark
@Preview(widthDp = 320, fontScale = 1.5f)
@Composable
private fun CartoonsHomeHeaderPreview() {
    TachiyomiPreviewTheme {
        DiscoveryHomeHeader("1", {}, {}, {}, {}, homes = previewHomes())
    }
}

@PreviewLightDark
@Composable
private fun HomeWithoutExtensionsPreview() {
    TachiyomiPreviewTheme {
        DiscoveryHomeHeader(null, {}, null, {}, {}, homes = emptyList())
    }
}

private fun previewHomes() = listOf("Cartoni", "Film").mapIndexed { index, title ->
    SourceHomeGroup((index + 1).toString(), title, emptyList())
}

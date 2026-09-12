package eu.kanade.presentation.discovery

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.LocalNyanimeStyle
import eu.kanade.presentation.theme.NyanimeWordmark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.domain.discovery.SourceHomeGroup

@Composable
fun DiscoveryHomeHeader(
    selectedHome: String?,
    onSelect: (String?) -> Unit,
    onBack: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onRefresh: () -> Unit,
    homes: List<SourceHomeGroup>,
) {
    if (!LocalNyanimeStyle.current) {
        return eu.kanade.presentation.discovery.legacy.DiscoveryHomeHeader(
            selectedHome,
            onSelect,
            onBack,
            onSearch,
            onRefresh,
            homes,
        )
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro") }
                }
                NyanimeWordmark(Modifier.weight(1f).padding(start = 4.dp))
                if (onSearch != null) {
                    IconButton(onClick = onSearch) {
                        Icon(
                            Icons.Outlined.Search,
                            "Cerca " + (homes.firstOrNull { it.id == selectedHome }?.title ?: "anime"),
                        )
                    }
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Opzioni Home") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Aggiorna Home") },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                            onClick = {
                                menu = false
                                onRefresh()
                            },
                        )
                    }
                }
            }
            HomeContentSwitch(selectedHome, homes, onSelect)
        }
    }
}

/** Availability and selection remain owned by the generic extension Home contract. */
@Composable
private fun HomeContentSwitch(selectedHome: String?, homes: List<SourceHomeGroup>, onSelect: (String?) -> Unit) {
    val choices = (if (homes.any { it.primary }) emptyList() else listOf(null to "Anime")) +
        homes.sortedWith(compareByDescending<SourceHomeGroup> { it.primary }.thenBy { it.title }).map { home ->
            home.id to home.title
        }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewportWidth = maxWidth
        Row(
            Modifier.horizontalScroll(rememberScrollState()).widthIn(min = viewportWidth)
                .selectableGroup().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        ) {
            choices.forEach { (value, label) ->
                key(value) {
                    val bringIntoView = remember { BringIntoViewRequester() }
                    val selected = selectedHome == value
                    val indicatorWidth by animateDpAsState(
                        if (selected) 32.dp else 0.dp,
                        tween(220),
                        label = "homeIndicator",
                    )
                    val labelColor by animateColorAsState(
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        tween(180),
                        label = "homeLabel",
                    )
                    LaunchedEffect(selected, viewportWidth) { if (selected) bringIntoView.bringIntoView() }
                    Column(
                        Modifier.widthIn(min = 64.dp, max = 220.dp).bringIntoViewRequester(bringIntoView)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(value) })
                            .semantics { contentDescription = "Home $label" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = labelColor,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            Modifier.width(
                                indicatorWidth,
                            ).height(
                                3.dp,
                            ).background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Preview(widthDp = 320, fontScale = 1.5f)
@Composable
private fun NyanimeHomeHeaderPreview() {
    TachiyomiPreviewTheme {
        DiscoveryHomeHeader(null, {}, null, {}, {}, homes = listOf(SourceHomeGroup("cartoons", "Cartoni", emptyList())))
    }
}

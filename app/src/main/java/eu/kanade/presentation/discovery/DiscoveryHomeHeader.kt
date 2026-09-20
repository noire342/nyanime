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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.motion.modernMotionEnabled
import eu.kanade.presentation.motion.posterForeground
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
    logo: SourceHomeLogo? = null,
    artworkRefreshKey: Int = 0,
) {
    if (!LocalNyanimeStyle.current) {
        return eu.kanade.presentation.discovery.legacy.DiscoveryHomeHeader(
            selectedHome,
            onSelect,
            onBack,
            onSearch,
            onRefresh,
            homes,
            logo,
            artworkRefreshKey,
        )
    }
    Surface(modifier = Modifier.posterForeground(zIndex = 3f), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                // Keep the wordmark and touch targets intact on narrow screens and with large text.
                val stacked = maxWidth < 356.dp ||
                    LocalDensity.current.fontScale > 1.15f ||
                    (onBack != null && maxWidth < 416.dp)
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro") }
                        }
                        SourceHomeWordmark(
                            logo,
                            Modifier.weight(1f).padding(start = 4.dp),
                            refreshKey = artworkRefreshKey,
                        )
                        if (!stacked) HomeHeaderActions(onSearch, selectedHome, homes)
                    }
                    if (stacked) {
                        HomeHeaderActions(
                            onSearch,
                            selectedHome,
                            homes,
                            Modifier.align(Alignment.End).padding(bottom = 4.dp),
                        )
                    }
                }
            }
            HomeContentSwitch(selectedHome, homes, onSelect)
        }
    }
}

@Composable
private fun HomeHeaderActions(
    onSearch: (() -> Unit)?,
    selectedHome: String?,
    homes: List<SourceHomeGroup>,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        eu.kanade.tachiyomi.ui.watch.WatchTogetherButton()
        if (onSearch != null) {
            Surface(
                onClick = onSearch,
                modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 48.dp).semantics {
                    role = Role.Button
                    contentDescription = "Cerca " + (homes.firstOrNull { it.id == selectedHome }?.title ?: "anime")
                },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Cerca", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        eu.kanade.tachiyomi.ui.community.CommunityAvatarButton()
    }
}

/** Availability and selection remain owned by the generic extension Home contract. */
@Composable
private fun HomeContentSwitch(selectedHome: String?, homes: List<SourceHomeGroup>, onSelect: (String?) -> Unit) {
    val motion = modernMotionEnabled()
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
                        tween(if (motion) 220 else 0),
                        label = "homeIndicator",
                    )
                    val labelColor by animateColorAsState(
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        tween(if (motion) 180 else 0),
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
                                fontWeight = FontWeight.SemiBold,
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

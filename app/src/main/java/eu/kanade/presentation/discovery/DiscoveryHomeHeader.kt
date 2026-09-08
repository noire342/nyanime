package eu.kanade.presentation.discovery

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryHomeHeader(
    cartoons: Boolean,
    onSelect: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onRefresh: () -> Unit,
) {
    TopAppBar(
        title = { HomeContentSwitch(cartoons, onSelect) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro") }
            }
        },
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, if (cartoons) "Cerca cartoni" else "Cerca anime")
                }
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Aggiorna Home") }
        },
    )
}

/** Compact, single-tap navigation. State remains owned by the Home, not this visual control. */
@Composable
private fun HomeContentSwitch(cartoons: Boolean, onSelect: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .widthIn(max = 240.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        listOf(false to "Anime", true to "Cartoni").forEach { (value, label) ->
            val selected = cartoons == value
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
                    .weight(1f)
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
        DiscoveryHomeHeader(false, {}, null, {}, {})
    }
}

@PreviewLightDark
@Preview(widthDp = 320, fontScale = 1.5f)
@Composable
private fun CartoonsHomeHeaderPreview() {
    TachiyomiPreviewTheme {
        DiscoveryHomeHeader(true, {}, {}, {}, {})
    }
}

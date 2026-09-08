package eu.kanade.presentation.discovery

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryHomeHeader(
    cartoons: Boolean,
    onSelect: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onRefresh: () -> Unit,
) {
    Column {
        TopAppBar(
            title = { Text("Home", style = MaterialTheme.typography.headlineMedium) },
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
        SecondaryTabRow(selectedTabIndex = if (cartoons) 1 else 0) {
            Tab(selected = !cartoons, onClick = { onSelect(false) }, text = { Text("Anime") })
            Tab(selected = cartoons, onClick = { onSelect(true) }, text = { Text("Cartoni") })
        }
    }
}

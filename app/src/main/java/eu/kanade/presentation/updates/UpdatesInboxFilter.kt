package eu.kanade.presentation.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdatesInboxFilter(
    pending: Boolean,
    pendingCount: Int,
    allCount: Int,
    isAnime: Boolean,
    onPendingChange: (Boolean) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(listOf(true, false)) { inbox ->
            FilterChip(
                selected = pending == inbox,
                onClick = { onPendingChange(inbox) },
                label = {
                    Text(
                        if (inbox) {
                            "${if (isAnime) "Da vedere" else "Da leggere"} · $pendingCount titoli"
                        } else {
                            "Tutti · $allCount ${if (isAnime) "episodi" else "capitoli"}"
                        },
                    )
                },
            )
        }
    }
}

@Composable
fun UpdatesInboxEmpty(isAnime: Boolean, modifier: Modifier = Modifier, onRefresh: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("Nessuna novità per ora", style = MaterialTheme.typography.titleLarge)
        Text(
            if (isAnime) {
                "Qui trovi i nuovi episodi dei titoli che segui o hai guardato."
            } else {
                "Qui trovi i nuovi capitoli dei titoli che segui o hai letto."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRefresh) { Text("Controlla novità") }
    }
}

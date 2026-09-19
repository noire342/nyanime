package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.anime.components.UltraAccent
import eu.kanade.presentation.entries.anime.components.UltraStatusRow
import eu.kanade.presentation.entries.anime.components.UltraTaskSheet
import eu.kanade.tachiyomi.data.download.anime.ultra.UltraDownloads
import eu.kanade.tachiyomi.data.download.anime.ultra.UltraPhase
import eu.kanade.tachiyomi.data.download.anime.ultra.UltraTask
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import kotlinx.coroutines.CancellationException

@Composable
internal fun UltraQueueDialog(animeId: Long? = null, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        UltraQueueContent(animeId = animeId, modifier = Modifier.heightIn(max = 640.dp))
    }
}

@Composable
internal fun UltraQueueContent(animeId: Long? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var retry by remember { mutableStateOf(0) }
    val result by produceState<Result<List<UltraTask>>?>(null, retry) {
        value = null
        try {
            UltraDownloads.observe(context).collect { value = Result.success(it) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            value = Result.failure(error)
        }
    }
    val jobs = result?.getOrNull().orEmpty().filter {
        (animeId == null || it.animeId == animeId) &&
            (animeId != null || it.phase != UltraPhase.AVAILABLE)
    }
        .sortedBy { if (it.active || it.phase == UltraPhase.PAUSED) 0 else 1 }
    var selected by remember { mutableStateOf<UltraTask?>(null) }
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ultra, con calma", color = UltraAccent, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Qualità A+ HQ, senza fretta. Il telefono e la riproduzione hanno la precedenza.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "I video restano nella scheda del titolo. Tocca un episodio per gestirlo o esportarne una copia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (result == null || result?.isFailure == true) {
            item {
                Text(
                    if (result == null) {
                        "Recupero dei download…"
                    } else {
                        "Non riesco a leggere lo stato Ultra. I video originali restano disponibili."
                    },
                )
                if (result?.isFailure == true) TextButton(onClick = { retry++ }) { Text("Riprova") }
            }
        } else if (jobs.isEmpty()) {
            item {
                Text(
                    "Nessuna elaborazione Ultra. Scarica un episodio, poi scegli «Prepara una copia Ultra» dalla sua scheda. Puoi anche attivare Ultra dopo il download nelle impostazioni.",
                    Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(jobs, key = { it.key }) { task ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall)
                UltraStatusRow(task, onClick = { selected = task })
            }
        }
    }
    selected?.let { task ->
        UltraTaskSheet(
            task,
            onDismiss = { selected = null },
            onOpenTitle = if (task.animeId > 0) {
                {
                    NotificationReceiver.openAnimeEntryPendingActivity(context, task.animeId).send()
                    selected = null
                }
            } else {
                null
            },
        )
    }
}

package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import eu.kanade.tachiyomi.data.download.anime.ultra.UltraDownloadWorker

@Composable
internal fun UltraQueueDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context) }
    val flow = remember { manager.getWorkInfosByTagFlow(UltraDownloadWorker.TAG) }
    val jobs by flow.collectAsState(emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anime4K Ultra", color = Color(0xFFE477FF)) },
        text = {
            if (jobs.isEmpty()) {
                Text("Qui compariranno le elaborazioni dei prossimi episodi scaricati con Ultra attivo.")
            } else {
                LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(jobs.sortedBy { it.state.isFinished }, key = { it.id }) { job ->
                        val output = job.outputData
                        val title = job.tags.firstOrNull {
                            it.startsWith("ultra-title:")
                        }?.removePrefix("ultra-title:").orEmpty()
                        val error = output.getString(UltraDownloadWorker.ERROR).orEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            val status = when (job.state) {
                                WorkInfo.State.RUNNING -> job.progress.getString(UltraDownloadWorker.STAGE)
                                    ?: "Preparazione"
                                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                                    "In coda · attesa di caricatore, batteria o conversione precedente"
                                }
                                WorkInfo.State.CANCELLED -> "Annullato · originale disponibile"
                                else -> if (error.isNotBlank()) {
                                    "$error\nL'originale è disponibile."
                                } else {
                                    output.getString(UltraDownloadWorker.STAGE) ?: "Interrotto"
                                }
                            }
                            Text(status, style = MaterialTheme.typography.bodySmall)
                            if (job.state == WorkInfo.State.RUNNING) {
                                LinearProgressIndicator(
                                    progress = { job.progress.getInt(UltraDownloadWorker.PROGRESS, 0) / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFFE477FF),
                                )
                            }
                            Row {
                                if (!job.state.isFinished) {
                                    TextButton(onClick = { manager.cancelWorkById(job.id) }) { Text("Annulla") }
                                } else if (error.isNotBlank() ||
                                    job.state == WorkInfo.State.CANCELLED ||
                                    job.state == WorkInfo.State.FAILED
                                ) {
                                    val folder = output.getString(UltraDownloadWorker.FOLDER)
                                        ?: job.tags.firstOrNull { it.startsWith("ultra-folder:") }
                                            ?.removePrefix("ultra-folder:")
                                    folder?.let { uri ->
                                        TextButton(onClick = {
                                            UltraDownloadWorker.enqueue(context, uri.toUri(), title)
                                        }) { Text("Riprova") }
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        dismissButton = {
            if (jobs.any { !it.state.isFinished }) {
                TextButton(onClick = { manager.cancelAllWorkByTag(UltraDownloadWorker.TAG) }) { Text("Annulla tutti") }
            }
        },
    )
}

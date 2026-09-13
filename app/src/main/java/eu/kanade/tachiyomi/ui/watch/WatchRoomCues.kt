package eu.kanade.tachiyomi.ui.watch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.common.BitMatrix
import eu.kanade.presentation.discovery.ArtworkPlaceholder
import eu.kanade.tachiyomi.data.watch.WatchQr
import eu.kanade.tachiyomi.data.watch.WatchRoomState
import eu.kanade.tachiyomi.ui.player.controls.components.NextEpisodeCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WatchQrDialog(link: String, onDismiss: () -> Unit) {
    val matrix by produceState<BitMatrix?>(null, link) {
        value = withContext(Dispatchers.Default) { runCatching { WatchQr.encode(link) }.getOrNull() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invita con il QR") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Il tuo amico può inquadrarlo con la fotocamera e confermare l'ingresso in Nyanime.")
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)
                        .semantics { contentDescription = "QR dell'invito alla stanza" },
                ) {
                    matrix?.let { qr ->
                        val cell = size.width / qr.width
                        for (y in 0 until qr.height) {
                            for (x in 0 until qr.width) {
                                if (qr[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
                            }
                        }
                    }
                }
                Text("Puoi sempre condividere o copiare il codice.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
fun WatchRoomCues(
    room: WatchRoomState,
    onSkip: () -> Unit,
    onCancelSkip: () -> Unit,
    onNext: () -> Unit,
    onCancelNext: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    onCancelResume: () -> Unit = {},
    artwork: @Composable () -> Unit = { ArtworkPlaceholder(Modifier.sizeIn(minWidth = 64.dp, minHeight = 88.dp)) },
) {
    val controls = room.host || room.sharedControls
    Column(modifier.widthIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (room.preparingPlayback || room.resumeSeconds != null) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        room.resumeSeconds?.let { "Si riparte insieme tra $it" } ?: "Preparazione della riproduzione",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (room.preparingPlayback) {
                        Text(room.playbackPreparationMessage, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (controls) TextButton(onClick = onCancelResume) { Text("Annulla") }
                }
            }
        }
        room.skip?.let { skip ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        skip.label +
                            (
                                room.skipSeconds?.let {
                                    " tra " + it + " s"
                                } ?: ""
                                ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (controls) {
                        Button(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Salta per tutti") }
                        TextButton(onClick = onCancelSkip) { Text("Annulla il salto") }
                    }
                }
            }
        }
        room.next?.let { next ->
            NextEpisodeCard(
                seriesTitle = next.media.title,
                episodeTitle = next.media.episode,
                secondsRemaining = room.nextSeconds ?: 10,
                onPlayNow = onNext,
                onCancel = onCancelNext,
                statusText = if (room.nextSeconds == null) "Preparazione insieme…" else null,
                showActions = controls,
                playEnabled = next.deadline != null,
                reduceMotion = reduceMotion,
                artwork = artwork,
            )
        }
    }
}

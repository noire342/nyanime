package eu.kanade.presentation.reader

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.data.translation.MangaTranslationSession
import eu.kanade.tachiyomi.data.translation.TranslationViewMode

@Composable
fun MangaTranslatorDialog(session: MangaTranslationSession, onDismiss: () -> Unit) {
    val state by session.state.collectAsState()
    var editIndex by remember { mutableIntStateOf(-1) }
    var editText by remember { mutableStateOf("") }
    var saveCorrection by remember { mutableStateOf(false) }
    var showGlossary by remember { mutableStateOf(false) }
    var showDeleteModels by remember { mutableStateOf(false) }
    var glossarySource by remember { mutableStateOf("") }
    var glossaryItalian by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f).widthIn(max = 680.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Traduzione manga",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Sul dispositivo · originale intatto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Chiudi") }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Inglese → Italiano", style = MaterialTheme.typography.labelLarge)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    "Italiano" to TranslationViewMode.OVERLAY,
                                    "Ricostruita" to TranslationViewMode.RECONSTRUCTED,
                                    "Originale" to TranslationViewMode.ORIGINAL,
                                ).forEach { (label, mode) ->
                                    FilterChip(
                                        selected = state.mode == mode,
                                        onClick = { session.selectMode(mode) },
                                        enabled = !state.busy,
                                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                            FilterChip(
                                selected = state.showInReader,
                                onClick = { session.setShowInReader(!state.showInReader) },
                                label = {
                                    Text(
                                        if (state.showInReader) {
                                            "Traduzione visibile nel lettore"
                                        } else {
                                            "Mostra nel lettore"
                                        },
                                    )
                                },
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 450.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Crossfade(
                                    targetState = state.preview,
                                    animationSpec = tween(220),
                                    label = "pagina tradotta",
                                ) { image ->
                                    if (image != null) {
                                        Image(
                                            bitmap = image.asImageBitmap(),
                                            contentDescription = if (state.mode ==
                                                TranslationViewMode.ORIGINAL
                                            ) {
                                                "Pagina originale"
                                            } else {
                                                "Pagina tradotta"
                                            },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 450.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                    } else {
                                        Text("Carico la pagina…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    if (!session.ocrReady(state.language) || !session.textReady()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Modelli offline",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Il primo download richiede circa 615 MB. Poi le pagine si traducono anche senza connessione.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    if (session.modelStorageBytes() > 0 && !state.busy) {
                        TextButton(onClick = {
                            showDeleteModels = true
                        }) { Text("Libera spazio: elimina modelli e download parziali") }
                    }
                    TextButton(onClick = { showGlossary = true }) { Text("Glossario personale") }
                    state.document?.takeIf { it.regions.isNotEmpty() }?.let { document ->
                        Text(
                            "Testi riconosciuti",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        document.regions.forEachIndexed { index, region ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            region.original,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(5.dp))
                                        Text(
                                            region.translated.ifBlank { "In attesa della traduzione" },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editIndex = index
                                            editText = region.translated
                                            saveCorrection = false
                                        },
                                        enabled = !state.busy,
                                    ) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Correggi traduzione")
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Surface(tonalElevation = 6.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.error?.let { message ->
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                        }
                        if (!state.busy && state.phase.isNotBlank() && state.phase != "Pronto") {
                            Text(
                                state.phase,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        if (state.busy) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    state.phase.ifBlank { "Elaboro…" },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = session::cancel) { Text("Pausa") }
                            }
                            when {
                                state.downloadTotal > 0 -> LinearProgressIndicator(
                                    progress = { (state.downloaded.toFloat() / state.downloadTotal).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                state.chapterTotal > 0 -> LinearProgressIndicator(
                                    progress = { state.chapterPage.toFloat() / state.chapterTotal },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                else -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            if (state.downloadTotal > 0) {
                                Text(
                                    "${state.downloaded / 1_000_000} / ${state.downloadTotal / 1_000_000} MB",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        } else if (!session.ocrReady(state.language) || !session.textReady()) {
                            Button(onClick = session::installModels, modifier = Modifier.fillMaxWidth()) {
                                val label = if (session.canResumeModelDownload()) {
                                    "Riprendi download"
                                } else {
                                    "Scarica i modelli offline"
                                }
                                Text(label)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = session::translatePage,
                                    modifier = Modifier.weight(1f),
                                    enabled = state.original != null,
                                ) { Text("Traduci pagina") }
                                OutlinedButton(onClick = session::translateChapter) { Text("Capitolo") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (editIndex >= 0) {
        AlertDialog(
            onDismissRequest = { editIndex = -1 },
            title = { Text("Correggi la traduzione") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { if (it.length <= 2000) editText = it },
                        label = { Text("Testo italiano") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveCorrection, onCheckedChange = { saveCorrection = it })
                        Text("Usa questa correzione anche su altre pagine")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    session.correctRegion(editIndex, editText, saveCorrection)
                    editIndex = -1
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { editIndex = -1 }) { Text("Annulla") } },
        )
    }
    if (showGlossary) {
        AlertDialog(
            onDismissRequest = { showGlossary = false },
            title = { Text("Glossario personale") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Le correzioni esatte si applicano alle prossime pagine nella lingua selezionata.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = glossarySource,
                        onValueChange = { glossarySource = it.take(2000) },
                        label = { Text("Testo originale") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = glossaryItalian,
                        onValueChange = { glossaryItalian = it.take(2000) },
                        label = { Text("Traduzione italiana") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            session.addGlossaryEntry(glossarySource, glossaryItalian)
                            glossarySource = ""
                            glossaryItalian = ""
                        },
                        enabled = glossarySource.isNotBlank() && glossaryItalian.isNotBlank(),
                    ) { Text("Aggiungi") }
                    session.glossaryEntries().forEach { (original, italian) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    original,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(italian, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { session.removeGlossaryEntry(original) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Elimina voce")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGlossary = false }) { Text("Chiudi") } },
        )
    }
    if (showDeleteModels) {
        AlertDialog(
            onDismissRequest = { showDeleteModels = false },
            title = { Text("Eliminare i modelli offline?") },
            text = {
                Text(
                    "Libererai circa ${session.modelStorageBytes() / 1_000_000} MB. " +
                        "Le correzioni nel glossario resteranno disponibili.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteModels = false
                    session.removeModels()
                }) { Text("Elimina modelli") }
            },
            dismissButton = { TextButton(onClick = { showDeleteModels = false }) { Text("Annulla") } },
        )
    }
}

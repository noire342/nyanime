package eu.kanade.tachiyomi.ui.reading

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.reading.ReadingEdit
import eu.kanade.tachiyomi.data.reading.ReadingEditKind
import eu.kanade.tachiyomi.data.reading.ReadingPosition
import eu.kanade.tachiyomi.data.reading.ReadingRoomState
import eu.kanade.tachiyomi.data.reading.ReadingTogetherManager
import eu.kanade.tachiyomi.data.reading.ReadingTools
import eu.kanade.tachiyomi.data.watch.WatchInvite
import eu.kanade.tachiyomi.ui.watch.WatchQrDialog
import eu.kanade.tachiyomi.ui.watch.WatchTogetherPanel

val readingInkColors = listOf(0xFFFFC857, 0xFF4DDDC7, 0xFF76AEFF, 0xFFFF766E, 0xFFFFFFFF).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingRoomSheet(manager: ReadingTogetherManager, onDismiss: () -> Unit, onChooseManga: () -> Unit) {
    val room by manager.controller.state.collectAsState()
    val tools by manager.tools.collectAsState()
    val initialNavigation = remember { tools.navigation }
    androidx.compose.runtime.LaunchedEffect(tools.navigation) {
        if (tools.navigation != initialNavigation) onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (!room.active) {
            WatchTogetherPanel(onChooseVideo = onChooseManga)
        } else {
            ReadingRoomPanel(manager, onChooseManga = onChooseManga, onPlaceNote = onDismiss, onDraw = {
                manager.setDrawing(true)
                onDismiss()
            })
        }
    }
}

@Composable
fun ReadingRoomPanel(
    manager: ReadingTogetherManager,
    onChooseManga: () -> Unit,
    onDraw: () -> Unit = {},
    onPlaceNote: () -> Unit = {},
    onVideo: (() -> Unit)? = null,
) {
    val activity = LocalContext.current as? Activity
    val room by manager.controller.state.collectAsState()
    val tools by manager.tools.collectAsState()
    val saveFailed by manager.watch.roomSaveFailed.collectAsState()
    var qr by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var addingNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    val link = remember(room.invite) {
        runCatching { WatchInvite.parse(room.invite, System.currentTimeMillis()).link() }.getOrNull()
    }
    if (qr && link != null) WatchQrDialog(link) { qr = false }
    if (addingNote) {
        AlertDialog(
            onDismissRequest = { addingNote = false },
            title = { Text("Una nota sulla pagina") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scrivi la nota, poi tocca il punto del manga dove vuoi lasciarla.")
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(280) },
                        label = { Text("Nota") },
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (manager.startNote(noteText)) {
                        noteText = ""
                        addingNote = false
                        onPlaceNote()
                    }
                }, enabled = noteText.isNotBlank()) { Text("Scegli il punto") }
            },
            dismissButton = { TextButton(onClick = { addingNote = false }) { Text("Annulla") } },
        )
    }
    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text(if (room.host) "Chiudere la stanza?" else "Lasciare la stanza?") },
            text = {
                val message = if (room.host) {
                    "La stanza si chiuderà per tutti. Gli schizzi sono temporanei e verranno rimossi."
                } else {
                    "La lettura continua sul tuo telefono. Gli schizzi della stanza sono temporanei."
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.watch.controller.leave()
                    leaving = false
                }) { Text("Conferma") }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Resta") } },
        )
    }
    ReadingRoomContent(
        room, tools, manager.currentPage(),
        onJump = { if (activity != null) manager.jump(activity, it) },
        onReturn = { if (activity != null) manager.returnToOwn(activity) },
        onChooseManga = onChooseManga,
        onDraw = onDraw,
        onAddNote = { addingNote = true },
        onVisible = manager::setVisible,
        history = manager.controller.history(),
        saveFailed = saveFailed,
        onSharedVisibility = { edit, visible -> manager.controller.setVisible(edit.page, edit.id, visible) },
        onShare = {
            activity?.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Leggiamo o guardiamo insieme su Nyanime!\n${link.orEmpty()}\n\n" +
                                "Codice stanza: ${room.invite}",
                        )
                    },
                    "Invita nella stanza",
                ),
            )
        },
        onQr = { qr = true },
        onLeave = { leaving = true },
        onVideo = onVideo,
        onRetry = manager.watch.controller::retryConnection,
    )
}

@Composable
fun ReadingRoomContent(
    room: ReadingRoomState,
    tools: ReadingTools,
    current: ReadingPosition?,
    onJump: (ReadingPosition) -> Unit,
    onReturn: () -> Unit,
    onChooseManga: () -> Unit,
    onDraw: () -> Unit,
    onAddNote: () -> Unit = {},
    onVisible: (Boolean) -> Unit,
    history: List<ReadingEdit> = emptyList(),
    saveFailed: Boolean = false,
    onSharedVisibility: (ReadingEdit, Boolean) -> Unit = { _, _ -> },
    onShare: () -> Unit,
    onQr: () -> Unit,
    onLeave: () -> Unit,
    onVideo: (() -> Unit)? = null,
    onRetry: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val interventions = remember(history) {
        history.filter { it.kind in setOf(ReadingEditKind.Stroke, ReadingEditKind.Note) }
    }
    var visibleHistory by rememberSaveable { mutableStateOf(50) }
    var openedNote by remember { mutableStateOf<ReadingEdit?>(null) }
    openedNote?.let { edit ->
        AlertDialog(
            onDismissRequest = { openedNote = null },
            title = { Text("Nota nella stanza") },
            text = { Text(edit.note?.text.orEmpty()) },
            confirmButton = { TextButton(onClick = { openedNote = null }) { Text("Chiudi") } },
        )
    }
    LazyColumn(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainer)),
                    RoundedCornerShape(26.dp),
                ).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.MenuBook, null, Modifier.size(26.dp), tint = colors.onPrimaryContainer)
                    Text(
                        "Leggi insieme",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Invita nella stanza") }
                }
                Text("La stessa stanza. Ognuno al proprio ritmo.", style = MaterialTheme.typography.bodyMedium)
                Text(room.status, style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                if (saveFailed) {
                    Text(
                        "Le annotazioni sono visibili, ma non riusciamo a salvarle per il rientro. " +
                            "Tieni aperta la stanza finché il problema si risolve.",
                        color = colors.error,
                    )
                }
                if (room.relayCount == 0 ||
                    !room.connected
                ) {
                    TextButton(onClick = onRetry) { Text("Riprova connessione", color = colors.onPrimaryContainer) }
                }
            }
        }
        if (tools.opening) {
            item {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().semantics {
                        contentDescription =
                            "Apertura della pagina"
                    },
                )
            }
        }
        (tools.error ?: room.notice.takeIf { it.isNotBlank() })?.let { message ->
            item {
                Text(message, color = colors.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        tools.returnTo?.let { bookmark ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Il tuo punto è al sicuro", fontWeight = FontWeight.Bold)
                        Text(bookmark.position.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${bookmark.position.chapterName} · p. ${bookmark.position.page + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Button(onClick = onReturn, enabled = !tools.opening) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                            Text("Torna al mio punto", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        item { Text("Nella stanza", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (room.others.isEmpty()) {
            item {
                Text(
                    "Invita un amico con il codice che usi già per Guarda insieme. Non serve aprire un'altra stanza.",
                    color = colors.onSurfaceVariant,
                )
            }
        }
        items(room.members.toList().sortedBy { it.first == room.localId }, key = { it.first }) { (id, peer) ->
            val own = id == room.localId
            val position = peer.position
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(42.dp).background(colors.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                peer.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = colors.onSecondaryContainer,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                peer.name + if (own) " · Tu" else "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when {
                                    !room.connected && !own -> "Ultimo punto condiviso"
                                    peer.reading -> "Sta leggendo"
                                    else -> "Nella stanza"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    if (position != null) {
                        Text(
                            position.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = if (own) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            position.chapterName,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!own) {
                            val progress by androidx.compose.animation.core.animateFloatAsState(
                                (position.page + 1f) / position.pages,
                                animationSpec = androidx.compose.animation.core.tween(
                                    if (eu.kanade.presentation.motion.modernMotionEnabled()) 180 else 0,
                                ),
                                label = "readingProgress",
                            )
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            "Pagina ${position.page + 1} di ${position.pages}" +
                                if (!peer.reading || (!room.connected && !own)) " · ultimo punto" else "",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (!own) {
                            val same = current?.pageKey == position.pageKey
                            OutlinedButton(
                                onClick = { onJump(position) },
                                enabled =
                                !tools.opening && !same && room.connected,
                            ) {
                                Text(if (same) "Siete sulla stessa pagina" else "Raggiungi questa pagina")
                            }
                        }
                    } else {
                        Text(
                            "Può aprire un manga o guardare un video senza uscire dalla stanza.",
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Lascia un segno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Disegna sulla pagina. Gli amici lo vedranno quando la aprono. Raggiungi la loro pagina per scarabocchiare insieme.",
                    color = colors.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mostra gli schizzi", Modifier.weight(1f))
                    Switch(checked = tools.visible, onCheckedChange = onVisible)
                }
                if (current !=
                    null
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDraw, enabled = room.supported) {
                            Icon(Icons.Default.Draw, null)
                            Text("Disegna", Modifier.padding(start = 8.dp))
                        }
                        if (room.crdtEnabled) {
                            OutlinedButton(onClick = onAddNote) { Text("Lascia una nota") }
                        }
                    }
                }
                Text(
                    if (room.crdtEnabled) {
                        "I segni restano nella stanza. Puoi nasconderli solo qui oppure rimuovere e ripristinare i tuoi segni per tutti."
                    } else {
                        "Questa stanza usa ancora la modalità disegno precedente."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    "Nella stanza mostriamo la pagina intera, senza ritagliare i bordi, per allineare i disegni. Le tue preferenze tornano appena esci.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (room.crdtEnabled && interventions.isNotEmpty()) {
            item {
                Text(
                    "Interventi nella stanza",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(
                interventions.take(visibleHistory),
                key = { it.id },
            ) { edit ->
                val visible = room.strokes(edit.page).any { it.id == edit.id } ||
                    room.notes(edit.page).any { it.id == edit.id }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).then(
                            if (edit.kind ==
                                ReadingEditKind.Note
                            ) {
                                Modifier.clickable { openedNote = edit }
                            } else {
                                Modifier
                            },
                        ),
                    ) {
                        Text(
                            (
                                room.members[edit.author]?.name
                                    ?: if (edit.author == room.localId) "Tu" else "Partecipante"
                                ) +
                                " · " +
                                if (edit.kind == ReadingEditKind.Note) edit.note?.text.orEmpty() else "Schizzo",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${edit.page.chapterName} · pagina ${edit.page.page + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        if (edit.kind == ReadingEditKind.Note) {
                            Text(
                                "Tocca per leggere",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                            )
                        }
                    }
                    if (room.host || edit.author == room.localId) {
                        TextButton(onClick = { onSharedVisibility(edit, !visible) }) {
                            Text(if (visible) "Nascondi" else "Ripristina")
                        }
                    }
                }
            }
            if (interventions.size > visibleHistory) {
                item {
                    TextButton(onClick = { visibleHistory += 50 }) { Text("Mostra altri interventi") }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChooseManga) { Text("Altri manga") }
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Text("Invita", Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = onQr) { Text("Codice QR") }
                onVideo?.let { TextButton(onClick = it) { Text("Controlli video") } }
                TextButton(onClick = onLeave) { Text(if (room.host) "Chiudi stanza" else "Lascia stanza") }
            }
        }
    }
}

@Composable
fun ReadingReaderOverlay(manager: ReadingTogetherManager, menuVisible: Boolean, onOpenRoom: () -> Unit) {
    val room by manager.controller.state.collectAsState()
    val tools by manager.tools.collectAsState()
    var clear by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    if (!room.active && tools.returnTo == null) return
    if (clear) {
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("Cancellare gli schizzi?") },
            text = {
                val message = if (room.host) {
                    "Rimuovi tutti gli schizzi di questa pagina, anche quelli degli amici."
                } else {
                    "Rimuovi i tuoi schizzi da questa pagina anche per gli amici."
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.currentPage()?.let(manager.controller::clear)
                    clear = false
                }) { Text("Cancella") }
            },
            dismissButton = { TextButton(onClick = { clear = false }) { Text("Annulla") } },
        )
    }
    Box(
        Modifier.fillMaxSize().statusBarsPadding().padding(
            top = if (menuVisible) 68.dp else 8.dp,
            start = 12.dp,
            end = 12.dp,
        ),
    ) {
        if (tools.returnTo != null) {
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(bottom = if (menuVisible) 180.dp else 48.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
            ) {
                TextButton(onClick = { activity?.let(manager::returnToOwn) }, enabled = !tools.opening) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    Text("Torna al mio punto", Modifier.padding(start = 8.dp))
                }
            }
        }
        if (!room.active) return@Box
        if (tools.noteDraft != null) {
            Surface(
                Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp),
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tocca la pagina per lasciare la nota", Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = manager::finishNote) { Icon(Icons.Default.Close, "Annulla nota") }
                }
            }
        }
        if (tools.drawing) {
            Surface(
                Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp),
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Disegna", Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            manager.currentPage()?.let(manager.controller::undo)
                        }) { Icon(Icons.AutoMirrored.Filled.Undo, "Annulla l'ultimo schizzo") }
                        TextButton(onClick = { clear = true }) { Text("Pulisci") }
                        IconButton(onClick = {
                            manager.setDrawing(false)
                        }) { Icon(Icons.Default.Close, "Fine disegno") }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        readingInkColors.forEachIndexed { index, color ->
                            FilterChip(
                                selected = tools.color == index,
                                onClick = { manager.setColor(index) },
                                label = { Box(Modifier.size(18.dp).background(Color(color), CircleShape)) },
                                modifier = Modifier.semantics {
                                    contentDescription =
                                        listOf("Giallo", "Verde acqua", "Blu", "Corallo", "Bianco")[index]
                                },
                            )
                        }
                        AssistChip(onClick = { manager.setWidth(tools.width % 3 + 1) }, label = {
                            Text(
                                listOf("Fine", "Medio", "Spesso")[
                                    tools.width -
                                        1,
                                ],
                            )
                        })
                    }
                    Text(
                        "Trascina per disegnare · due dita per muovere la pagina",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        } else {
            Surface(
                Modifier.align(Alignment.TopEnd).widthIn(max = 280.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val friend = room.others.values.firstOrNull { it.reading && it.position != null }
                    Column(Modifier.weight(1f).clickable(onClick = onOpenRoom).padding(vertical = 10.dp)) {
                        Text(
                            if (room.connected) "Insieme · ${room.members.size}" else "Riconnessione…",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            friend?.let {
                                "${it.name} · p. ${it.position!!.page + 1}"
                            } ?: "Apri la stanza",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = {
                        manager.setVisible(!tools.visible)
                    }) {
                        Icon(
                            if (tools.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            if (tools.visible) "Nascondi schizzi" else "Mostra schizzi",
                        )
                    }
                    IconButton(onClick = onOpenRoom) { Icon(Icons.Default.Group, "Apri la stanza") }
                }
            }
        }
    }
}

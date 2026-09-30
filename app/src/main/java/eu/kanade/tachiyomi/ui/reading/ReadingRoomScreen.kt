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
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.reading.ReadingEdit
import eu.kanade.tachiyomi.data.reading.ReadingEditKind
import eu.kanade.tachiyomi.data.reading.ReadingPosition
import eu.kanade.tachiyomi.data.reading.ReadingRoomState
import eu.kanade.tachiyomi.data.reading.ReadingTogetherManager
import eu.kanade.tachiyomi.data.reading.ReadingTools
import eu.kanade.tachiyomi.data.watch.WatchInvite
import eu.kanade.tachiyomi.data.watch.WatchShortRooms
import eu.kanade.tachiyomi.ui.watch.WatchQrDialog
import eu.kanade.tachiyomi.ui.watch.WatchTogetherPanel
import eu.kanade.tachiyomi.ui.watch.rememberRoomText

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
    val text = rememberRoomText()
    val activity = LocalContext.current as? Activity
    val room by manager.controller.state.collectAsState()
    val tools by manager.tools.collectAsState()
    val saveFailed by manager.watch.roomSaveFailed.collectAsState()
    val short by manager.watch.shortRooms.state.collectAsState()
    var qr by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var addingNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    val link = remember(room.invite, short.code) {
        if (short.code.isNotBlank()) {
            WatchShortRooms.link(short.code)
        } else {
            runCatching { WatchInvite.parse(room.invite, System.currentTimeMillis()).link() }.getOrNull()
        }
    }
    if (qr && link != null) WatchQrDialog(link) { qr = false }
    if (addingNote) {
        AlertDialog(
            onDismissRequest = { addingNote = false },
            title = { Text(text(R.string.room_note_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text(R.string.room_note_hint))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(280) },
                        label = { Text(text(R.string.room_note)) },
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
                }, enabled = noteText.isNotBlank()) { Text(text(R.string.room_choose_point)) }
            },
            dismissButton = { TextButton(onClick = { addingNote = false }) { Text(text(R.string.room_cancel)) } },
        )
    }
    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text(if (room.host) text(R.string.room_confirm_close) else text(R.string.room_confirm_leave)) },
            text = {
                val message = if (room.host) {
                    text(R.string.room_close_reading_hint)
                } else {
                    text(R.string.room_leave_reading_hint)
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.watch.controller.leave()
                    leaving = false
                }) { Text(text(R.string.room_confirm)) }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text(text(R.string.room_stay)) } },
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
                            if (short.code.isNotBlank()) {
                                WatchShortRooms.shareText(short.code, text)
                            } else {
                                text(R.string.room_share_full_invite, link.orEmpty(), room.invite)
                            },
                        )
                    },
                    text(R.string.room_invite_room),
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
    val text = rememberRoomText()
    val colors = MaterialTheme.colorScheme
    val interventions = remember(history) {
        history.filter { it.kind in setOf(ReadingEditKind.Stroke, ReadingEditKind.Note) }
    }
    var visibleHistory by rememberSaveable { mutableStateOf(50) }
    var openedNote by remember { mutableStateOf<ReadingEdit?>(null) }
    openedNote?.let { edit ->
        AlertDialog(
            onDismissRequest = { openedNote = null },
            title = { Text(text(R.string.room_room_note)) },
            text = { Text(edit.note?.text.orEmpty()) },
            confirmButton = { TextButton(onClick = { openedNote = null }) { Text(text(R.string.room_close_dialog)) } },
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
                        text(R.string.room_read),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, text(R.string.room_invite_room)) }
                }
                Text(text(R.string.room_reading_intro), style = MaterialTheme.typography.bodyMedium)
                Text(room.status(text), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                if (saveFailed) {
                    Text(
                        text(R.string.room_reading_save_failed),
                        color = colors.error,
                    )
                }
                if (room.relayCount == 0 ||
                    !room.connected
                ) {
                    TextButton(onClick = onRetry) {
                        Text(text(R.string.room_retry_connection), color = colors.onPrimaryContainer)
                    }
                }
            }
        }
        if (tools.opening) {
            item {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().semantics {
                        contentDescription =
                            text(R.string.room_opening_page)
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
                        Text(text(R.string.room_position_safe), fontWeight = FontWeight.Bold)
                        Text(bookmark.position.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text(R.string.room_chapter_page, bookmark.position.chapterName, bookmark.position.page + 1),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Button(onClick = onReturn, enabled = !tools.opening) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                            Text(text(R.string.room_return_position), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        item {
            Text(text(R.string.room_in_room), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (room.others.isEmpty()) {
            item {
                Text(
                    text(R.string.room_reading_invite_hint),
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
                                peer.name + if (own) text(R.string.room_you_suffix) else "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when {
                                    !room.connected && !own -> text(R.string.room_last_position)
                                    peer.reading -> text(R.string.room_reading)
                                    else -> text(R.string.room_in_room)
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
                            text(R.string.room_page_count, position.page + 1, position.pages) +
                                if (!peer.reading ||
                                    (!room.connected && !own)
                                ) {
                                    text(R.string.room_last_position_suffix)
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (!own) {
                            val same = current?.pageKey == position.pageKey
                            OutlinedButton(
                                onClick = { onJump(position) },
                                enabled =
                                !tools.opening && !same && room.connected,
                            ) {
                                Text(if (same) text(R.string.room_same_page) else text(R.string.room_jump_page))
                            }
                        }
                    } else {
                        Text(
                            text(R.string.room_can_switch_content),
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text(R.string.room_leave_mark),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text(R.string.room_drawing_hint),
                    color = colors.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text(R.string.room_show_sketches), Modifier.weight(1f))
                    Switch(checked = tools.visible, onCheckedChange = onVisible)
                }
                if (current !=
                    null
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDraw, enabled = room.supported) {
                            Icon(Icons.Default.Draw, null)
                            Text(text(R.string.room_draw), Modifier.padding(start = 8.dp))
                        }
                        if (room.crdtEnabled) {
                            OutlinedButton(onClick = onAddNote) { Text(text(R.string.room_add_note)) }
                        }
                    }
                }
                Text(
                    if (room.crdtEnabled) {
                        text(R.string.room_annotations_hint)
                    } else {
                        text(R.string.room_old_drawing)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text(R.string.room_page_alignment),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (room.crdtEnabled && interventions.isNotEmpty()) {
            item {
                Text(
                    text(R.string.room_annotations),
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
                                    ?: if (edit.author ==
                                        room.localId
                                    ) {
                                        text(R.string.room_you)
                                    } else {
                                        text(R.string.room_participant)
                                    }
                                ) +
                                " · " +
                                if (edit.kind ==
                                    ReadingEditKind.Note
                                ) {
                                    edit.note?.text.orEmpty()
                                } else {
                                    text(R.string.room_sketch)
                                },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text(R.string.room_annotation_page, edit.page.chapterName, edit.page.page + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        if (edit.kind == ReadingEditKind.Note) {
                            Text(
                                text(R.string.room_read_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                            )
                        }
                    }
                    if (room.host || edit.author == room.localId) {
                        TextButton(onClick = { onSharedVisibility(edit, !visible) }) {
                            Text(if (visible) text(R.string.room_hide) else text(R.string.room_restore))
                        }
                    }
                }
            }
            if (interventions.size > visibleHistory) {
                item {
                    TextButton(onClick = { visibleHistory += 50 }) { Text(text(R.string.room_more_annotations)) }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChooseManga) { Text(text(R.string.room_other_manga)) }
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Text(text(R.string.room_invite), Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = onQr) { Text(text(R.string.room_qr_code)) }
                onVideo?.let { TextButton(onClick = it) { Text(text(R.string.room_video_controls)) } }
                TextButton(onClick = onLeave) {
                    Text(if (room.host) text(R.string.room_close_short) else text(R.string.room_leave_short))
                }
            }
        }
    }
}

@Composable
fun ReadingReaderOverlay(manager: ReadingTogetherManager, menuVisible: Boolean, onOpenRoom: () -> Unit) {
    val text = rememberRoomText()
    val room by manager.controller.state.collectAsState()
    val tools by manager.tools.collectAsState()
    var clear by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    if (!room.active && tools.returnTo == null) return
    if (clear) {
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text(text(R.string.room_clear_sketches_title)) },
            text = {
                val message = if (room.host) {
                    text(R.string.room_clear_all_hint)
                } else {
                    text(R.string.room_clear_own_hint)
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.currentPage()?.let(manager.controller::clear)
                    clear = false
                }) { Text(text(R.string.room_delete)) }
            },
            dismissButton = { TextButton(onClick = { clear = false }) { Text(text(R.string.room_cancel)) } },
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
                    Text(text(R.string.room_return_position), Modifier.padding(start = 8.dp))
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
                    Text(text(R.string.room_place_note), Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = manager::finishNote) {
                        Icon(Icons.Default.Close, text(R.string.room_cancel_note))
                    }
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
                        Text(
                            text(R.string.room_draw),
                            Modifier.weight(1f).padding(start = 8.dp),
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = {
                            manager.currentPage()?.let(manager.controller::undo)
                        }) { Icon(Icons.AutoMirrored.Filled.Undo, text(R.string.room_undo_sketch)) }
                        TextButton(onClick = { clear = true }) { Text(text(R.string.room_clear)) }
                        IconButton(onClick = {
                            manager.setDrawing(false)
                        }) { Icon(Icons.Default.Close, text(R.string.room_finish_drawing)) }
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
                                        listOf(
                                            text(R.string.room_yellow),
                                            text(R.string.room_teal),
                                            text(R.string.room_blue),
                                            text(R.string.room_coral),
                                            text(R.string.room_white),
                                        )[index]
                                },
                            )
                        }
                        AssistChip(onClick = { manager.setWidth(tools.width % 3 + 1) }, label = {
                            Text(
                                listOf(text(R.string.room_thin), text(R.string.room_medium), text(R.string.room_thick))[
                                    tools.width -
                                        1,
                                ],
                            )
                        })
                    }
                    Text(
                        text(R.string.room_draw_gestures),
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
                            if (room.connected) {
                                text(
                                    R.string.room_together_count,
                                    room.members.size,
                                )
                            } else {
                                text(R.string.room_reconnecting_short)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            friend?.let {
                                text(R.string.room_chapter_page, it.name, it.position!!.page + 1)
                            } ?: text(R.string.room_open_room),
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
                            if (tools.visible) {
                                text(
                                    R.string.room_hide_sketches,
                                )
                            } else {
                                text(R.string.room_show_sketches_short)
                            },
                        )
                    }
                    IconButton(onClick = onOpenRoom) { Icon(Icons.Default.Group, text(R.string.room_open_room)) }
                }
            }
        }
    }
}

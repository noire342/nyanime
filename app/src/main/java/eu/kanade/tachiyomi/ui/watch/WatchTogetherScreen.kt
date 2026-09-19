package eu.kanade.tachiyomi.ui.watch

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.motion.modernMotionEnabled
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.data.watch.WatchInvite
import eu.kanade.tachiyomi.data.watch.WatchOpeningState
import eu.kanade.tachiyomi.data.watch.WatchPhase
import eu.kanade.tachiyomi.data.watch.WatchProblem
import eu.kanade.tachiyomi.data.watch.WatchRoomState
import eu.kanade.tachiyomi.data.watch.WatchTogetherManager
import eu.kanade.tachiyomi.data.watch.description
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity

class WatchTogetherActivity : BaseActivity() {
    private var incomingCode by mutableStateOf("")
    private var inviteError by mutableStateOf<String?>(null)

    private fun acceptIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val parsed = runCatching { WatchInvite.codeFromLink(intent.dataString.orEmpty(), System.currentTimeMillis()) }
        incomingCode = parsed.getOrDefault("")
        inviteError = parsed.exceptionOrNull()?.message
        // Keep invitation secrets out of saved activity state and subsequent launches.
        intent.data = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptIntent(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerSecureActivity(this)
        enableEdgeToEdge()
        acceptIntent(intent)
        WatchTogetherManager.get(this).present(this)
        setContent {
            TachiyomiTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("La nostra stanza") },
                            navigationIcon = {
                                IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                            },
                        )
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                        WatchTogetherPanel(
                            onChooseVideo = {
                                if (isTaskRoot) {
                                    startActivity(
                                        Intent(
                                            this@WatchTogetherActivity,
                                            eu.kanade.tachiyomi.ui.main.MainActivity::class.java,
                                        ),
                                    )
                                }
                                finish()
                            },
                            incomingCode = incomingCode,
                            inviteError = inviteError,
                            onInviteConsumed = {
                                incomingCode = ""
                                inviteError = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatchTogetherSheet(onDismiss: () -> Unit) {
    PlayerSheet(onDismissRequest = onDismiss) { WatchTogetherPanel(onChooseVideo = onDismiss) }
}

@Composable
fun WatchTogetherButton() {
    val context = LocalContext.current
    IconButton(onClick = { context.startActivity(Intent(context, WatchTogetherActivity::class.java)) }) {
        Icon(Icons.Default.Group, "Guarda insieme")
    }
}

@Composable
fun WatchTogetherPanel(
    onChooseVideo: () -> Unit,
    incomingCode: String = "",
    inviteError: String? = null,
    onInviteConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val manager = remember { WatchTogetherManager.get(context) }
    val room by manager.controller.state.collectAsState()
    val opening by manager.opening.collectAsState()
    val preparation by manager.preparation.collectAsState()
    var showQr by remember { mutableStateOf(false) }
    val invitationLink = remember(room.invite) {
        runCatching { WatchInvite.parse(room.invite, System.currentTimeMillis()).link() }.getOrNull()
    }
    if (showQr && room.active && invitationLink != null) {
        WatchQrDialog(invitationLink) { showQr = false }
    }
    var name by rememberSaveable { mutableStateOf(manager.displayName) }
    var copied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }
    DisposableEffect(manager) {
        (context as? Activity)?.let(manager::present)
        onDispose {}
    }
    var readingPanel by rememberSaveable { mutableStateOf(false) }
    if (readingPanel && room.active) {
        eu.kanade.tachiyomi.ui.reading.ReadingRoomPanel(
            manager = eu.kanade.tachiyomi.data.reading.ReadingTogetherManager.get(context),
            onChooseManga = {
                context.startActivity(
                    Intent(context, eu.kanade.tachiyomi.ui.main.MainActivity::class.java).apply {
                        action = eu.kanade.tachiyomi.core.common.Constants.SHORTCUT_LIBRARY
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                )
            },
            onVideo = { readingPanel = false },
        )
        return
    }
    WatchTogetherContent(
        room = room, opening = opening, name = name, onName = { name = it.take(32) },
        incomingCode = incomingCode, inviteError = inviteError, preparation = preparation,
        onQr = { showQr = true },
        onRead = { readingPanel = true },
        onSkip = manager.controller::requestSkip, onCancelSkip = manager.controller::cancelSkip,
        onNext = manager.controller::playNextNow, onCancelNext = manager.controller::cancelNext,
        onExtensions = {
            context.startActivity(
                Intent(context, eu.kanade.tachiyomi.ui.main.MainActivity::class.java).apply {
                    action = eu.kanade.tachiyomi.core.common.Constants.SHORTCUT_ANIMEEXTENSIONS
                },
            )
        },
        onCreate = {
            manager.displayName = name
            manager.createRoom(name)
        },
        onJoin = { code ->
            manager.displayName = name
            if (room.active) manager.controller.leave()
            manager.controller.join(code, name)
            onInviteConsumed()
        },
        onCopy = {
            context.getSystemService(
                ClipboardManager::class.java,
            ).setPrimaryClip(ClipData.newPlainText("Codice stanza", room.invite))
            copied = true
        },
        copied = copied,
        onShare = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Guardiamo insieme su Nyanime!\n" +
                        invitationLink.orEmpty() +
                        "\n\nSe il link non si apre, usa Guarda insieme → Inserisci codice:\n" +
                        room.invite,
                )
            }
            context.startActivity(Intent.createChooser(intent, "Invita un amico"))
        },
        onTogglePlayback = {
            if (room.wantsPlayback &&
                !room.localHold
            ) {
                manager.controller.requestPause(true)
            } else {
                manager.controller.resumeByUser()
            }
        },
        onResync = {
            manager.controller.resync()
            manager.retryOpening()
            manager.retryPreparation()
        },
        onLeave = manager.controller::leave,
        onSharedControls = manager.controller::setSharedControls,
        onWaitForEveryone = manager.controller::setWaitForEveryone,
        onChooseVideo = onChooseVideo,
        onOpenPlayer = if (context is eu.kanade.tachiyomi.ui.player.PlayerActivity) {
            null
        } else {
            manager::openSelectedVideo
        },
    )
}

/** Pure UI so phone, landscape and large-text layouts can be verified without a running player. */
@Composable
fun WatchTogetherContent(
    room: WatchRoomState,
    opening: WatchOpeningState,
    name: String,
    onName: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onTogglePlayback: () -> Unit,
    onResync: () -> Unit,
    onLeave: () -> Unit,
    onSharedControls: (Boolean) -> Unit,
    onWaitForEveryone: (Boolean) -> Unit,
    onChooseVideo: () -> Unit,
    copied: Boolean = false,
    onOpenPlayer: (() -> Unit)? = null,
    incomingCode: String = "",
    inviteError: String? = null,
    preparation: WatchOpeningState = WatchOpeningState(),
    onQr: () -> Unit = {},
    onSkip: () -> Unit = {},
    onCancelSkip: () -> Unit = {},
    onNext: () -> Unit = {},
    onCancelNext: () -> Unit = {},
    onExtensions: () -> Unit = {},
    onRead: () -> Unit = {},
) {
    var joining by rememberSaveable { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(incomingCode) {
        if (incomingCode.isNotBlank()) {
            code = incomingCode
            joining = true
        }
    }
    var options by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val motionDuration = if (modernMotionEnabled()) 180 else 0
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(56.dp).background(
                    Brush.linearGradient(listOf(colors.primary, colors.tertiary)),
                    RoundedCornerShape(18.dp),
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Group, null, Modifier.size(30.dp), tint = colors.onPrimary) }
            Column(Modifier.weight(1f)) {
                Text("Una serata insieme", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (room.active) "La vostra stanza" else "Un codice. Lo stesso momento.",
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (inviteError != null) Text(inviteError, color = colors.error)
        if (room.active && incomingCode.isNotBlank() && incomingCode != room.invite) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hai ricevuto un altro invito. Per entrare devi lasciare questa stanza.")
                    Button(onClick = { onJoin(incomingCode) }) { Text("Lascia questa stanza ed entra") }
                }
            }
        }
        if (!room.active) {
            Text(
                "Una stanza per video e manga. Chi crea la stanza sceglie l'episodio da guardare; " +
                    "per i manga ciascuno legge al proprio ritmo e può raggiungere gli altri.",
            )
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Il tuo nome (facoltativo)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            if (room.message.isNotBlank()) {
                Text(
                    room.message,
                    color = if (room.phase ==
                        WatchPhase.Failed
                    ) {
                        colors.error
                    } else {
                        colors.onSurfaceVariant
                    },
                )
            }
            AnimatedVisibility(
                visible = joining,
                enter = fadeIn(tween(motionDuration)),
                exit = fadeOut(tween(motionDuration)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(5000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Codice del tuo amico")
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        supportingText = { Text("Puoi incollare anche l'intero messaggio d'invito.") },
                    )
                    Button(
                        onClick = { onJoin(code) },
                        enabled = code.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Entra nella stanza") }
                }
            }
            if (!joining) {
                Button(onClick = onCreate, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Crea codice")
                }
                OutlinedButton(onClick = {
                    joining = true
                }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Inserisci codice")
                }
            } else {
                TextButton(onClick = { joining = false }) { Text("Preferisco creare una stanza") }
            }
            Text(
                "Ogni telefono usa la propria estensione. Non servono account né configurazioni del router.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        } else {
            OutlinedButton(onClick = onRead, modifier = Modifier.fillMaxWidth()) {
                Text("Leggi insieme · manga, pagine e schizzi")
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        room.media?.title ?: if (room.host) "Scegli cosa guardare" else "Il tuo amico sta scegliendo…",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    room.media?.episode?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.onSurfaceVariant) }
                    if (opening.loading ||
                        room.preparingPlayback ||
                        room.phase in listOf(WatchPhase.Connecting, WatchPhase.Reconnecting, WatchPhase.Buffering)
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    }
                    Text(
                        opening.error ?: when {
                            opening.loading -> "Apro l'episodio dalla tua estensione…"
                            room.preparingPlayback -> room.playbackPreparationMessage
                            else -> room.message
                        },
                        color = if (opening.error != null) colors.error else colors.onSurfaceVariant,
                    )
                    if (opening.error != null) {
                        TextButton(
                            onClick = if (opening.problem ==
                                WatchProblem.MissingSource
                            ) {
                                onExtensions
                            } else {
                                onResync
                            },
                        ) {
                            Text(if (opening.problem == WatchProblem.MissingSource) "Apri estensioni" else "Riprova")
                        }
                    }
                    if (room.host &&
                        room.media == null
                    ) {
                        Button(onClick = onChooseVideo) { Text("Scegli un titolo nella Home") }
                    }
                    if (room.media != null && onOpenPlayer != null) {
                        OutlinedButton(onClick = onOpenPlayer, enabled = !opening.loading) { Text("Apri il player") }
                    }
                }
            }
            if (room.invite.isNotBlank() && (room.host || room.members.size < 2)) {
                Text("Codice d'invito", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onQr) { Text("Mostra QR d'invito") }
                SelectionContainer {
                    Text(room.invite, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                        Icon(
                            if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                            null,
                            Modifier.size(18.dp),
                        )
                        Text(if (copied) " Copiato" else " Copia")
                    }
                    Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Text(" Invita")
                    }
                }
            }
            if (room.members.isNotEmpty()) {
                Text("Nella stanza · " + room.members.size + "/8", style = MaterialTheme.typography.titleSmall)
                room.members.forEachIndexed { index, member ->
                    val participant: @Composable (Modifier) -> Unit = { nameModifier ->
                        Text(member.name + if (index == 0) " · Host" else "", nameModifier)
                    }
                    val status: @Composable () -> Unit = {
                        Text(
                            if (member.problem != WatchProblem.None) {
                                member.problem.description()
                            } else if (member.buffering) {
                                "Caricamento"
                            } else if (member.ready) {
                                "Pronto"
                            } else {
                                "In attesa"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (member.ready && !member.buffering) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                    if (member.problem != WatchProblem.None || LocalDensity.current.fontScale > 1.3f) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            participant(Modifier)
                            status()
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            participant(Modifier.weight(1f).padding(end = 12.dp))
                            status()
                        }
                    }
                }
            }
            WatchRoomCues(room, onSkip, onCancelSkip, onNext, onCancelNext)
            if (preparation.error != null) {
                Text("Prossimo episodio: " + preparation.error, color = colors.error)
                TextButton(
                    onClick = if (preparation.problem ==
                        WatchProblem.MissingSource
                    ) {
                        onExtensions
                    } else {
                        onResync
                    },
                ) {
                    Text(
                        if (preparation.problem ==
                            WatchProblem.MissingSource
                        ) {
                            "Apri estensioni"
                        } else {
                            "Riprova preparazione"
                        },
                    )
                }
            }
            if (room.media != null && (room.sharedControls || room.host || room.localHold)) {
                Button(
                    onClick = onTogglePlayback,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled =
                    room.phase !in listOf(
                        WatchPhase.Connecting,
                        WatchPhase.Reconnecting,
                        WatchPhase.DifferentVideo,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        if (room.wantsPlayback &&
                            !room.localHold
                        ) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        null,
                    )
                    Text(
                        when {
                            !room.sharedControls && !room.host -> " Torna alla visione"
                            room.wantsPlayback && !room.localHold -> " Pausa per tutti"
                            else -> " Riprendi insieme"
                        },
                    )
                }
            }
            TextButton(onClick = {
                options = !options
            }) { Text(if (options) "Nascondi opzioni" else "Opzioni della stanza") }
            AnimatedVisibility(options, enter = fadeIn(tween(motionDuration)), exit = fadeOut(tween(motionDuration))) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (room.host) {
                        WatchOption("Tutti possono usare i comandi", room.sharedControls, onSharedControls)
                        WatchOption("Aspetta tutti durante il caricamento", room.waitForEveryone, onWaitForEveryone)
                    }
                    Text(
                        "Connessioni attive: " +
                            room.relayCount +
                            (room.latencyMs?.let { " · Ritardo " + it + " ms" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    TextButton(onClick = onResync) { Text("Riallinea adesso") }
                }
            }
            HorizontalDivider()
            TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text(if (room.host) "Chiudi la stanza" else "Lascia la stanza", color = colors.error)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun WatchOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun WatchRoomChip(room: WatchRoomState, onClick: () -> Unit) {
    if (room.active) {
        AssistChip(
            onClick = onClick,
            label = {
                Text(
                    when {
                        room.resumeSeconds != null -> "Ripartenza tra ${room.resumeSeconds}"
                        room.preparingPlayback -> "Guarda insieme · Preparazione…"
                        room.phase == WatchPhase.Playing -> "Insieme · " + room.members.size
                        room.phase in listOf(
                            WatchPhase.Connecting,
                            WatchPhase.Reconnecting,
                        ) -> "Guarda insieme · Connessione…"
                        room.phase == WatchPhase.Buffering -> "Guarda insieme · Caricamento…"
                        else -> "Guarda insieme · In pausa"
                    },
                )
            },
            leadingIcon = { Icon(Icons.Default.Group, null, Modifier.size(18.dp)) },
        )
    }
}

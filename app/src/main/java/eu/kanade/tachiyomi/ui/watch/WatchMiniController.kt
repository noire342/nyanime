package eu.kanade.tachiyomi.ui.watch

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.motion.modernMotionEnabled
import eu.kanade.tachiyomi.data.cast.CastController
import eu.kanade.tachiyomi.data.watch.WatchOpeningState
import eu.kanade.tachiyomi.data.watch.WatchRoomState
import eu.kanade.tachiyomi.data.watch.WatchTogetherManager

/** Browsing reads the room snapshot only; it never reads the detached native player. */
@Composable
fun WatchMiniController(modifier: Modifier = Modifier, includeNavigationInsets: Boolean = true) {
    val context = LocalContext.current
    val manager = remember { WatchTogetherManager.get(context) }
    val room by manager.controller.state.collectAsState()
    val opening by manager.opening.collectAsState()
    val cast = remember { CastController.get(context) }
    val casting by cast.state.collectAsState()
    val motion = modernMotionEnabled()
    AnimatedVisibility(
        visible = room.active && !casting.active && !casting.connecting,
        enter = fadeIn(tween(if (motion) 180 else 0)) + expandVertically(tween(if (motion) 240 else 0)),
        exit = fadeOut(tween(if (motion) 120 else 0)) + shrinkVertically(tween(if (motion) 180 else 0)),
    ) {
        val openRoom = { context.startActivity(Intent(context, WatchTogetherActivity::class.java)) }
        WatchMiniBar(
            room,
            opening,
            onOpenPlayer = if (room.media != null) manager::openSelectedVideo else openRoom,
            onOpenRoom = openRoom,
            modifier = modifier,
            includeNavigationInsets = includeNavigationInsets,
        )
    }
}

@Composable
internal fun WatchMiniBar(
    room: WatchRoomState,
    opening: WatchOpeningState,
    onOpenPlayer: () -> Unit,
    onOpenRoom: () -> Unit,
    modifier: Modifier = Modifier,
    includeNavigationInsets: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        color = colors.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Row(
            (if (includeNavigationInsets) Modifier.navigationBarsPadding() else Modifier)
                .fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.weight(1f).clickable(
                    enabled = !opening.loading,
                    role = Role.Button,
                    onClickLabel = if (room.media != null) "Torna al video" else "Apri la stanza",
                    onClick = onOpenPlayer,
                ).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(32.dp), colors.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        room.media?.title ?: "Guardiamo insieme",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            opening.loading -> "Apriamo il video…"
                            opening.error != null -> "Video non aperto · Tocca per riprovare"
                            room.media != null -> "Torna al video · " + room.media.episode
                            else -> "Scegli un episodio per la stanza"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (opening.error != null) colors.error else colors.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onOpenRoom) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Groups,
                        "Apri la stanza: ${room.members.size} partecipanti",
                        Modifier.size(22.dp),
                    )
                    Text(room.members.size.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

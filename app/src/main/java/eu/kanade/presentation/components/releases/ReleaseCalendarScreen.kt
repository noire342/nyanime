package eu.kanade.presentation.components.releases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.releases.ReleaseMedium
import eu.kanade.tachiyomi.data.releases.ReleasePreferences
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import tachiyomi.presentation.core.util.collectAsState

class ReleaseCalendarScreen(
    private val entryPoint: ReleaseMedium = ReleaseMedium.ANIME,
    private val showBack: Boolean = true,
) : Screen() {
    @Composable
    override fun Content() {
        ReleaseCalendar(entryPoint, showBack)
    }
}

/** The actual tab or navigation screen owns the model, so Voyager disposes its jobs correctly. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun cafe.adriel.voyager.core.screen.Screen.ReleaseCalendar(
    entryPoint: ReleaseMedium = ReleaseMedium.ANIME,
    showBack: Boolean = true,
) {
    val unified by ReleasePreferences().unifiedAgenda.collectAsState()
    var selected by rememberSaveable { mutableStateOf(entryPoint) }
    val scope = if (unified) null else selected
    val model = rememberScreenModel(tag = "releases-$scope") { ReleaseCalendarScreenModel(scope) }
    val state by model.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    var choice by remember { mutableStateOf<ReleaseAgendaItem?>(null) }
    fun open(item: ReleaseAgendaItem) {
        when (item.medium) {
            ReleaseMedium.ANIME -> if (item.itemId == null) {
                navigator.push(AnimeScreen(item.entryId))
            } else {
                context.startActivity(PlayerActivity.newIntent(context, item.entryId, item.itemId))
            }
            ReleaseMedium.MANGA -> if (item.itemId == null) {
                navigator.push(MangaScreen(item.entryId))
            } else {
                context.startActivity(ReaderActivity.newIntent(context, item.entryId, item.itemId))
            }
        }
    }
    ReleaseCalendarContent(
        state.month, state.date, state.events, state.items,
        state.loading, state.warning, model::setMonth, model::setDate, model::refresh,
        onItem = {
            if (it.choices.isEmpty()) open(it) else choice = it
        }, showBack = showBack, showMediaFilter = true, medium = state.medium,
        onMedium = if (unified) {
            model::setMedium
        } else {
            { medium ->
                if (medium != null) selected = medium
            }
        },
        allowAllMedia = unified,
    )
    choice?.let { item ->
        ModalBottomSheet(onDismissRequest = { choice = null }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.release_choose_source), style = MaterialTheme.typography.titleLarge)
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                item.choices.forEach { option ->
                    Surface(
                        onClick = {
                            choice = null
                            open(option)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.sourceLabel, style = MaterialTheme.typography.titleMedium)
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

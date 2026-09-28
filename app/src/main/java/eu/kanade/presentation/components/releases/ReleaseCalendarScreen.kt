package eu.kanade.presentation.components.releases

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
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
    ReleaseCalendarContent(
        state.month, state.date, state.events, state.items,
        state.loading, state.warning, model::setMonth, model::setDate, model::refresh,
        onItem = {
            when (it.medium) {
                ReleaseMedium.ANIME -> if (it.itemId == null) {
                    navigator.push(AnimeScreen(it.entryId))
                } else {
                    context.startActivity(PlayerActivity.newIntent(context, it.entryId, it.itemId))
                }
                ReleaseMedium.MANGA -> if (it.itemId == null) {
                    navigator.push(MangaScreen(it.entryId))
                } else {
                    context.startActivity(ReaderActivity.newIntent(context, it.entryId, it.itemId))
                }
            }
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
}

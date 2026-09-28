package mihon.feature.upcoming.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen

class UpcomingMangaScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = androidx.compose.ui.platform.LocalContext.current

        val screenModel = rememberScreenModel { UpcomingMangaScreenModel() }
        val state by screenModel.state.collectAsState()

        UpcomingMangaScreenContent(
            state = state,
            setSelectedYearMonth = screenModel::setSelectedYearMonth,
            setSelectedDate = screenModel::setSelectedDate,
            onRefresh = screenModel::refresh,
            onClickUpcoming = {
                if (it.itemId == null) {
                    navigator.push(MangaScreen(it.entryId))
                } else {
                    context.startActivity(
                        eu.kanade.tachiyomi.ui.reader.ReaderActivity.newIntent(context, it.entryId, it.itemId),
                    )
                }
            },
        )
    }
}

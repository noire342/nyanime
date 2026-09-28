package mihon.feature.upcoming.anime

import androidx.compose.runtime.Composable
import eu.kanade.presentation.components.releases.ReleaseAgendaItem
import eu.kanade.presentation.components.releases.ReleaseCalendarContent
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun UpcomingAnimeScreenContent(
    state: UpcomingAnimeScreenModel.State,
    setSelectedYearMonth: (YearMonth) -> Unit,
    setSelectedDate: (LocalDate?) -> Unit,
    onRefresh: () -> Unit,
    onClickUpcoming: (ReleaseAgendaItem) -> Unit,
) {
    ReleaseCalendarContent(
        state.selectedYearMonth, state.selectedDate, state.events, state.items,
        state.loading, state.warning, setSelectedYearMonth, setSelectedDate, onRefresh, onClickUpcoming,
    )
}

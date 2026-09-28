package eu.kanade.presentation.components.releases

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.motion.ModernMotion
import eu.kanade.tachiyomi.R
import kotlinx.collections.immutable.ImmutableMap
import mihon.feature.upcoming.components.calendar.Calendar
import tachiyomi.presentation.core.components.material.Scaffold
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ReleaseAgendaItem(
    val key: String,
    val entryId: Long,
    val title: String,
    val cover: Any?,
    val at: Long,
    val label: String,
    val itemId: Long? = null,
) {
    val date: LocalDate get() = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
}

@Composable
fun ReleaseCalendarContent(
    month: YearMonth,
    selectedDate: LocalDate?,
    events: ImmutableMap<LocalDate, Int>,
    items: List<ReleaseAgendaItem>,
    loading: Boolean,
    warning: String?,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate?) -> Unit,
    onRefresh: () -> Unit,
    onItem: (ReleaseAgendaItem) -> Unit,
    initialCalendar: Boolean = false,
) {
    val navigator = LocalNavigator.currentOrThrow
    val locale = LocalConfiguration.current.locales[0]
    var calendarVisible by rememberSaveable { mutableStateOf(initialCalendar) }
    Scaffold(topBar = {
        AppBar(
            title = stringResource(R.string.release_title),
            navigateUp = navigator::pop,
            actions = {
                IconButton(enabled = !loading, onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.release_check_now))
                }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "view-mode") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SegmentedButton(
                        selected = !calendarVisible,
                        onClick = {
                            calendarVisible = false
                        },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.release_agenda)) }
                    SegmentedButton(
                        selected = calendarVisible,
                        onClick = { calendarVisible = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.release_calendar_title)) }
                }
            }
            item(key = "calendar") {
                AnimatedVisibility(
                    calendarVisible,
                    enter = expandVertically(tween(ModernMotion.RESIZE_MILLIS)) + fadeIn(),
                    exit = shrinkVertically(tween(ModernMotion.RESIZE_MILLIS)) + fadeOut(),
                ) {
                    Calendar(month, events, onMonth, { onDate(it) }, selectedDate = selectedDate)
                }
            }
            item(key = "agenda-mode") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selectedDate?.let { relativeDateText(it) } ?: if (month == YearMonth.now()) {
                            stringResource(R.string.release_calendar_week)
                        } else {
                            val format = DateTimeFormatter.ofPattern("d MMM", locale)
                            "${month.atDay(1).format(format)} – ${month.atDay(7).format(format)}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedDate !=
                        null
                    ) {
                        TextButton(onClick = {
                            onMonth(YearMonth.now())
                            onDate(null)
                        }) { Text(stringResource(R.string.release_calendar_week)) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 16.dp)) {
                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (warning != null) {
                    Text(
                        warning,
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.isEmpty()) {
                item(key = "empty") {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(
                            stringResource(
                                if (selectedDate ==
                                    null
                                ) {
                                    R.string.release_calendar_unknown
                                } else {
                                    R.string.release_calendar_empty
                                },
                            ),
                            Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items.groupBy { it.date }.forEach { (date, releases) ->
                if (selectedDate == null) {
                    item(key = "day-$date", contentType = "day") {
                        Text(
                            relativeDateText(date),
                            Modifier.padding(horizontal = 20.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                items(releases, key = { it.key }, contentType = { "release" }) { item ->
                    Card(
                        Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp).clickable {
                            onItem(item)
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ItemCover.Book(
                                data = item.cover,
                                modifier = Modifier.width(64.dp).height(96.dp),
                                shape = RoundedCornerShape(8.dp),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    Instant.ofEpochMilli(
                                        item.at,
                                    ).atZone(
                                        ZoneId.systemDefault(),
                                    ).format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", locale)),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "bottom-space") { androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp)) }
        }
    }
}

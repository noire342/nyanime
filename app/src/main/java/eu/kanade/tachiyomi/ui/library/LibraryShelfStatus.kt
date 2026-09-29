package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.data.releases.AiringEvent
import eu.kanade.tachiyomi.data.releases.ChapterScheduleRepository
import eu.kanade.tachiyomi.data.releases.ReleaseStore

@Immutable
data class LibraryShelfStatus(
    val newReleaseCount: Int = 0,
    val newestReleaseNumber: Double? = null,
    val nextReleaseAt: Long? = null,
    val nextReleaseNumber: Double? = null,
)

internal fun animeShelfStatuses(
    notices: List<ReleaseStore.Notice>,
    events: List<AiringEvent>,
    now: Long = System.currentTimeMillis(),
): Map<Long, LibraryShelfStatus> = buildShelfStatuses(
    notices = notices,
    future = events.asSequence()
        .filter { it.airingAt > now }
        .map { FutureRelease(it.entryId, it.episode.toDouble(), it.airingAt) }
        .toList(),
)

internal fun mangaShelfStatuses(
    notices: List<ReleaseStore.Notice>,
    events: List<ChapterScheduleRepository.Event>,
    now: Long = System.currentTimeMillis(),
): Map<Long, LibraryShelfStatus> = buildShelfStatuses(
    notices = notices,
    future = events.asSequence()
        .filter { it.release.releaseAt > now }
        .map { FutureRelease(it.entryId, it.release.number, it.release.releaseAt) }
        .toList(),
)

private data class FutureRelease(val entryId: Long, val number: Double, val at: Long)

private fun buildShelfStatuses(
    notices: List<ReleaseStore.Notice>,
    future: List<FutureRelease>,
): Map<Long, LibraryShelfStatus> {
    val noticesByEntry = notices.groupBy { it.entryId }
    val nextByEntry = future.groupBy { it.entryId }.mapValues { (_, values) -> values.minBy { it.at } }
    return (noticesByEntry.keys + nextByEntry.keys).associateWith { entryId ->
        val entryNotices = noticesByEntry[entryId].orEmpty()
        val next = nextByEntry[entryId]
        LibraryShelfStatus(
            newReleaseCount = entryNotices.size,
            newestReleaseNumber = entryNotices.asSequence().map { it.number }.filter { it > 0 }.maxOrNull(),
            nextReleaseAt = next?.at,
            nextReleaseNumber = next?.number,
        )
    }
}

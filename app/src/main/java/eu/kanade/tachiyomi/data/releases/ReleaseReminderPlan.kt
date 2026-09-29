package eu.kanade.tachiyomi.data.releases

enum class ReleaseReminderKind { ADVANCE, AIRING }

data class ReleaseReminder(
    val kind: ReleaseReminderKind,
    val events: List<AiringEvent>,
    val keys: Set<String>,
) {
    val event: AiringEvent get() = events.first()
    val at: Long get() = event.airingAt - if (kind == ReleaseReminderKind.ADVANCE) ReleasePolicy.DAY else 0

    fun expired(now: Long): Boolean = now - at > when (kind) {
        ReleaseReminderKind.ADVANCE -> 6 * ReleasePolicy.HOUR
        ReleaseReminderKind.AIRING -> 2 * ReleasePolicy.HOUR
    }
}

/** Absolute dates from either provider. No polling, guessed dates or source-specific identities. */
object ReleaseReminderPlan {
    fun pending(
        events: List<AiringEvent>,
        delivered: Set<Pair<String, String>>,
        advance: Boolean,
    ): List<ReleaseReminder> = events.filter {
        it.airingAt > 0 && it.episode > 0
    }.groupBy(::key).values.flatMap { group ->
        // Prefer the selected broadcast over a duplicate edition's fallback RAW date.
        val choices = group.sortedWith(
            compareBy<AiringEvent> { it.variants.isEmpty() }.thenBy { it.airingAt }.thenBy { it.entryId },
        )
        val keys = choices.flatMap { listOf(key(it), "entry:${it.entryId}:${it.episode}") }.toSet()
        ReleaseReminderKind.entries.filter { kind ->
            (kind != ReleaseReminderKind.ADVANCE || advance) &&
                choices.none { it.remindedAt != 0L } &&
                keys.none { it to kind.name in delivered }
        }.map { ReleaseReminder(it, choices, keys) }
    }.sortedBy { it.at }

    private fun key(event: AiringEvent): String = if (event.catalogId > 0) {
        "catalog:${event.catalogId}:${event.episode}"
    } else {
        "entry:${event.entryId}:${event.episode}"
    }
}

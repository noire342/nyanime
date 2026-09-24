package eu.kanade.tachiyomi.ui.tv

import tachiyomi.domain.items.episode.model.Episode

/** A resume point wins; otherwise offer the first unwatched episode in source order. */
internal fun recommendedTvEpisode(
    episodes: List<Episode>,
    states: Map<String, TvEpisodeState>,
    useMainState: Boolean,
): Episode? {
    val resume = episodes.firstOrNull { episode ->
        val state = states[episode.url]
        if (state != null) {
            state.positionMs > 0 && (state.durationMs <= 0 || state.positionMs < state.durationMs)
        } else {
            useMainState &&
                episode.lastSecondSeen > 0 &&
                (episode.totalSeconds <= 0 || episode.lastSecondSeen < episode.totalSeconds)
        }
    }
    return resume ?: episodes.firstOrNull { episode ->
        !(states[episode.url]?.seen ?: (useMainState && episode.seen))
    } ?: episodes.firstOrNull()
}

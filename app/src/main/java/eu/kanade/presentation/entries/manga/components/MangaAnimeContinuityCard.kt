package eu.kanade.presentation.entries.manga.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.ContinuityCard
import eu.kanade.presentation.components.ContinuityDestination
import eu.kanade.presentation.components.ContinuityDestinationSheet
import eu.kanade.tachiyomi.data.track.MangaAnimeContinuity
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MangaAnimeContinuityCard(
    result: MangaAnimeContinuity.Result?,
    onOpenAnime: (Anime) -> Unit,
    onSearchAnime: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val found = result as? MangaAnimeContinuity.Result.Found
    if (found?.choices.isNullOrEmpty() && result != MangaAnimeContinuity.Result.Unavailable) return
    if (found == null) {
        ContinuityCard(
            title = "Collegamento anime",
            subtitle = "Non disponibile adesso · tocca per riprovare",
            onOpen = onRetry,
            modifier = modifier,
        )
        return
    }
    var selectedId by rememberSaveable { mutableLongStateOf(found.choices.first().catalogId) }
    var choosingAdaptation by rememberSaveable { mutableStateOf(false) }
    var choosingCopy by rememberSaveable { mutableStateOf(false) }
    val choice = found.choices.firstOrNull { it.catalogId == selectedId } ?: found.choices.first()
    fun openChoice(candidate: MangaAnimeContinuity.Choice) {
        selectedId = candidate.catalogId
        when (candidate.matches.size) {
            0 -> onSearchAnime(candidate.title)
            1 -> onOpenAnime(candidate.matches.single())
            else -> choosingCopy = true
        }
    }
    ContinuityCard(
        title = "Passa all'anime",
        subtitle = choice.title,
        onOpen = {
            if (found.choices.size > 1) choosingAdaptation = true else openChoice(choice)
        },
        modifier = modifier,
    )
    if (choosingAdaptation) {
        ContinuityDestinationSheet(
            title = "Quale anime vuoi aprire?",
            destinations = found.choices.map {
                ContinuityDestination(
                    it.title,
                    listOfNotNull(formatLabel(it.format), it.year?.toString(), it.episodes?.let { "$it episodi" })
                        .joinToString(" · "),
                )
            },
            onSelect = {
                choosingAdaptation = false
                openChoice(found.choices[it])
            },
            onDismiss = { choosingAdaptation = false },
        )
    }
    if (choosingCopy) {
        val sources = Injekt.get<AnimeSourceManager>()
        ContinuityDestinationSheet(
            title = "Scegli la copia",
            destinations = choice.matches.map { ContinuityDestination(it.title, sources.getOrStub(it.source).name) },
            onSelect = {
                choosingCopy = false
                onOpenAnime(choice.matches[it])
            },
            onDismiss = { choosingCopy = false },
        )
    }
}

private fun formatLabel(format: String?): String? = when (format) {
    "TV", "TV_SHORT" -> "Serie TV"
    "MOVIE" -> "Film"
    "OVA" -> "OVA"
    "ONA" -> "Serie web"
    "SPECIAL" -> "Speciale"
    else -> format
}

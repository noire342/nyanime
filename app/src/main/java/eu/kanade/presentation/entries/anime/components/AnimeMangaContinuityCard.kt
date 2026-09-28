package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.ContinuityCard
import eu.kanade.presentation.components.ContinuityDestination
import eu.kanade.presentation.components.ContinuityDestinationSheet
import eu.kanade.tachiyomi.data.track.AnimeMangaContinuity
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AnimeMangaContinuityCard(
    result: AnimeMangaContinuity.Result.Found,
    onOpenManga: (Manga, Double?) -> Unit,
    onSearchManga: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (result.choices.isEmpty()) return
    var selectedId by rememberSaveable { mutableLongStateOf(result.choices.first().catalogId) }
    var choosingAdaptation by rememberSaveable { mutableStateOf(false) }
    var choosingCopy by rememberSaveable { mutableStateOf(false) }
    val choice = result.choices.firstOrNull { it.catalogId == selectedId } ?: result.choices.first()
    val continuation = choice.continuationAfter(result.watchedEpisode)
    fun openChoice(candidate: AnimeMangaContinuity.Choice) {
        selectedId = candidate.catalogId
        when (candidate.matches.size) {
            0 -> onSearchManga(candidate.title)
            1 -> onOpenManga(candidate.matches.single(), candidate.continuationAfter(result.watchedEpisode))
            else -> choosingCopy = true
        }
    }
    ContinuityCard(
        title = continuation?.let { "Continua nel manga · capitolo ${chapterLabel(it)}" } ?: "Continua nel manga",
        subtitle = choice.title,
        onOpen = {
            if (choice.matches.isEmpty() && result.choices.size > 1) {
                choosingAdaptation = true
            } else {
                openChoice(choice)
            }
        },
        modifier = modifier,
        preview = {
            if (choice.beginning != null || choice.latestAdapted != null) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    choice.beginning?.let { ChapterReference(choice.season, "Inizio", it.chapter) }
                    choice.latestAdapted?.let { ChapterReference(choice.season, "Fine", it.chapter) }
                }
            }
        },
    )
    if (choosingAdaptation) {
        ContinuityDestinationSheet(
            title = "Quale manga vuoi aprire?",
            destinations = result.choices.map { ContinuityDestination(it.title) },
            onSelect = {
                choosingAdaptation = false
                openChoice(result.choices[it])
            },
            onDismiss = { choosingAdaptation = false },
        )
    }
    if (choosingCopy) {
        val sources = Injekt.get<MangaSourceManager>()
        ContinuityDestinationSheet(
            title = "Scegli la copia",
            destinations = choice.matches.map { ContinuityDestination(it.title, sources.getOrStub(it.source).name) },
            onSelect = {
                choosingCopy = false
                onOpenManga(choice.matches[it], continuation)
            },
            onDismiss = { choosingCopy = false },
        )
    }
}

@Composable
private fun ChapterReference(season: Int?, label: String, chapter: Double) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(season?.let { "Stagione $it" } ?: "Adattamento")
                append(" • $label • ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append("Capitolo ${chapterLabel(chapter)}")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun chapterLabel(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

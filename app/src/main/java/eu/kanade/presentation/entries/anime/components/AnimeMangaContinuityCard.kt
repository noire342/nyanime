package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.ContinuityCard
import eu.kanade.tachiyomi.data.track.AnimeMangaContinuity
import tachiyomi.domain.entries.manga.model.Manga

@Composable
fun AnimeMangaContinuityCard(
    result: AnimeMangaContinuity.Result.Found,
    onOpenManga: (Manga, Double?) -> Unit,
    onSearchManga: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (result.choices.isEmpty()) return
    var selectedId by rememberSaveable { mutableLongStateOf(result.choices.first().catalogId) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val choice = result.choices.firstOrNull { it.catalogId == selectedId } ?: result.choices.first()
    val continuation = choice.continuationAfter(result.watchedEpisode)
    val target = choice.matches.singleOrNull()
    ContinuityCard(
        title = continuation?.let { "Continua nel manga · capitolo ${chapterLabel(it)}" } ?: "Continua nel manga",
        subtitle = choice.title,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onOpen = {
            when {
                target != null -> onOpenManga(target, continuation)
                choice.matches.isEmpty() -> onSearchManga(choice.title)
                else -> expanded = true
            }
        },
        modifier = modifier,
        preview = {
            if (choice.beginning != null || choice.latestAdapted != null) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    choice.beginning?.let {
                        ChapterReference(choice.season, "Inizio", it.chapter)
                    }
                    choice.latestAdapted?.let {
                        ChapterReference(choice.season, "Fine", it.chapter)
                    }
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = choice.coverUrl ?: choice.matches.firstOrNull()?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp, 70.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Text(
                    choice.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (result.choices.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    result.choices.forEach { candidate ->
                        OutlinedButton(
                            onClick = { selectedId = candidate.catalogId },
                            modifier = Modifier.widthIn(max = 240.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (candidate.catalogId == choice.catalogId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            Text(
                                candidate.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            if (choice.viaOriginalNovel) {
                Text(
                    "Anime e manga sono adattamenti della stessa novel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (choice.matches.size > 1) {
                Text("Scegli la tua copia", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    choice.matches.forEach { manga ->
                        OutlinedButton(onClick = { onOpenManga(manga, continuation) }) {
                            Text(manga.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else if (target == null) {
                OutlinedButton(onClick = { onSearchManga(choice.title) }) { Text("Cerca una copia") }
            } else if (continuation != null) {
                OutlinedButton(onClick = { onOpenManga(target, null) }) { Text("Apri la scheda manga") }
            }
        }
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

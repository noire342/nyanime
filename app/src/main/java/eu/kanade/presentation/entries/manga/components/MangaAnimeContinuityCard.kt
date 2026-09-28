package eu.kanade.presentation.entries.manga.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.ContinuityCard
import eu.kanade.tachiyomi.data.track.MangaAnimeContinuity
import tachiyomi.domain.entries.anime.model.Anime

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
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (found == null) {
        ContinuityCard(
            title = "Collegamento anime",
            subtitle = "Non disponibile adesso · tocca per riprovare",
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onOpen = onRetry,
            modifier = modifier,
        ) {
            OutlinedButton(onClick = onRetry) { Text("Riprova") }
        }
        return
    }
    var selectedId by rememberSaveable { mutableLongStateOf(found.choices.first().catalogId) }
    val choice = found.choices.firstOrNull { it.catalogId == selectedId } ?: found.choices.first()
    val target = choice.matches.singleOrNull()
    ContinuityCard(
        title = "Passa all'anime",
        subtitle = choice.title,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onOpen = {
            when {
                target != null -> onOpenAnime(target)
                choice.matches.isEmpty() -> onSearchAnime(choice.title)
                else -> expanded = true
            }
        },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = choice.coverUrl ?: choice.matches.firstOrNull()?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp, 70.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        choice.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            formatLabel(choice.format),
                            choice.year?.toString(),
                            choice.episodes?.let { "$it episodi" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (found.choices.size > 1) {
                Text("Scegli l'adattamento", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    found.choices.forEach { candidate ->
                        OutlinedButton(
                            onClick = { selectedId = candidate.catalogId },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.widthIn(max = 240.dp),
                            border = BorderStroke(
                                1.dp,
                                if (candidate.catalogId == choice.catalogId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    candidate.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    listOfNotNull(candidate.year?.toString(), formatLabel(candidate.format))
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
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
                    choice.matches.forEach { anime ->
                        OutlinedButton(onClick = { onOpenAnime(anime) }) {
                            Text(anime.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else if (target == null) {
                OutlinedButton(onClick = { onSearchAnime(choice.title) }) { Text("Cerca una copia") }
            }
        }
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

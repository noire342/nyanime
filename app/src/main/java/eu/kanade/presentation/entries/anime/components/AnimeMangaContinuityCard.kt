package eu.kanade.presentation.entries.anime.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.data.track.AnimeMangaContinuity
import tachiyomi.domain.entries.manga.model.Manga

@Composable
fun AnimeMangaContinuityCard(
    result: AnimeMangaContinuity.Result.Found,
    onOpenManga: (Manga, Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (result.choices.isEmpty()) return
    var selected by rememberSaveable(result.choices.map { it.catalogId }) { mutableIntStateOf(0) }
    val choice = result.choices.getOrNull(selected) ?: result.choices.first()
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.12f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "DALL'ANIME AL MANGA",
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            AnimatedContent(targetState = choice.catalogId, label = "Manga collegato") {
                val selectedChoice = result.choices.first { candidate -> candidate.catalogId == it }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AsyncImage(
                        model = selectedChoice.coverUrl ?: selectedChoice.matches.firstOrNull()?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp, 88.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            selectedChoice.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (selectedChoice.matches.isEmpty()) {
                                "Collegamento verificato · cerca una copia disponibile nelle tue fonti"
                            } else {
                                "Manga riconosciuto · pronto da leggere"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (result.choices.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    result.choices.forEachIndexed { index, candidate ->
                        OutlinedButton(
                            onClick = { selected = index },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected ==
                                    index
                                ) {
                                    accent
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            Text(
                                candidate.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            choice.beginning?.let {
                Text(
                    "Dall'inizio dell'adattamento · cap. ${chapterLabel(it.chapter)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            choice.latestAdapted?.let {
                Text(
                    "Ultimo punto catalogato · cap. ${chapterLabel(it.chapter)}" +
                        (it.episode?.let { episode -> " · episodio $episode" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val continuation = choice.continuationAfter(result.watchedEpisode)
            if (continuation == null) {
                Text(
                    "Il capitolo esatto del tuo episodio non è verificato. I riferimenti qui sopra non sono una stima della tua posizione.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Dopo il tuo episodio · dal cap. ${chapterLabel(continuation)}",
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val target = choice.matches.singleOrNull()
            if (target != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenManga(target, continuation) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                    ) {
                        Text(if (continuation != null) "Continua nel manga" else "Apri il manga")
                    }
                    if (continuation != null) {
                        OutlinedButton(onClick = { onOpenManga(target, null) }) { Text("Scheda") }
                    }
                }
            } else if (choice.matches.size > 1) {
                Text("Più copie verificate nella libreria", style = MaterialTheme.typography.labelMedium)
                choice.matches.take(3).forEach { manga ->
                    OutlinedButton(onClick = { onOpenManga(manga, continuation) }) {
                        Text(manga.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text(
                "Collegamento: AniList · checkpoint: MangaBaka / MangaUpdates",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun chapterLabel(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

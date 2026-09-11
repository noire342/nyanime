package eu.kanade.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tachiyomi.domain.discovery.CatalogAnime
import tachiyomi.domain.discovery.CatalogId
import tachiyomi.domain.discovery.providerLabel

@Composable
fun CatalogDetailsContent(anime: CatalogAnime, actions: @Composable () -> Unit = {}, onRelated: (CatalogId) -> Unit) {
    var expanded by rememberSaveable(anime.id.provider, anime.id.value) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height((maxWidth * 0.75f).coerceIn(240.dp, 380.dp))) {
            AsyncImage(
                anime.banner ?: anime.cover,
                anime.title,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
            )
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(anime.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (anime.alternateTitles.isNotEmpty()) {
            Text(
                anime.alternateTitles.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val facts = listOfNotNull(
            anime.score?.let { "Voto ${anime.id.providerLabel()}: $it/100" },
            metadataLabel(anime.format),
            metadataLabel(anime.status),
            listOfNotNull(metadataLabel(anime.season), anime.year?.toString()).joinToString(" ").takeIf {
                it.isNotEmpty()
            },
            anime.episodes?.let { "$it episodi" },
            anime.duration?.let { "$it min/episodio" },
        )
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        if (anime.genres.isNotEmpty()) Text(anime.genres.joinToString(" · "), color = MaterialTheme.colorScheme.primary)
        if (anime.studios.isNotEmpty()) Text("Studio: ${anime.studios.joinToString(", ")}")
        airingLabel(anime)?.let { Text("Prossima messa in onda: $it", style = MaterialTheme.typography.bodyMedium) }
        actions()
        Text(
            anime.synopsis ?: "Trama non disponibile",
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
        )
        if (anime.synopsis != null) {
            TextButton(onClick = {
                expanded = !expanded
            }) { Text(if (expanded) "Riduci trama" else "Leggi tutta la trama") }
        }
        Text(
            "Informazioni: ${anime.id.providerLabel()} · episodi e disponibilità dipendono dalla fonte",
            style = MaterialTheme.typography.labelSmall,
        )
    }
    if (anime.relations.isNotEmpty()) {
        SectionHeader("Opere collegate")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(anime.relations, key = { "${it.id.provider}:${it.id.value}:${it.relationship}" }) { relation ->
                PosterCard(relation.title, relation.cover, metadataLabel(relation.relationship), {
                    onRelated(relation.id)
                })
            }
        }
    }
}

package eu.kanade.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A compact catalog link; touching the body opens its destination. */
@Composable
internal fun ContinuityCard(
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), Color.Transparent),
                ),
            ).clickable(onClick = onOpen).fillMaxWidth().heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            preview()
        }
    }
}

internal data class ContinuityDestination(val title: String, val subtitle: String? = null)

/** Only ambiguous destinations require a choice; the card itself never expands. */
@Composable
internal fun ContinuityDestinationSheet(
    title: String,
    destinations: List<ContinuityDestination>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                itemsIndexed(destinations) { index, destination ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onSelect(index) }
                            .heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 14.dp),
                    ) {
                        Text(destination.title, style = MaterialTheme.typography.bodyLarge)
                        destination.subtitle?.takeIf(String::isNotBlank)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

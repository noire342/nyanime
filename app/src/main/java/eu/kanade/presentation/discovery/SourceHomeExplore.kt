package eu.kanade.presentation.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.motion.ModernMotion
import eu.kanade.presentation.motion.modernMotionEnabled
import tachiyomi.domain.discovery.SourceHomeFilter
import tachiyomi.domain.discovery.SourceHomeGroup

@Composable
fun SourceHomeExploreBar(
    categories: List<SourceHomeGroup.Section>,
    onBrowse: () -> Unit,
    onCategory: (SourceHomeGroup.Section) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            AssistChip(
                onClick = onBrowse,
                label = { Text("Esplora e filtra") },
                leadingIcon = { Icon(Icons.Outlined.Tune, null) },
            )
        }
        items(categories, key = { it.id }) { category ->
            AssistChip(onClick = { onCategory(category) }, label = { Text(category.title) })
        }
    }
}

@Composable
fun SourceHomeActiveFilters(
    values: Map<String, List<String>>,
    onRemove: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values.entries.toList(), key = { it.key }) { (name, selection) ->
            FilterChip(
                selected = true,
                onClick = { onRemove(name) },
                label = {
                    Text(
                        "$name: ${selection.joinToString().ifBlank { "Tutti" }}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillParentMaxWidth(.72f),
                    )
                },
                trailingIcon = { Icon(Icons.Outlined.Close, "Rimuovi filtro $name") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceHomeFilterSheet(
    definitions: List<SourceHomeFilter>,
    applied: Map<String, List<String>>,
    onDismiss: () -> Unit,
    onApply: (Map<String, List<String>>) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        SourceHomeFilterContent(definitions, applied, onApply = {
            onApply(it)
            onDismiss()
        })
    }
}

@Composable
fun SourceHomeFilterContent(
    definitions: List<SourceHomeFilter>,
    applied: Map<String, List<String>>,
    onApply: (Map<String, List<String>>) -> Unit,
) {
    var draft by remember(definitions) { mutableStateOf(applied) }
    var expanded by remember { mutableStateOf(definitions.firstOrNull()?.name) }
    val motion = modernMotionEnabled()
    Column(Modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Trova la prossima storia", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Combina i filtri e scegli cosa guardare",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { draft = emptyMap() }) { Text("Azzera") }
        }
        LazyColumn(
            Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(definitions, key = { it.name }) { filter ->
                val selected = draft[filter.name] ?: filter.defaults
                val open = expanded == filter.name
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { expanded = if (open) null else filter.name }
                                .heightIn(min = 64.dp).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(filter.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    selected.joinToString().ifBlank { "Tutti" },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                if (open) "Chiudi ${filter.name}" else "Scegli ${filter.name}",
                            )
                        }
                        AnimatedVisibility(
                            open,
                            enter = if (motion) {
                                expandVertically(tween(ModernMotion.RESIZE_MILLIS)) + ModernMotion.enter()
                            } else {
                                EnterTransition.None
                            },
                            exit = if (motion) {
                                shrinkVertically(tween(ModernMotion.EXIT_MILLIS)) + ModernMotion.exit()
                            } else {
                                ExitTransition.None
                            },
                        ) {
                            SourceHomeFilterOptions(filter, selected) { values ->
                                draft = if (values == filter.defaults) {
                                    draft - filter.name
                                } else {
                                    draft + (filter.name to values)
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                onApply(draft)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp),
        ) {
            Text("Mostra risultati")
        }
    }
}

@Composable
private fun SourceHomeFilterOptions(
    filter: SourceHomeFilter,
    selected: List<String>,
    onSelect: (List<String>) -> Unit,
) {
    var search by remember(filter.name) { mutableStateOf("") }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        if (filter.kind == SourceHomeFilter.Kind.TEXT) {
            OutlinedTextField(
                selected.firstOrNull().orEmpty(),
                { onSelect(listOf(it.take(300))) },
                Modifier.fillMaxWidth(),
                label = { Text(filter.name) },
                singleLine = true,
            )
        } else {
            if (filter.options.size > 16) {
                OutlinedTextField(
                    search,
                    { search = it },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Cerca nelle opzioni") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                )
            }
            if (filter.kind == SourceHomeFilter.Kind.MULTIPLE) {
                TextButton(onClick = { onSelect(emptyList()) }) { Text("Nessuna limitazione") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filter.options.filter { it.contains(search, ignoreCase = true) }.forEach { option ->
                    FilterChip(selected = option in selected, onClick = {
                        onSelect(
                            if (filter.kind == SourceHomeFilter.Kind.SINGLE) {
                                listOf(option)
                            } else if (option in selected) {
                                selected - option
                            } else {
                                selected + option
                            },
                        )
                    }, label = { Text(option) })
                }
            }
        }
    }
}

package eu.kanade.presentation.discovery

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.discovery.LocalHomeItem
import eu.kanade.tachiyomi.data.discovery.ResumeVisibility
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SectionState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ContinueWatchingRow(
    state: SectionState<List<LocalHomeItem>>,
    onOpen: (Long) -> Unit,
    emptyMessage: String = "I titoli che guardi compariranno qui.",
    onPlay: (LocalHomeItem) -> Unit,
) {
    val visibility = remember { Injekt.get<ResumeVisibility>() }
    val base = remember { Injekt.get<BasePreferences>() }
    val hidden by visibility.hidden.changes().collectAsState(initial = visibility.hidden.get())
    val incognito by base.incognitoMode().changes().collectAsState(initial = base.incognitoMode().get())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Column {
        LocalAnimeRow(
            state = state,
            onOpen = { onOpen(it.anime.id) },
            emptyMessage = emptyMessage,
            onHide = { item ->
                visibility.hide(item.anime.id)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    if (snackbar.showSnackbar("Titolo nascosto. Cronologia conservata.", "Annulla") ==
                        SnackbarResult.ActionPerformed
                    ) {
                        visibility.restore(item.anime.id)
                    }
                }
            },
            onPlay = onPlay,
        )
        SnackbarHost(snackbar)
        if (!incognito && hidden.isNotEmpty()) {
            TextButton(onClick = { visibility.restoreAll() }) { Text("Ripristina tutti i titoli nascosti") }
        }
    }
}

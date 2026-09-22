package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.m3.util.htmlReadyLicenseContent
import com.mikepenz.aboutlibraries.util.withContext
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

class OpenSourceLicensesScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current.applicationContext
        val catalog by produceState<LicenseCatalog>(initialValue = LicenseCatalog.Loading, context) {
            value = withContext(Dispatchers.IO) {
                runCatching { Libs.Builder().withContext(context).build().libraries.toList() }
                    .fold(LicenseCatalog::Ready) { LicenseCatalog.Failed }
            }
        }
        var query by remember { mutableStateOf("") }
        val listState = rememberLazyListState()
        val filtered = remember(catalog, query) {
            (catalog as? LicenseCatalog.Ready)?.libraries?.filter { library ->
                library.name.contains(query, ignoreCase = true) ||
                    library.uniqueId.contains(query, ignoreCase = true) ||
                    library.licenses.any { it.name.contains(query, ignoreCase = true) }
            }.orEmpty()
        }
        LaunchedEffect(query) { listState.scrollToItem(0) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.library_licenses),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(Modifier.fillMaxSize().padding(contentPadding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text(stringResource(MR.strings.action_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                )
                when (catalog) {
                    LicenseCatalog.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    LicenseCatalog.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(MR.strings.unknown_error))
                    }
                    is LicenseCatalog.Ready -> {
                        if (filtered.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(MR.strings.no_results_found))
                            }
                        } else {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(filtered, key = Library::uniqueId) { library ->
                                    val licenseNames = library.licenses.joinToString { it.name }
                                    val subtitle = listOfNotNull(
                                        library.artifactVersion?.takeIf(String::isNotBlank),
                                        licenseNames.takeIf(String::isNotBlank),
                                    ).joinToString(" · ")
                                    TextPreferenceWidget(
                                        title = library.name,
                                        subtitle = subtitle,
                                        onPreferenceClick = {
                                            navigator.push(
                                                OpenSourceLibraryLicenseScreen(
                                                    name = library.name,
                                                    website = library.website,
                                                    license = library.licenses.firstOrNull()
                                                        ?.htmlReadyLicenseContent.orEmpty(),
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface LicenseCatalog {
    data object Loading : LicenseCatalog
    data object Failed : LicenseCatalog
    data class Ready(val libraries: List<Library>) : LicenseCatalog
}

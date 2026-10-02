package eu.kanade.tachiyomi.ui.news

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.news.NewsAlerts
import eu.kanade.tachiyomi.data.news.NewsExtension
import eu.kanade.tachiyomi.data.news.NewsPersonalLibrary
import eu.kanade.tachiyomi.data.news.NewsPersonalTitles
import eu.kanade.tachiyomi.data.news.NewsRepository
import eu.kanade.tachiyomi.data.news.NewsRules
import eu.kanade.tachiyomi.data.news.StoredNews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nyanime.news.api.NewsTopic
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NewsSourcesScreen : Screen {
    @Composable override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        NewsSettingsFrame(
            stringResource(R.string.news_sources),
            { navigator.pop() },
        ) { padding -> NewsSourcesContent(Modifier.padding(padding)) }
    }
}

@Composable
fun NewsSourcesContent(modifier: Modifier = Modifier) {
    val repository = remember { Injekt.get<NewsRepository>() }
    val extensions by repository.registry.state.collectAsState()
    val snapshot by repository.store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    var trust by remember { mutableStateOf<NewsExtension?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val configure: (NewsExtension, Boolean, NewsAlerts?) -> Unit = { extension, enabled, alerts ->
        scope.launch {
            busy = true
            try {
                repository.configure(extension, enabled, alerts)
                failed = false
            } catch (
                cancel: CancellationException,
            ) {
                throw cancel
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
        if (alerts != null &&
            alerts != NewsAlerts.OFF &&
            Build.VERSION.SDK_INT >= 33
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LifecycleStartEffect(Unit) {
        val job = scope.launch {
            try {
                repository.initialize()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failed = true
            }
        }
        onStopOrDispose { job.cancel() }
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row {
                TextButton(onClick = {
                    navigator.push(NewsStandaloneScreen())
                }) { Text(stringResource(R.string.news_title)) }
                TextButton(onClick = {
                    navigator.push(NewsInterestsScreen())
                }) { Text(stringResource(R.string.news_personalize)) }
            }
        }
        if (failed) item { Text(stringResource(R.string.news_storage_error)) }
        if (extensions.isEmpty()) item { Text(stringResource(R.string.news_empty_body)) }
        items(extensions, key = { it.packageName }) { extension ->
            val settings = snapshot.sources[extension.packageName]
            val enabled = settings?.enabled == true && extension.trusted
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                extension.source?.name ?: extension.label,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                extension.version,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            enabled,
                            onCheckedChange = { checked ->
                                if (checked &&
                                    !extension.trusted
                                ) {
                                    trust = extension
                                } else {
                                    configure(
                                        extension,
                                        checked,
                                        null,
                                    )
                                }
                            },
                            enabled = extension.compatible && !busy,
                            modifier = Modifier.semantics {
                                contentDescription = extension.source?.name ?: extension.label
                            },
                        )
                    }
                    if (!extension.compatible || extension.failed) {
                        val errorLabel = if (!extension.compatible) {
                            R.string.news_incompatible
                        } else {
                            R.string.news_extension_error
                        }
                        Text(stringResource(errorLabel), style = MaterialTheme.typography.bodySmall)
                    }
                    if (enabled) {
                        Text(stringResource(R.string.news_notifications), style = MaterialTheme.typography.labelLarge)
                        NewsAlerts.entries.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                    selected = settings?.alerts == option,
                                    enabled = !busy,
                                    role = Role.RadioButton,
                                ) {
                                    configure(
                                        extension,
                                        true,
                                        option,
                                    )
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val label = when (option) {
                                    NewsAlerts.OFF -> R.string.news_alert_off
                                    NewsAlerts.PERSONAL -> R.string.news_alert_personal
                                    NewsAlerts.ALL -> R.string.news_alert_all
                                }
                                Text(
                                    stringResource(label),
                                    Modifier.weight(1f),
                                    color = if (settings?.alerts == option) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                RadioButton(settings?.alerts == option, onClick = null, enabled = !busy)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.news_alert_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            item {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            context.packageName,
                        ),
                    )
                }) { Text(stringResource(R.string.news_permission)) }
            }
        }
    }
    trust?.let { extension ->
        AlertDialog(
            onDismissRequest = { trust = null },
            title = { Text(stringResource(R.string.news_trust)) },
            text = {
                Text(
                    stringResource(
                        R.string.news_trust_body,
                        extension.metadata.signers.joinToString("\n"),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    trust = null
                    configure(
                        extension,
                        true,
                        null,
                    )
                }) { Text(stringResource(R.string.news_enable)) }
            },
            dismissButton = { TextButton(onClick = { trust = null }) { Text(stringResource(R.string.news_cancel)) } },
        )
    }
}

class NewsStandaloneScreen : Screen {
    @Composable override fun Content() {
        val model = rememberScreenModel { NewsScreenModel() }
        val navigator = LocalNavigator.currentOrThrow
        NewsSettingsFrame(stringResource(R.string.news_title), { navigator.pop() }) { padding ->
            Column(Modifier.padding(padding)) { NewsContent(model) }
        }
    }
}

class NewsInterestsScreen : Screen {
    @Composable
    override fun Content() {
        val repository = remember { Injekt.get<NewsRepository>() }
        val snapshot by repository.store.state.collectAsState()
        var library by remember { mutableStateOf(NewsPersonalLibrary()) }
        var query by remember { mutableStateOf("") }
        var connections by remember { mutableStateOf(false) }
        var chosen by remember { mutableStateOf<Pair<String, NewsTopic>?>(null) }
        var failed by remember { mutableStateOf(false) }
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            try {
                library = NewsPersonalTitles().load()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failed = true
            }
        }
        NewsSettingsFrame(stringResource(R.string.news_personalize), { navigator.pop() }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!connections, { connections = false }, label = {
                            Text(stringResource(R.string.news_your_titles))
                        })
                        FilterChip(connections, { connections = true }, label = {
                            Text(stringResource(R.string.news_connections))
                        })
                    }
                    OutlinedTextField(
                        query,
                        { query = it.take(256) },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.news_filter_titles)) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (failed) Text(stringResource(R.string.news_storage_error))
                }
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            stringResource(if (connections) R.string.news_match_help else R.string.news_personal_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (connections) {
                        val topics = snapshot.articles.values.flatMap { article ->
                            article.article.topics.map { article.source to it }
                        }.distinctBy { (source, topic) -> NewsRules.topicKey(source, topic.id) }
                            .filter { it.second.title.contains(query, true) }.sortedBy { it.second.title }
                        items(topics, key = { (source, topic) -> NewsRules.topicKey(source, topic.id) }) { entry ->
                            val linked = snapshot.mappings[NewsRules.topicKey(entry.first, entry.second.id)].orEmpty()
                            Surface(
                                onClick = { chosen = entry },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    val publisher = snapshot.articles.values
                                        .firstOrNull { it.source == entry.first }?.article?.publisher.orEmpty()
                                    Text(
                                        publisher,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(entry.second.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(
                                            if (linked.isEmpty()) R.string.news_match else R.string.news_connected,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        if (topics.isEmpty()) item { Text(stringResource(R.string.news_no_topics)) }
                    } else {
                        val titles = library.titles.filter { it.title.contains(query, true) }
                            .distinctBy { it.medium to (if (it.ids.isEmpty()) it.title else it.ids) }
                            .sortedBy { it.title }
                        items(titles) { title ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(title.title, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            if (title.ids.isEmpty()) {
                                                stringResource(R.string.news_no_identity)
                                            } else {
                                                title.medium.name.lowercase().replaceFirstChar { it.uppercase() }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        title.ids.isNotEmpty() && title.ids.none { it in snapshot.excluded },
                                        { enabled ->
                                            scope.launch {
                                                try {
                                                    repository.store.update {
                                                        it.copy(
                                                            excluded = if (enabled) {
                                                                it.excluded - title.ids
                                                            } else {
                                                                it.excluded + title.ids
                                                            },
                                                        )
                                                    }
                                                } catch (cancel: CancellationException) {
                                                    throw cancel
                                                } catch (_: Exception) {
                                                    failed = true
                                                }
                                            }
                                        },
                                        enabled = title.ids.isNotEmpty(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        chosen?.let { (source, topic) -> NewsTopicDialog(source, topic) { chosen = null } }
    }
}

@Composable
internal fun NewsTopicLinks(item: StoredNews) {
    var topic by remember { mutableStateOf<NewsTopic?>(null) }
    Column {
        item.article.topics.forEach { entry -> TextButton(onClick = { topic = entry }) { Text(entry.title) } }
    }
    topic?.let { selected -> NewsTopicDialog(item.source, selected) { topic = null } }
}

@Composable
private fun NewsTopicDialog(source: String, topic: NewsTopic, onClose: () -> Unit) {
    val repository = remember { Injekt.get<NewsRepository>() }
    val snapshot by repository.store.state.collectAsState()
    var library by remember { mutableStateOf(NewsPersonalLibrary()) }
    var query by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            library = NewsPersonalTitles().load()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            failed = true
        }
    }
    val connect: (Set<nyanime.news.api.NewsCatalogId>?) -> Unit = { ids ->
        scope.launch {
            busy = true
            try {
                val key = NewsRules.topicKey(source, topic.id)
                repository.store.update {
                    it.copy(mappings = if (ids == null) it.mappings - key else it.mappings + (key to ids))
                }
                onClose()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.news_match)) },
        text = {
            Column {
                Text(topic.title, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.news_match_help), style = MaterialTheme.typography.bodySmall)
                if (failed) Text(stringResource(R.string.news_storage_error))
                OutlinedTextField(
                    query,
                    { query = it.take(256) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.news_filter_titles)) },
                )
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    val tokens = topic.title.split(' ').filter { it.length > 2 }
                    val ranked = library.titles.filter { it.title.contains(query, true) }
                        .sortedWith(
                            compareByDescending<eu.kanade.tachiyomi.data.news.NewsPersonalTitle> { title ->
                                tokens.count { title.title.contains(it, true) }
                            }.thenBy { it.title },
                        )
                    items(ranked) { title ->
                        TextButton(onClick = { connect(title.ids) }, enabled = !busy && title.ids.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(title.title)
                                Text(
                                    title.medium.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    if (ranked.isEmpty()) {
                        item {
                            Text(stringResource(R.string.news_no_identity), Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.news_close)) } },
        dismissButton = {
            if (NewsRules.topicKey(source, topic.id) in snapshot.mappings) {
                TextButton(onClick = { connect(null) }, enabled = !busy) {
                    Text(stringResource(R.string.news_unlink))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsSettingsFrame(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.news_close),
                        )
                    }
                },
            )
        },
        content = content,
    )
}

package eu.kanade.presentation.components.releases

import android.content.ClipDescription
import android.content.ClipboardManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.releases.AiringRefreshJob
import eu.kanade.tachiyomi.data.releases.AnimeScheduleException
import eu.kanade.tachiyomi.data.releases.AnimeSchedulePreferences
import eu.kanade.tachiyomi.data.releases.AnimeScheduleRepository
import eu.kanade.tachiyomi.data.releases.ReleaseAgendaWidget
import eu.kanade.tachiyomi.data.releases.ReleaseMedium
import eu.kanade.tachiyomi.data.releases.ReleaseReminders
import eu.kanade.tachiyomi.data.releases.ScheduleAirType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.util.collectAsState

class AnimeScheduleScreen : Screen() {
    @Composable override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val uri = LocalUriHandler.current
        val scope = rememberCoroutineScope()
        val preferences = remember { AnimeSchedulePreferences() }
        val connected by preferences.connected.collectAsState()
        val enabled by preferences.enabled.collectAsState()
        val channel by preferences.preferredType.collectAsState()
        val state by preferences.state.collectAsState()
        var browsing by rememberSaveable { mutableStateOf(false) }
        // Secrets never enter saved-instance state, links, logs or backups.
        var token by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        fun changed() {
            scope.launch(Dispatchers.IO) {
                ReleaseReminders.schedule(context)
                ReleaseAgendaWidget.refresh(context)
            }
            AiringRefreshJob.enqueue(context)
        }
        Scaffold(topBar = {
            AppBar(title = "AnimeSchedule", navigateUp = { if (browsing) browsing = false else navigator.pop() })
        }) { padding ->
            if (browsing) {
                ScheduleSetupBrowser(Modifier.fillMaxSize().padding(padding), onDone = { browsing = false })
            } else {
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.schedule_heading),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                stringResource(R.string.schedule_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.schedule_languages),
                                style = MaterialTheme.typography.labelLarge,
                                color = releaseColor(ReleaseMedium.ANIME),
                            )
                        }
                    }
                    if (connected) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.schedule_use),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(
                                        if (enabled) R.string.schedule_connected else R.string.schedule_paused,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(enabled, onCheckedChange = {
                                preferences.enabled.set(it)
                                changed()
                            })
                        }
                        Text(stringResource(R.string.schedule_channel), style = MaterialTheme.typography.titleMedium)
                        ScheduleAirType.entries.forEach { type ->
                            Surface(
                                onClick = {
                                    preferences.preferredType.set(type.name)
                                    changed()
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (channel ==
                                    type.name
                                ) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = channel == type.name, onClick = null)
                                    Text(scheduleTypeLabel(type), Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.schedule_channel_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = {
                            ReleaseAgendaWidget.pin(context)
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.schedule_widget)) }
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AnimeScheduleRepository().disconnect() }
                                changed()
                            }
                        }) { Text(stringResource(R.string.schedule_disconnect)) }
                    } else {
                        Text(stringResource(R.string.schedule_setup), style = MaterialTheme.typography.titleLarge)
                        ScheduleStep("1", stringResource(R.string.schedule_step_account))
                        ScheduleStep("2", stringResource(R.string.schedule_step_token))
                        Button(onClick = {
                            browsing = true
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.schedule_start)) }
                        ScheduleStep("3", stringResource(R.string.schedule_step_verify))
                        OutlinedTextField(
                            value = token,
                            onValueChange = {
                                token = it
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.schedule_token)) },
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !busy,
                        )
                        TextButton(enabled = !busy, onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            if (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ==
                                true
                            ) {
                                token = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().take(8192)
                            }
                        }) { Text(stringResource(R.string.schedule_paste)) }
                        Button(enabled = !busy && token.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { AnimeScheduleRepository().connect(token) }
                                    token = ""
                                    changed()
                                } catch (
                                    cancelled: CancellationException,
                                ) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error =
                                        context.getString(
                                            scheduleErrorResource(
                                                (failure as? AnimeScheduleException)?.reason
                                                    ?: if (failure is IllegalArgumentException) "AUTH" else "NETWORK",
                                            ),
                                        )
                                } finally {
                                    busy = false
                                }
                            }
                        }) { Text(stringResource(if (busy) R.string.schedule_verifying else R.string.schedule_verify)) }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    val message =
                        error ?: state.takeIf { it.isNotEmpty() }?.let { context.getString(scheduleErrorResource(it)) }
                    if (message !=
                        null
                    ) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.schedule_fallback),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.schedule_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        uri.openUri("https://animeschedule.net")
                    }) { Text(stringResource(R.string.schedule_credit)) }
                }
            }
        }
    }
}

@Composable private fun ScheduleStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                number,
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The guide surrounds the real HTTPS page. No form cropping, credential scraping or JS bridge. */
@Composable private fun ScheduleSetupBrowser(modifier: Modifier, onDone: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var view by remember { mutableStateOf<WebView?>(null) }
    var page by remember { mutableStateOf("https://animeschedule.net/login") }
    var apiPage by remember { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    BackHandler { if (view?.canGoBack() == true) view?.goBack() else onDone() }
    DisposableEffect(Unit) {
        onDispose {
            view?.stopLoading()
            view?.destroy()
            view = null
        }
    }
    Column(modifier) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.schedule_browser_guide)) }
                if (expanded) {
                    Text(
                        stringResource(R.string.schedule_browser_steps),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    apiPage?.let { target ->
                        TextButton(onClick = {
                            view?.loadUrl(target)
                        }) { Text(stringResource(R.string.schedule_browser_api)) }
                    }
                    TextButton(onClick = { uri.openUri(page) }) {
                        Icon(Icons.Outlined.OpenInNew, null)
                        Text(stringResource(R.string.schedule_browser_external), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = {
            WebView(context).apply {
                view = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(webView: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        val url = request.url
                        if (url.scheme == "https" &&
                            url.host in listOf("animeschedule.net", "www.animeschedule.net")
                        ) {
                            return false
                        }
                        if (url.scheme == "https") uri.openUri(url.toString())
                        return true
                    }
                    override fun onPageStarted(
                        webView: WebView,
                        url: String?,
                        icon: android.graphics.Bitmap?,
                    ) {
                        loading =
                            true
                        if (url != null) page = url
                    }
                    override fun onPageFinished(webView: WebView, url: String?) {
                        loading = false
                        // Read navigation links only, never inputs or page text containing a secret.
                        webView.evaluateJavascript(
                            "Array.from(document.querySelectorAll('a[href]')).map(a=>a.getAttribute('href')).find(h=>/^\\/users\\/[^/]+\\/settings(?:\\/api)?$/.test(h)) || ''",
                        ) { result ->
                            val path = runCatching {
                                kotlinx.serialization.json.Json.decodeFromString<String>(result)
                            }.getOrNull()
                            if (path != null &&
                                Regex("/users/[A-Za-z0-9_-]+/settings(?:/api)?").matches(path)
                            ) {
                                apiPage =
                                    "https://animeschedule.net" + path.removeSuffix("/api") + "/api"
                            }
                        }
                    }
                }
                loadUrl(page)
            }
        })
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.schedule_browser_done))
        }
    }
}

internal fun scheduleErrorResource(reason: String): Int = when (reason) {
    "AUTH" -> R.string.schedule_error_auth
    "RATE_LIMIT" -> R.string.schedule_error_limit
    "IDENTITY" -> R.string.schedule_error_identity
    else -> R.string.schedule_error_network
}

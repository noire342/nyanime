package eu.kanade.presentation.more

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Upgrade
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import eu.kanade.presentation.motion.ModernMotion
import eu.kanade.presentation.motion.appMotionEnabled
import eu.kanade.presentation.theme.NyanimeWordmark
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.UpdateScreenPhase

@Composable
fun NewUpdateScreen(
    versionName: String,
    installedVersion: String,
    changelogInfo: String,
    phase: UpdateScreenPhase = UpdateScreenPhase.AVAILABLE,
    downloadProgress: Int? = null,
    installError: String? = null,
    inAppInstallation: Boolean = true,
    onCancelDownload: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onRejectUpdate: () -> Unit,
    onAcceptUpdate: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val notes = remember(changelogInfo) { updateReleaseNotes(changelogInfo) }
    val motion = appMotionEnabled()
    val compact = LocalDensity.current.fontScale > 1.2f || LocalConfiguration.current.screenHeightDp < 640
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { UpdateTopBar(onRejectUpdate, onOpenInBrowser) },
        bottomBar = {
            UpdateActions(phase, installError, inAppInstallation, motion, onRejectUpdate, onAcceptUpdate)
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UpdateVersionCard(versionName, installedVersion)
            AnimatedVisibility(
                visible = phase != UpdateScreenPhase.AVAILABLE,
                enter = if (motion) {
                    ModernMotion.enter() + expandVertically(tween(ModernMotion.RESIZE_MILLIS))
                } else {
                    EnterTransition.None
                },
                exit = if (motion) {
                    ModernMotion.exit() + shrinkVertically(tween(ModernMotion.RESIZE_MILLIS))
                } else {
                    ExitTransition.None
                },
            ) {
                UpdateDownloadStatus(phase, downloadProgress, motion, compact, onCancelDownload)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_update_whats_new),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Icon(Icons.Outlined.Notes, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
            // Only release notes scroll. Version, download status and actions keep their place.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (notes.isEmpty()) {
                    item(key = "empty-notes") {
                        Text(
                            stringResource(R.string.app_update_no_notes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(notes, key = { index, _ -> "notes-$index" }) { _, section ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            section.title?.let {
                                Text(it, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                            }
                            RichText(
                                style = RichTextStyle(
                                    stringStyle = RichTextStringStyle(linkStyle = SpanStyle(color = colors.primary)),
                                ),
                            ) { Markdown(content = section.markdown) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateVersionCard(versionName: String, installedVersion: String) {
    // Version layout is independent of frequently changing download progress.
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.primary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.14f)),
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.app_update_available_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
                BasicText(
                    versionName.removePrefix("v"),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 34.sp),
                )
                Text(
                    stringResource(R.string.app_update_current_version, installedVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Surface(shape = RoundedCornerShape(16.dp), color = colors.primary.copy(alpha = 0.10f)) {
                Icon(Icons.Outlined.Upgrade, null, Modifier.padding(12.dp).size(28.dp), tint = colors.primary)
            }
        }
    }
}

@Composable
private fun UpdateTopBar(onClose: () -> Unit, onDetails: () -> Unit) {
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).padding(WindowInsets.statusBars.asPaddingValues()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NyanimeWordmark(Modifier.weight(1f))
            IconButton(onClick = onDetails) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.app_update_full_release))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, stringResource(R.string.app_update_close))
            }
        }
    }
}

@Composable
private fun UpdateDownloadStatus(
    phase: UpdateScreenPhase,
    progress: Int?,
    motion: Boolean,
    compact: Boolean,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isError = phase == UpdateScreenPhase.FAILED || phase == UpdateScreenPhase.UNAVAILABLE
    val tint = if (isError) colors.error else colors.primary
    val displayProgress = progress?.coerceIn(0, 100)
    val fraction by animateFloatAsState(
        (displayProgress ?: 0) / 100f,
        tween(if (motion) ModernMotion.RESIZE_MILLIS else 0),
        label = "updateProgress",
    )
    Surface(
        modifier = Modifier.animateContentSize(tween(if (motion) ModernMotion.RESIZE_MILLIS else 0)),
        shape = RoundedCornerShape(24.dp),
        color = tint.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(if (compact) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                    Icon(
                        when {
                            isError -> Icons.Outlined.ErrorOutline
                            phase == UpdateScreenPhase.READY -> Icons.Outlined.Check
                            phase == UpdateScreenPhase.VERIFYING -> Icons.Outlined.VerifiedUser
                            else -> Icons.Outlined.SystemUpdate
                        },
                        null,
                        Modifier.padding(10.dp).size(24.dp),
                        tint = tint,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(phase.statusTitle()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!compact) {
                        Text(
                            stringResource(phase.statusDescription()),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            if (phase.busy) {
                if (phase == UpdateScreenPhase.DOWNLOADING && displayProgress != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.weight(1f),
                            color = tint,
                            trackColor = tint.copy(alpha = 0.12f),
                        )
                        Text("$displayProgress%", style = MaterialTheme.typography.labelLarge, color = tint)
                    }
                } else {
                    if (motion) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = tint,
                            trackColor = tint.copy(alpha = 0.12f),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { 0.25f },
                            modifier = Modifier.fillMaxWidth(),
                            color = tint,
                            trackColor = tint.copy(alpha = 0.12f),
                        )
                    }
                }
            }
            if (phase.cancellable) {
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.app_update_cancel_download))
                }
            }
        }
    }
}

@Composable
private fun UpdateActions(
    phase: UpdateScreenPhase,
    error: String?,
    inAppInstallation: Boolean,
    motion: Boolean,
    onLater: () -> Unit,
    onAccept: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth().animateContentSize(tween(if (motion) ModernMotion.RESIZE_MILLIS else 0))
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = onAccept,
                enabled = !phase.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = { ModernMotion.transform(motion) },
                    label = "updateAction",
                ) { state ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            when {
                                state == UpdateScreenPhase.READY -> Icons.AutoMirrored.Outlined.ArrowForward
                                state == UpdateScreenPhase.FAILED || state == UpdateScreenPhase.UNAVAILABLE ->
                                    Icons.Outlined.RestartAlt
                                else -> Icons.Outlined.Download
                            },
                            null,
                            Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(state.actionLabel()),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (!inAppInstallation && phase == UpdateScreenPhase.AVAILABLE) {
                Text(
                    stringResource(R.string.app_update_notification_install),
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (phase.busy) R.string.app_update_continue else R.string.app_update_later))
            }
        }
    }
}

private fun UpdateScreenPhase.statusTitle() = when (this) {
    UpdateScreenPhase.AVAILABLE -> R.string.app_update_available_label
    UpdateScreenPhase.QUEUED -> R.string.app_update_waiting
    UpdateScreenPhase.DOWNLOADING -> R.string.app_update_downloading
    UpdateScreenPhase.VERIFYING -> R.string.app_update_verifying
    UpdateScreenPhase.READY -> R.string.app_update_downloaded
    UpdateScreenPhase.FAILED -> R.string.app_update_failed
    UpdateScreenPhase.CANCELLED -> R.string.app_update_cancelled
    UpdateScreenPhase.UNAVAILABLE -> R.string.app_update_file_unavailable
}

private fun UpdateScreenPhase.statusDescription() = when (this) {
    UpdateScreenPhase.AVAILABLE -> R.string.app_update_no_notes
    UpdateScreenPhase.QUEUED -> R.string.app_update_waiting_description
    UpdateScreenPhase.DOWNLOADING -> R.string.app_update_downloading_description
    UpdateScreenPhase.VERIFYING -> R.string.app_update_verifying_description
    UpdateScreenPhase.READY -> R.string.app_update_downloaded_description
    UpdateScreenPhase.FAILED -> R.string.app_update_failed_description
    UpdateScreenPhase.CANCELLED -> R.string.app_update_cancelled_description
    UpdateScreenPhase.UNAVAILABLE -> R.string.app_update_file_unavailable_description
}

private fun UpdateScreenPhase.actionLabel() = when (this) {
    UpdateScreenPhase.READY -> R.string.app_update_install_now
    UpdateScreenPhase.QUEUED -> R.string.app_update_waiting
    UpdateScreenPhase.DOWNLOADING -> R.string.app_update_downloading
    UpdateScreenPhase.VERIFYING -> R.string.app_update_verifying
    UpdateScreenPhase.FAILED, UpdateScreenPhase.UNAVAILABLE -> R.string.app_update_retry
    else -> R.string.app_update_download
}

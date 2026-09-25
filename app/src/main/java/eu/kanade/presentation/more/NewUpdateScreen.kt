package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.InfoScreen

@Composable
fun NewUpdateScreen(
    versionName: String,
    changelogInfo: String,
    inAppInstallation: Boolean = false,
    downloadRunning: Boolean = false,
    downloadProgress: Int? = null,
    downloadStatus: String? = null,
    installError: String? = null,
    acceptText: String = stringResource(MR.strings.update_check_confirm),
    canAccept: Boolean = true,
    onCancelDownload: (() -> Unit)? = null,
    onOpenInBrowser: () -> Unit,
    onRejectUpdate: () -> Unit,
    onAcceptUpdate: () -> Unit,
) {
    InfoScreen(
        icon = Icons.Outlined.NewReleases,
        headingText = stringResource(MR.strings.update_check_notification_update_available),
        subtitleText = versionName,
        acceptText = acceptText,
        onAcceptClick = onAcceptUpdate,
        canAccept = canAccept,
        rejectText = stringResource(MR.strings.action_not_now),
        onRejectClick = onRejectUpdate,
    ) {
        if (inAppInstallation && downloadStatus != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.padding.medium),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.padding(MaterialTheme.padding.medium)) {
                    Text(downloadStatus, style = MaterialTheme.typography.bodyMedium)
                    if (downloadRunning) {
                        if (downloadProgress != null) {
                            LinearProgressIndicator(
                                progress = { downloadProgress.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.padding.medium),
                            )
                            Text("$downloadProgress%", style = MaterialTheme.typography.labelMedium)
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.padding.medium),
                            )
                        }
                    }
                    if (onCancelDownload != null) {
                        TextButton(onClick = onCancelDownload) { Text(stringResource(MR.strings.action_cancel)) }
                    }
                }
            }
        }
        if (installError != null) {
            Text(
                installError,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = MaterialTheme.padding.medium),
            )
        }
        RichText(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.large),
            style = RichTextStyle(
                stringStyle = RichTextStringStyle(
                    linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
                ),
            ),
        ) {
            Markdown(content = changelogInfo)

            TextButton(
                onClick = onOpenInBrowser,
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            ) {
                Text(text = stringResource(MR.strings.update_check_open))
                Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                Icon(imageVector = Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NewUpdateScreenPreview() {
    TachiyomiPreviewTheme {
        NewUpdateScreen(
            versionName = "v0.99.9",
            changelogInfo = """
                ## Yay
                Foobar

                ### More info
                - Hello
                - World
            """.trimIndent(),
            onOpenInBrowser = {},
            onRejectUpdate = {},
            onAcceptUpdate = {},
        )
    }
}

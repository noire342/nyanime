package eu.kanade.tachiyomi.ui.more

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.work.WorkInfo
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.NewUpdateScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.openInBrowser
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class NewUpdateScreen(
    private val versionName: String,
    private val changelogInfo: String,
    private val releaseLink: String,
    private val downloadLink: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val preferences = remember { Injekt.get<UiPreferences>() }
        val installInApp by preferences.inAppUpdateInstallation().changes().collectAsState(
            initial = preferences.inAppUpdateInstallation().get(),
        )
        val works by remember(context) { AppUpdateDownloadJob.observe(context) }.collectAsState(initial = emptyList())
        val work = works.lastOrNull { AppUpdateDownloadJob.updateTag(downloadLink) in it.tags }
        val active = installInApp &&
            work?.state in listOf(
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED,
                WorkInfo.State.RUNNING,
            )
        val apk = work?.takeIf { it.state == WorkInfo.State.SUCCEEDED }
            ?.outputData?.getString(AppUpdateDownloadJob.OUTPUT_APK_PATH)
            ?.let(::File)?.takeIf(File::isFile)
        var installError by remember { mutableStateOf<String?>(null) }
        val changelogInfoNoChecksum = remember {
            changelogInfo.replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
        }

        NewUpdateScreen(
            versionName = versionName,
            changelogInfo = changelogInfoNoChecksum,
            inAppInstallation = installInApp,
            downloadRunning = work?.state == WorkInfo.State.RUNNING,
            downloadProgress = if (work?.state == WorkInfo.State.RUNNING) {
                work.progress.getInt(AppUpdateDownloadJob.PROGRESS, -1).takeIf { it >= 0 }
            } else {
                null
            },
            downloadStatus = when {
                apk != null -> "Download completato. L'aggiornamento è pronto da installare."
                work?.state == WorkInfo.State.ENQUEUED || work?.state == WorkInfo.State.BLOCKED ->
                    "In attesa della connessione. Il download partirà appena possibile."
                work?.state == WorkInfo.State.RUNNING ->
                    "Scaricamento in corso. Puoi continuare a usare l'app; " +
                        "l'avanzamento resta visibile anche nelle notifiche."
                work?.state == WorkInfo.State.FAILED -> "Download non riuscito. Puoi riprovare."
                work?.state == WorkInfo.State.CANCELLED -> "Download annullato. Puoi ricominciare quando vuoi."
                else -> null
            },
            installError = installError,
            acceptText = when {
                installInApp && apk != null -> "Installa aggiornamento"
                installInApp && work?.state == WorkInfo.State.FAILED -> "Riprova il download"
                else -> "Scarica aggiornamento"
            },
            canAccept = !active,
            onCancelDownload = if (active) {
                { AppUpdateDownloadJob.stop(context) }
            } else {
                null
            },
            onOpenInBrowser = { context.openInBrowser(releaseLink) },
            onRejectUpdate = navigator::pop,
            onAcceptUpdate = {
                if (installInApp && apk != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !context.packageManager.canRequestPackageInstalls()
                        ) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = "package:${context.packageName}".toUri()
                                },
                            )
                            installError = "Consenti l'installazione da Nyanime, poi torna qui e premi Installa."
                        } else {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(apk.getUriCompat(context), "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                            )
                            installError = null
                        }
                    } catch (e: Exception) {
                        installError = "Impossibile aprire l'installer Android: ${e.localizedMessage.orEmpty()}"
                    }
                } else {
                    installError = null
                    AppUpdateDownloadJob.start(context, downloadLink, versionName)
                    if (!installInApp) navigator.pop()
                }
            },
        )
    }
}

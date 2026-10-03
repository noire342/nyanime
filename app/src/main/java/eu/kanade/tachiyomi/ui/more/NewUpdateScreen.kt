package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkInfo
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.NewUpdateScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.data.updater.ReadyAppUpdate
import eu.kanade.tachiyomi.data.updater.UpdateScreenPhase
import eu.kanade.tachiyomi.data.updater.installReadyAppUpdate
import eu.kanade.tachiyomi.data.updater.readyAppUpdate
import eu.kanade.tachiyomi.data.updater.updateScreenPhase
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

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
        var startingId by remember { mutableStateOf<UUID?>(null) }
        LaunchedEffect(work?.id) {
            if (work?.id == startingId) startingId = null
        }
        // Recheck after returning from Android's installer/permission screen, off the UI thread.
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
        val validation by produceState<UpdateValidation?>(null, work?.id, work?.state, lifecycle) {
            if (value?.workId != work?.id || work?.state != WorkInfo.State.SUCCEEDED) value = null
            if (work?.state == WorkInfo.State.SUCCEEDED && lifecycle.isAtLeast(Lifecycle.State.STARTED)) {
                value = withContext(Dispatchers.IO) { UpdateValidation(work.id, work.readyAppUpdate(context)) }
            }
        }
        val ready = validation?.takeIf { it.workId == work?.id }?.update
        val phase = if (startingId != null) {
            UpdateScreenPhase.QUEUED
        } else {
            updateScreenPhase(
                work?.state,
                validation?.workId == work?.id,
                ready != null,
            )
        }
        var installError by remember { mutableStateOf<String?>(null) }
        NewUpdateScreen(
            versionName = versionName,
            installedVersion = BuildConfig.VERSION_NAME,
            changelogInfo = changelogInfo,
            phase = phase,
            inAppInstallation = installInApp,
            downloadProgress = if (phase == UpdateScreenPhase.DOWNLOADING) {
                work?.progress?.getInt(AppUpdateDownloadJob.PROGRESS, -1)?.takeIf { it >= 0 }
            } else {
                null
            },
            installError = installError,
            onCancelDownload = { AppUpdateDownloadJob.stop(context) },
            onOpenInBrowser = { context.openInBrowser(releaseLink) },
            onRejectUpdate = navigator::pop,
            onAcceptUpdate = {
                if (!phase.busy && startingId == null) {
                    if (ready != null) {
                        installError = installReadyAppUpdate(context, ready.apk)
                    } else {
                        installError = null
                        startingId = AppUpdateDownloadJob.start(context, downloadLink, versionName)
                        if (!installInApp) navigator.pop()
                    }
                }
            },
        )
    }
}

private data class UpdateValidation(val workId: UUID, val update: ReadyAppUpdate?)

package eu.kanade.tachiyomi.data.download.anime.ultra

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import tachiyomi.domain.download.service.DownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Independent from playback and normal downloads; failures leave the original available. */
@UnstableApi
class UltraDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val title = inputData.getString(TITLE).orEmpty()
    private var stage = "Preparazione Ultra"
    private var percent = 0

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_DOWNLOADER_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Anime4K Ultra · $title")
            .setContentText(stage)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Annulla", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 35) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            },
        )
    }

    override suspend fun doWork(): Result {
        // Unique jobs allow cancelling one conversion without cancelling the rest of the queue.
        if (UltraPlaybackGuard.playerActive || !gpu.tryLock()) return Result.retry()
        val scratch = File(applicationContext.cacheDir, "ultra/$id")
        var folder: UniFile? = null
        var published = false
        try {
            folder = UniFile.fromUri(applicationContext, Uri.parse(requireNotNull(inputData.getString(FOLDER))))
            require(folder?.exists() == true) { "Il download originale non è più disponibile" }
            if (UltraFiles.completed(applicationContext, folder) != null) return Result.success(output("Completato"))
            val original = requireNotNull(UltraFiles.original(folder)) { "Il download originale non è più disponibile" }
            setForeground(getForegroundInfo())
            withContext(Dispatchers.IO) {
                scratch.parentFile?.listFiles()?.forEach { it.deleteRecursively() }
                check(scratch.mkdirs()) { "Impossibile preparare lo spazio Ultra" }
            }
            val video = File(scratch, "video.mp4")
            val muxed = File(scratch, "complete.mkv")
            val originalMedia = withContext(Dispatchers.IO) { UltraExporter.inspect(applicationContext, original.uri) }
            val (w, h) = UltraShaderGraph.target(originalMedia.width, originalMedia.height)
            val estimatedBytes = originalMedia.durationMs * (w.toLong() * h * 6).coerceIn(8_000_000, 60_000_000) / 8000
            require(scratch.usableSpace > estimatedBytes * 2 + 256L * 1024 * 1024) {
                "Spazio insufficiente per preparare Ultra; l'originale è conservato"
            }
            val media = UltraExporter.export(applicationContext, original.uri, video) { value ->
                if (UltraPlaybackGuard.playerActive) throw UltraPlaybackGuard.YieldToPlayer()
                val power = applicationContext.getSystemService(PowerManager::class.java)
                check(Build.VERSION.SDK_INT < 29 || power.currentThermalStatus < PowerManager.THERMAL_STATUS_SEVERE) {
                    "Elaborazione interrotta per temperatura elevata. Lascia raffreddare il telefono e riprova."
                }
                update("Elaborazione Anime4K · $value%", value * 9 / 10)
            }
            update("Conservazione di audio e sottotitoli", 92)
            UltraMuxer.remux(applicationContext, video, original.uri, muxed)
            withContext(Dispatchers.IO) {
                val verified = UltraExporter.inspect(applicationContext, Uri.fromFile(muxed))
                check(
                    verified.width == media.width &&
                        verified.height == media.height &&
                        kotlin.math.abs(verified.durationMs - originalMedia.durationMs) <=
                        (originalMedia.durationMs / 100).coerceIn(100L, 1500L),
                ) {
                    "Verifica del file Ultra fallita"
                }
                currentCoroutineContext().ensureActive()
                update("Salvataggio del video Ultra", 97)
                folder.findFile(UltraFiles.MARKER)?.delete()
                folder.findFile(UltraFiles.PART)?.delete()
                val temporary = checkNotNull(folder.createFile(UltraFiles.PART))
                applicationContext.contentResolver.openOutputStream(temporary.uri, "wt")!!.use { destination ->
                    muxed.inputStream().use { source ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer)
                            if (read == -1) break
                            destination.write(buffer, 0, read)
                        }
                    }
                }
                check(temporary.length() == muxed.length()) { "Copia Ultra incompleta" }
                currentCoroutineContext().ensureActive()
                folder.findFile(UltraFiles.VIDEO)?.delete()
                check(temporary.renameTo(UltraFiles.VIDEO)) { "Impossibile salvare Ultra" }
                val ready = checkNotNull(folder.findFile(UltraFiles.VIDEO))
                UltraFiles.markComplete(applicationContext, folder, ready, media)
                published = true
            }
            return Result.success(output("Completato"))
        } catch (_: UltraPlaybackGuard.YieldToPlayer) {
            return Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            // This is a handled conversion failure, not a failed download or a broken work chain.
            val reason = generateSequence(error) { it.cause }.last().message
                ?.take(240) ?: "Il dispositivo non supporta questa conversione"
            return Result.success(output("Non completato", reason))
        } finally {
            try {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    scratch.deleteRecursively()
                    if (!published && folder?.let { UltraFiles.completed(applicationContext, it) } == null) {
                        folder?.findFile(UltraFiles.PART)?.delete()
                        folder?.findFile(UltraFiles.MARKER)?.delete()
                        folder?.findFile(UltraFiles.VIDEO)?.delete()
                    }
                }
            } finally {
                gpu.unlock()
            }
        }
    }

    private suspend fun update(message: String, progress: Int) {
        if (stage == message && percent == progress) return
        stage = message
        percent = progress
        setProgress(workDataOf(TITLE to title, STAGE to stage, PROGRESS to percent))
        setForeground(getForegroundInfo())
    }

    private fun output(message: String, error: String = "") = workDataOf(
        TITLE to title,
        FOLDER to inputData.getString(FOLDER),
        STAGE to message,
        ERROR to error,
    )

    companion object {
        const val TAG = "Anime4KUltra"
        const val TITLE = "title"
        const val FOLDER = "folder"
        const val STAGE = "stage"
        const val PROGRESS = "progress"
        const val ERROR = "error"
        private const val NOTIFICATION_ID = -230
        private val gpu = Mutex()

        fun enqueue(context: Context, folder: Uri, title: String) {
            val preferences = Injekt.get<DownloadPreferences>()
            val key = MessageDigest.getInstance("SHA-256").digest(folder.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
            val work = OneTimeWorkRequestBuilder<UltraDownloadWorker>()
                .addTag(TAG)
                .addTag("ultra-title:$title")
                .addTag("ultra-folder:$folder")
                .setInputData(workDataOf(FOLDER to folder.toString(), TITLE to title))
                .setConstraints(
                    Constraints.Builder().setRequiresCharging(preferences.ultraOnlyWhileCharging().get())
                        .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("$TAG:$key", ExistingWorkPolicy.KEEP, work)
        }
    }
}

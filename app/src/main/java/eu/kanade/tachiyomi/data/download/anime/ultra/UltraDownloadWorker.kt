package eu.kanade.tachiyomi.data.download.anime.ultra

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Compatibility entry point for work persisted by earlier releases.
 * No new jobs are scheduled. An old job terminates without touching downloaded files,
 * starting a foreground service, or performing GPU/codec work.
 */
class UltraDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.failure(
        workDataOf("error" to "La conversione Ultra in background non è più disponibile"),
    )
}

package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.work.WorkInfo
import eu.kanade.tachiyomi.util.storage.getUriCompat
import java.io.File

data class ReadyAppUpdate(val id: String, val apk: File)

fun WorkInfo.readyAppUpdate(context: Context): ReadyAppUpdate? {
    if (state != WorkInfo.State.SUCCEEDED) return null
    val apk = outputData.getString(AppUpdateDownloadJob.OUTPUT_APK_PATH)?.let(::File)
        ?.takeIf { it.isFile && it.length() > 0L } ?: return null
    val valid = runCatching {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(apk.absolutePath, 0) ?: return@runCatching false
        val installed = manager.getPackageInfo(context.packageName, 0)
        archive.packageName == context.packageName &&
            isNewerAppUpdate(
                PackageInfoCompat.getLongVersionCode(installed),
                installed.versionName,
                PackageInfoCompat.getLongVersionCode(archive),
                archive.versionName,
            )
    }.getOrDefault(false)
    if (!valid) return null
    return ReadyAppUpdate(id.toString(), apk)
}

/** Preview releases intentionally share a versionCode; their revision suffix increases instead. */
internal fun isNewerAppUpdate(
    installedCode: Long,
    installedName: String?,
    archiveCode: Long,
    archiveName: String?,
): Boolean {
    if (archiveCode != installedCode) return archiveCode > installedCode
    val installed = installedName ?: return false
    val archive = archiveName ?: return false
    val current = installed.substringAfterLast('-').toLongOrNull() ?: return false
    val candidate = archive.substringAfterLast('-').toLongOrNull() ?: return false
    return archive.substringBeforeLast('-') == installed.substringBeforeLast('-') && candidate > current
}

/** Returns a user-facing error when Android needs a permission or has no installer. */
fun installReadyAppUpdate(context: Context, apk: File): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
            },
        )
        "Consenti l'installazione da Nyanime, poi torna nell'app e premi Installa."
    } else {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apk.getUriCompat(context), "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        null
    }
} catch (e: Exception) {
    "Impossibile aprire l'installer Android: ${e.localizedMessage.orEmpty()}"
}

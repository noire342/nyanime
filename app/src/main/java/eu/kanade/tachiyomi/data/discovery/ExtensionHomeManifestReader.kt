package eu.kanade.tachiyomi.data.discovery

import android.content.Context
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import java.util.zip.ZipFile

fun interface ExtensionHomeManifestReader {
    fun read(extension: AnimeExtension.Installed): List<ExtensionHomeManifest>
}

/** Reads shared and private APKs selected by the existing loader; never follows remote manifest URLs. */
class ApkExtensionHomeManifestReader(private val context: Context) : ExtensionHomeManifestReader {
    override fun read(extension: AnimeExtension.Installed): List<ExtensionHomeManifest> = runCatching {
        val info = AnimeExtensionLoader.getAnimeExtensionPackageInfoFromPkgName(context, extension.pkgName)
        val apk = info?.applicationInfo?.sourceDir ?: return emptyList()
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(ExtensionHomeManifest.ASSET_PATH) ?: return emptyList()
            if (entry.size > ExtensionHomeManifest.MAX_BYTES) return emptyList()
            zip.getInputStream(entry).use { input ->
                // Bound uncompressed bytes too: a forged ZIP size must not allow an allocation bomb.
                val bytes = ByteArray(ExtensionHomeManifest.MAX_BYTES + 1)
                var count = 0
                while (count < bytes.size) {
                    val read = input.read(bytes, count, bytes.size - count)
                    if (read < 0) break
                    if (read == 0) return emptyList()
                    count += read
                }
                if (count > ExtensionHomeManifest.MAX_BYTES) {
                    emptyList()
                } else {
                    ExtensionHomeManifest.parse(String(bytes, 0, count, Charsets.UTF_8))
                }
            }
        }
    }.getOrDefault(emptyList())
}

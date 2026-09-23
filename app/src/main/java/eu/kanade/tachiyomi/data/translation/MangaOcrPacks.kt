package eu.kanade.tachiyomi.data.translation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Verified, atomic on-demand downloads. No reader/Ultra worker or device-idle gate is involved. */
class MangaOcrPacks(private val context: Context) {
    data class Pack(val language: String, val bytes: Long, val sha256: String) {
        val url: String get() =
            "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/87416418657359cb625c412a48b6e1d6d41c29bd/$language.traineddata"
    }

    companion object {
        val packs = mapOf(
            "eng" to Pack("eng", 4_113_088, "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"),
            "jpn" to Pack("jpn", 2_471_260, "1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d"),
            "jpn_vert" to
                Pack("jpn_vert", 3_037_480, "bf1e2640954691797e2dc14f38533e601b59ee37958698ae0f0b81dc6f09c71b"),
        )
    }

    private val root = File(context.filesDir, "manga-translation/ocr")
    private val tessdata = File(root, "tessdata")
    private val checked = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun directory(): File = root

    fun usedBytes(): Long = tessdata.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L

    fun isInstalled(language: String): Boolean {
        val pack = packs[language] ?: return false
        val target = File(tessdata, "${pack.language}.traineddata")
        if (!target.isFile || target.length() != pack.bytes) return false
        if (language in checked) return true
        return verify(target, pack).also { if (it) checked += language }
    }

    suspend fun install(language: String, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val pack = requireNotNull(packs[language]) { "Lingua OCR non supportata" }
        check(tessdata.isDirectory || tessdata.mkdirs()) { "Impossibile creare la cartella dei modelli" }
        val target = File(tessdata, "${pack.language}.traineddata")
        if (isInstalled(language)) return@withContext root
        val temp = File(tessdata, "${pack.language}.part")
        temp.delete()
        try {
            val connection = (URL(pack.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Download OCR HTTP ${connection.responseCode}"
                }
                val advertised = connection.contentLengthLong
                check(advertised < 0 || advertised == pack.bytes) { "Dimensione del modello inattesa" }
                var copied = 0L
                connection.inputStream.use { source ->
                    temp.outputStream().buffered().use { sink ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            copied += count
                            check(copied <= pack.bytes) { "Il modello supera la dimensione prevista" }
                            sink.write(buffer, 0, count)
                            progress(copied, pack.bytes)
                        }
                        sink.flush()
                    }
                }
            } finally {
                connection.disconnect()
            }
            check(verify(temp, pack)) { "Verifica SHA-256 del modello non riuscita" }
            check(temp.renameTo(target)) { "Impossibile completare l'installazione del modello" }
            checked += language
            root
        } finally {
            temp.delete()
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        tessdata.listFiles()?.forEach(File::delete)
        checked.clear()
    }

    private fun verify(file: File, pack: Pack): Boolean {
        if (!file.isFile || file.length() != pack.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == pack.sha256
    }
}

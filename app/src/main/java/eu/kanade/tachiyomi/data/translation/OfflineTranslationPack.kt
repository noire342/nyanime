package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Optional SMaLL-100 model pack. Its models are data files, not part of the APK. */
class OfflineTranslationPack(private val context: Context) {
    data class Asset(val name: String, val size: Long, val sha256: String)

    companion object {
        private const val REVISION = "5c2c73ac70bee9c58f5a7ac5e84a36bee25db8ee"
        val assets = listOf(
            Asset(
                "encoder_model.onnx",
                287_317_499,
                "a130f553106646e56c1908094074f354353c45d86b3e8d222b037784035e6dcd",
            ),
            Asset(
                "decoder_model_merged.onnx",
                321_741_340,
                "f966ce4f1f484c2307dc007ef0beadc3a1651220c1ff9b33ea8dddbce468a4b0",
            ),
            Asset("tokenizer.json", 5_807_639, "e4fb8024a6278640a0e4a2f813e4ed4e0b531cadb12a54e10283a2ed5faf6bf3"),
            Asset("lang_tokens.json", 1_885, "c638e65941c57577fff6321d5be72b2b25afc39fde7d6a48adecd2e44b504ddf"),
        )
        val totalBytes = assets.sumOf(Asset::size)
    }

    private val root = File(context.filesDir, "manga-translation/small100-v1")
    private val marker = File(root, "complete")
    private val mutex = Mutex()

    fun ready(): Boolean = marker.readTextOrNull() == REVISION && assets.all { File(root, it.name).length() == it.size }

    fun usedBytes(): Long = root.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L

    fun hasPartialDownload(): Boolean = assets.any { File(root, "${it.name}.part").length() > 0L }

    fun file(name: String): File {
        require(assets.any { it.name == name })
        check(ready()) { "Pacchetto di traduzione non installato" }
        return File(root, name)
    }

    suspend fun install(progress: (Long, Long) -> Unit) = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(root.isDirectory || root.mkdirs()) { "Impossibile creare la cartella dei modelli" }
            if (ready()) return@withContext
            val remaining = assets.sumOf { asset ->
                val file = File(root, asset.name)
                if (file.length() == asset.size && verify(file, asset)) {
                    0L
                } else {
                    asset.size - File(root, "${asset.name}.part").length().coerceIn(0, asset.size)
                }
            }
            check(StatFs(root.absolutePath).availableBytes > remaining + 100L * 1024 * 1024) {
                "Spazio insufficiente: servono almeno ${remaining / 1_000_000 + 100} MB liberi"
            }
            var completed = 0L
            for (asset in assets) {
                val target = File(root, asset.name)
                if (!verify(target, asset)) {
                    val partial = File(root, "${asset.name}.part")
                    download(asset, partial) { bytes -> progress(completed + bytes, totalBytes) }
                    check(verify(partial, asset)) {
                        partial.delete()
                        "Verifica SHA-256 del modello ${asset.name} non riuscita"
                    }
                    check(partial.renameTo(target)) { "Impossibile installare ${asset.name}" }
                }
                completed += asset.size
                progress(completed, totalBytes)
            }
            marker.writeText(REVISION)
        }
    }

    suspend fun remove() = mutex.withLock {
        withContext(Dispatchers.IO) { root.listFiles()?.forEach(File::delete) }
    }

    private suspend fun download(asset: Asset, partial: File, progress: (Long) -> Unit) {
        val path = if (asset.name.endsWith(".onnx")) "onnx/${asset.name}" else asset.name
        var offset = partial.length().takeIf { it in 1 until asset.size } ?: 0L
        if (offset == 0L) partial.delete()
        val connection = (
            URL("https://huggingface.co/casawolice/small100-onnx/resolve/$REVISION/$path")
                .openConnection() as HttpURLConnection
            ).apply {
            connectTimeout = 20_000
            readTimeout = 40_000
            instanceFollowRedirects = true
            if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val status = connection.responseCode
            check(status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_PARTIAL) {
                "Download del modello HTTP $status"
            }
            if (status == HttpURLConnection.HTTP_OK) offset = 0L
            if (status == HttpURLConnection.HTTP_PARTIAL) {
                check(connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true) {
                    "Ripresa del download non valida"
                }
            }
            RandomAccessFile(partial, "rw").use { output ->
                output.setLength(offset)
                output.seek(offset)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var current = offset
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        current += count
                        check(current <= asset.size) { "Il modello supera la dimensione prevista" }
                        output.write(buffer, 0, count)
                        progress(current)
                    }
                    output.fd.sync()
                }
            }
            check(partial.length() == asset.size) { "Download incompleto; riprova per riprendere" }
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(file: File, asset: Asset): Boolean {
        if (!file.isFile || file.length() != asset.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == asset.sha256
    }

    private fun File.readTextOrNull(): String? = runCatching { if (isFile) readText() else null }.getOrNull()
}

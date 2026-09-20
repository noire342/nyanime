package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hippo.unifile.UniFile
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Properties

class UriPartialVideoStore(
    private val context: Context,
    private val file: UniFile,
    private val metadataFile: UniFile,
) : PartialVideoStore {
    override var metadata: PartialVideoMetadata?
        get() = runCatching {
            // Ignore oversized or incomplete state left by an interrupted write.
            require(metadataFile.length() in 1..16_384)
            val state = Properties().apply { metadataFile.openInputStream().use { load(it) } }
            PartialVideoMetadata(
                state.getProperty("identity") ?: return null,
                state.getProperty("etag"),
                state.getProperty("total")?.toLongOrNull() ?: return null,
            )
        }.getOrNull()
        set(value) {
            if (value == null) {
                metadataFile.openOutputStream().use { (it as? FileOutputStream)?.channel?.truncate(0) }
                return
            }
            val state = Properties().apply {
                setProperty("identity", value.identity)
                value.etag?.let { setProperty("etag", it) }
                setProperty("total", value.total.toString())
            }
            metadataFile.openOutputStream().use {
                (it as? FileOutputStream)?.channel?.truncate(0)
                state.store(it, null)
            }
        }

    override fun size() = file.length()

    override fun open(offset: Long): OutputStream {
        val descriptor = context.contentResolver.openFileDescriptor(file.uri, "rw")
            ?: throw VideoStorageException("Cartella download non accessibile. Controlla i permessi di archiviazione.")
        val output = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
        try {
            if (offset == 0L) output.channel.truncate(0)
            output.channel.position(offset)
            if (output.channel.position() != offset) throw IOException("Storage does not support seeking")
            return output
        } catch (e: Exception) {
            output.close()
            throw UnsupportedDirectVideo()
        }
    }
}

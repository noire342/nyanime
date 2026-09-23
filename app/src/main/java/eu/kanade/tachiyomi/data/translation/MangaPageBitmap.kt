package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

data class MangaPageBitmap(val bitmap: Bitmap, val imageHash: String)

/** A bounded decode prevents a very large chapter image from exhausting the reader process. */
suspend fun loadMangaPageBitmap(page: ReaderPage): MangaPageBitmap = withContext(Dispatchers.IO) {
    val stream = requireNotNull(page.stream) { "Pagina non ancora disponibile" }
    val raw = stream().use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var size = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            check(size <= 64 * 1024 * 1024) { "Immagine troppo grande per la traduzione" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Immagine non leggibile" }
    var sample = 1
    // A 1500 × 2152 digital page was previously halved to 750 × 1076; that loses
    // the small speech-balloon glyphs before OCR can inspect them.
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 3072 ||
        (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 8_000_000
    ) {
        sample *= 2
    }
    val bitmap = BitmapFactory.decodeByteArray(
        raw,
        0,
        raw.size,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: error("Immagine non leggibile")
    val hash = MessageDigest.getInstance("SHA-256").digest(raw)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    MangaPageBitmap(bitmap, hash)
}

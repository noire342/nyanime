package eu.kanade.tachiyomi.data.translation

import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Serial per-call OCR instance; the native Tesseract object is never shared between jobs. */
class MangaOcrEngine(private val packs: MangaOcrPacks) {
    suspend fun recognize(
        image: MangaPageBitmap,
        language: String,
    ): TranslationPage = withContext(Dispatchers.Default) {
        check(language in MangaOcrPacks.packs) { "Lingua OCR non supportata" }
        check(packs.isInstalled(language)) { "Scarica prima il modello OCR" }
        val api = TessBaseAPI()
        try {
            // init() expects the parent of tessdata; the pack returns this directory.
            val root = packs.directory()
            check(api.init(root.absolutePath, language)) { "Impossibile inizializzare il modello OCR" }
            api.setPageSegMode(
                if (language == "jpn_vert") {
                    TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
                } else {
                    TessBaseAPI.PageSegMode.PSM_AUTO
                },
            )
            api.setImage(image.bitmap)
            api.getUTF8Text()
            val lines = ArrayList<TranslationRegion>()
            val iterator = api.resultIterator
            if (iterator != null) {
                try {
                    val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                    do {
                        val text = iterator.getUTF8Text(level)?.trim().orEmpty()
                        val box = iterator.getBoundingBox(level)
                        val confidence = iterator.confidence(level)
                        if (text.isNotBlank() && confidence >= 25f && box.size == 4) {
                            val region = TranslationRegion(
                                left = box[0].toFloat() / image.bitmap.width,
                                top = box[1].toFloat() / image.bitmap.height,
                                right = box[2].toFloat() / image.bitmap.width,
                                bottom = box[3].toFloat() / image.bitmap.height,
                                original = text.take(2000),
                                confidence = confidence,
                            )
                            if (region.valid()) lines += region
                        }
                    } while (lines.size < 300 && iterator.next(level))
                } finally {
                    iterator.delete()
                }
            }
            TranslationPage(
                imageHash = image.imageHash,
                language = language,
                width = image.bitmap.width,
                height = image.bitmap.height,
                regions = lines,
            )
        } finally {
            api.recycle()
        }
    }
}

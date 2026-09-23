package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min

/** Both modes render a preview copy; the reader's image and downloads are never modified. */
class MangaTranslationRenderer {
    fun render(original: Bitmap, regions: List<TranslationRegion>, mode: TranslationViewMode): Bitmap {
        if (mode == TranslationViewMode.ORIGINAL || regions.none { it.translated.isNotBlank() }) return original
        val translated = regions.filter { it.valid() && it.translated.isNotBlank() }
        val output = if (mode == TranslationViewMode.RECONSTRUCTED) {
            reconstruct(original, translated)
        } else {
            original.copy(Bitmap.Config.ARGB_8888, true)
        }
        val canvas = Canvas(output)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xe8222222.toInt() }
        val foreground = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface =
                android.graphics.Typeface.DEFAULT_BOLD
        }
        for (region in translated) {
            val box = box(region, output.width, output.height, mode)
            if (mode == TranslationViewMode.OVERLAY) {
                canvas.drawRoundRect(box, 12f, 12f, background)
                foreground.color = Color.WHITE
            } else {
                foreground.color = Color.BLACK
            }
            drawFittedText(canvas, foreground, region.translated, box)
        }
        return output
    }

    private fun reconstruct(original: Bitmap, regions: List<TranslationRegion>): Bitmap {
        check(OpenCVLoader.initLocal()) { "Motore di ricostruzione non disponibile" }
        val source = Mat()
        val rgb = Mat()
        val mask = Mat.zeros(original.height, original.width, CvType.CV_8UC1)
        val result = Mat()
        val rgba = Mat()
        try {
            Utils.bitmapToMat(original, source)
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)
            for (region in regions) {
                val area = box(region, original.width, original.height, TranslationViewMode.RECONSTRUCTED)
                Imgproc.rectangle(
                    mask,
                    Point(area.left.toDouble(), area.top.toDouble()),
                    Point(area.right.toDouble(), area.bottom.toDouble()),
                    Scalar(255.0),
                    -1,
                )
            }
            Photo.inpaint(rgb, mask, result, 3.0, Photo.INPAINT_TELEA)
            Imgproc.cvtColor(result, rgba, Imgproc.COLOR_RGB2RGBA)
            return Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(rgba, it)
            }
        } finally {
            source.release()
            rgb.release()
            mask.release()
            result.release()
            rgba.release()
        }
    }

    private fun box(region: TranslationRegion, width: Int, height: Int, mode: TranslationViewMode): RectF {
        val pad = if (mode == TranslationViewMode.OVERLAY) 6f else 3f
        val left = (region.left * width - pad).coerceAtLeast(0f)
        val top = (region.top * height - pad).coerceAtLeast(0f)
        val right = (region.right * width + pad).coerceAtMost(width.toFloat())
        val bottom = (region.bottom * height + pad).coerceAtMost(height.toFloat())
        return RectF(left, top, right, bottom)
    }

    private fun drawFittedText(canvas: Canvas, paint: TextPaint, text: String, area: RectF) {
        val usableWidth = max(1, (area.width() - 12f).toInt())
        val usableHeight = max(1f, area.height() - 8f)
        var layout: StaticLayout
        var size = min(42f, max(14f, area.height() / 2f))
        do {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, usableWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
            if (layout.height <= usableHeight || size <= 10f) break
            size -= 1f
        } while (true)
        canvas.save()
        canvas.translate(area.left + (area.width() - usableWidth) / 2f, area.top + (area.height() - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()
    }
}

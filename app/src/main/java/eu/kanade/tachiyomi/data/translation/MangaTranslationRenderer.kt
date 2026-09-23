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
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min

/** Both modes render a preview copy; the reader's image and downloads are never modified. */
class MangaTranslationRenderer {
    private val overlayBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xf0222222.toInt() }
    private val overlayForeground = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    fun render(original: Bitmap, regions: List<TranslationRegion>, mode: TranslationViewMode): Bitmap {
        if (mode == TranslationViewMode.ORIGINAL || regions.none { it.translated.isNotBlank() }) return original
        val translated = regions.filter { it.valid() && it.translated.isNotBlank() }
        val output = if (mode == TranslationViewMode.RECONSTRUCTED) {
            reconstruct(original, translated)
        } else {
            original.copy(Bitmap.Config.ARGB_8888, true)
        }
        val canvas = Canvas(output)
        val foreground = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface =
                android.graphics.Typeface.DEFAULT_BOLD
        }
        for (region in translated) {
            val box = box(region, output.width, output.height, mode)
            if (mode == TranslationViewMode.OVERLAY) {
                drawOverlay(canvas, region, box)
            } else {
                foreground.color = Color.BLACK
                drawFittedText(canvas, foreground, region.translated, box)
            }
        }
        return output
    }

    fun drawOverlay(canvas: Canvas, region: TranslationRegion, box: RectF) {
        if (region.translated.isBlank() || box.width() < 12f || box.height() < 12f) return
        canvas.drawRoundRect(box, 8f, 8f, overlayBackground)
        drawFittedText(canvas, overlayForeground, region.translated, box)
    }

    private fun reconstruct(original: Bitmap, regions: List<TranslationRegion>): Bitmap {
        check(OpenCVLoader.initLocal()) { "Motore di ricostruzione non disponibile" }
        val source = Mat()
        val rgb = Mat()
        val gray = Mat()
        val mask = Mat.zeros(original.height, original.width, CvType.CV_8UC1)
        val result = Mat()
        val rgba = Mat()
        try {
            Utils.bitmapToMat(original, source)
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            for (region in regions) {
                val area = box(region, original.width, original.height, TranslationViewMode.RECONSTRUCTED)
                val bounds = Rect(
                    area.left.toInt(),
                    area.top.toInt(),
                    area.width().toInt().coerceAtLeast(1),
                    area.height().toInt().coerceAtLeast(1),
                )
                val pixels = gray.submat(bounds)
                val target = mask.submat(bounds)
                try {
                    val average = Core.mean(pixels).`val`[0]
                    val lightBackground = average >= 128.0
                    val threshold = if (lightBackground) average - 55.0 else average + 55.0
                    Imgproc.threshold(
                        pixels,
                        target,
                        threshold,
                        255.0,
                        if (lightBackground) Imgproc.THRESH_BINARY_INV else Imgproc.THRESH_BINARY,
                    )
                } finally {
                    pixels.release()
                    target.release()
                }
            }
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
            try {
                Imgproc.dilate(mask, mask, kernel)
            } finally {
                kernel.release()
            }
            Photo.inpaint(rgb, mask, result, 3.0, Photo.INPAINT_TELEA)
            Imgproc.cvtColor(result, rgba, Imgproc.COLOR_RGB2RGBA)
            return Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(rgba, it)
            }
        } finally {
            source.release()
            rgb.release()
            gray.release()
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

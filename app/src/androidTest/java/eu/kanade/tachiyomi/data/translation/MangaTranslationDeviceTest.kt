package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Real-device smoke test. Models are staged in target app files before running this test. */
@RunWith(AndroidJUnit4::class)
class MangaTranslationDeviceTest {
    @Test
    fun modelRangeEndpointResponds() {
        val connection = URL(
            "https://huggingface.co/casawolice/small100-onnx/resolve/" +
                "5c2c73ac70bee9c58f5a7ac5e84a36bee25db8ee/onnx/encoder_model.onnx",
        ).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Range", "bytes=0-1023")
            assertTrue(connection.responseCode == 200 || connection.responseCode == 206)
            assertTrue(connection.inputStream.use { it.read() >= 0 })
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun offlineTranslationOcrAndReconstruction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Existing FFmpeg runtime failed", ReturnCode.isSuccess(FFmpegKit.execute("-version").returnCode))
        val textPack = OfflineTranslationPack(context)
        val ocrPacks = MangaOcrPacks(context)
        assumeTrue("Stage the translation pack before this device test", textPack.ready())
        assumeTrue("Stage the English OCR pack before this device test", ocrPacks.isInstalled("eng"))

        OfflineTextTranslator(textPack).use { translator ->
            assertEquals("Ciao, come stai?", translator.translate("Hello, how are you?"))
            assertEquals("Ciao, stai bene?", translator.translate("こんにちは、元気ですか？"))
        }

        val bitmap = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawText(
            "Hello world",
            100f,
            250f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 90f
            },
        )
        val page = MangaPageBitmap(bitmap, "a".repeat(64))
        val recognized = MangaOcrEngine(ocrPacks).recognize(page, "eng")
        assertTrue("OCR returned no text", recognized.regions.any { "hello" in it.original.lowercase() })
        if (ocrPacks.isInstalled("jpn_vert")) {
            assertEquals("jpn_vert", MangaOcrEngine(ocrPacks).recognize(page, "jpn_vert").language)
        }

        val region = TranslationRegion(0.1f, 0.25f, 0.8f, 0.55f, "Hello world", "Ciao mondo")
        val reconstructed = MangaTranslationRenderer().render(bitmap, listOf(region), TranslationViewMode.RECONSTRUCTED)
        assertEquals(bitmap.width, reconstructed.width)
        assertEquals(bitmap.height, reconstructed.height)
    }
}

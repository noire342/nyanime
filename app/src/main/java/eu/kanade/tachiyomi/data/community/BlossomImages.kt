package eu.kanade.tachiyomi.data.community

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Re-encoding removes EXIF/GPS. A failed upload leaves the local draft untouched. */
internal object BlossomImages {
    val hosts = listOf("https://blossom.primal.net", "https://blossom.band")
    private val client = OkHttpClient.Builder().followRedirects(false).callTimeout(45, TimeUnit.SECONDS).build()
    suspend fun prepare(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >=
            28
        ) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val ratio = minOf(1.0, 1600.0 / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(
                    maxOf(1, (info.size.width * ratio).toInt()),
                    maxOf(1, (info.size.height * ratio).toInt()),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            require(options.outWidth > 0 && options.outHeight > 0)
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
            requireNotNull(
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                },
            )
        }
        try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                output.toByteArray().also { require(it.size <= 2_000_000) }
            }
        } finally {
            bitmap.recycle()
        }
    }
    suspend fun upload(
        identity: CommunityIdentity,
        bytes: ByteArray,
        host: String,
    ): String = withContext(Dispatchers.IO) {
        require(safeImage(host) && URI(host).path.orEmpty() in listOf("", "/")) { "Host immagini non valido" }
        require(bytes.size in 1..2_000_000)
        val hash = sha256(bytes).hex()
        val event = NostrEvent.create(
            identity,
            24242,
            "Pubblica un’immagine scelta per il profilo Nyanime",
            listOf(
                listOf("t", "upload"),
                listOf("x", hash),
                listOf("server", URI(host).host.lowercase()),
                listOf("expiration", (System.currentTimeMillis() / 1000 + 120).toString()),
            ),
        )
        val authorization = Base64.getUrlEncoder().withoutPadding().encodeToString(
            communityJson.encodeToString(event).toByteArray(),
        )
        val request = Request.Builder().url(
            host.trimEnd('/') + "/upload",
        ).header("Authorization", "Nostr $authorization")
            .header("X-SHA-256", hash).put(bytes.toRequestBody("image/jpeg".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            require(response.code !in listOf(401, 402, 403)) {
                "Questo host richiede accesso aggiuntivo o pagamento. Scegli un altro host: la bozza è conservata."
            }
            require(response.isSuccessful) { "Host immagini non disponibile. La bozza è conservata." }
            val body = response.body.byteStream().use { it.readBounded(16_384) }
            val descriptor = communityJson.parseToJsonElement(body.decodeToString()).jsonObject
            val url = descriptor["url"]?.jsonPrimitive?.content.orEmpty()
            require(descriptor["sha256"]?.jsonPrimitive?.content == hash && safeImage(url) && hash in URI(url).path) {
                "L’host ha restituito un’immagine non verificabile"
            }
            client.newCall(Request.Builder().url(url).build()).execute().use { downloaded ->
                require(downloaded.isSuccessful) { "L’immagine caricata non è ancora disponibile. Riprova tra poco." }
                val actual = downloaded.body.byteStream().use { it.readBounded(2_000_000) }
                require(sha256(actual).hex() == hash) { "Il contenuto dell’immagine non corrisponde al suo hash." }
            }
            url
        }
    }
}

internal fun java.io.InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
        if (read < 0) return output.toByteArray()
        output.write(buffer, 0, read)
        require(output.size() <= limit) { "Il file supera la dimensione consentita" }
    }
}

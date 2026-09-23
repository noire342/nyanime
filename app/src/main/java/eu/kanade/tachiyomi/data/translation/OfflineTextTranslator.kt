package eu.kanade.tachiyomi.data.translation

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.huggingface.tokenizers.jni.TokenizersLibrary
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.coroutines.coroutineContext

/** Local SMaLL-100 inference. A short OCR region is decoded greedily to bound time and memory.
 * Model: casawolice/small100-onnx (MIT), revision pinned in [OfflineTranslationPack].
 */
class OfflineTextTranslator(private val pack: OfflineTranslationPack) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val encoder: OrtSession
    private val decoder: OrtSession
    private val tokenizer: HuggingFaceTokenizer
    private val italianToken: Long

    init {
        check(pack.ready()) { "Scarica prima il pacchetto di traduzione" }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(2))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        var createdEncoder: OrtSession? = null
        var createdDecoder: OrtSession? = null
        var createdTokenizer: HuggingFaceTokenizer? = null
        try {
            createdEncoder = environment.createSession(pack.file("encoder_model.onnx").absolutePath, options)
            createdDecoder = environment.createSession(pack.file("decoder_model_merged.onnx").absolutePath, options)
            createdTokenizer =
                pack.file("tokenizer.json").inputStream().use { HuggingFaceTokenizer.newInstance(it, null) }
            val token = JSONObject(pack.file("lang_tokens.json").readText())
                .getJSONObject("lang_to_id").getLong("it")
            encoder = checkNotNull(createdEncoder)
            decoder = checkNotNull(createdDecoder)
            tokenizer = checkNotNull(createdTokenizer)
            italianToken = token
        } catch (error: Throwable) {
            createdTokenizer?.close()
            createdDecoder?.close()
            createdEncoder?.close()
            throw error
        } finally {
            options.close()
        }
    }

    suspend fun translate(source: String): String = withContext(Dispatchers.Default) {
        if (source.isBlank()) return@withContext ""
        require(source.length <= 2000)
        val encoded = encodeIds(source)
        check(encoded.size <= 256) { "Testo troppo lungo per il modello" }
        val input = LongArray(encoded.size + 1)
        input[0] = italianToken
        encoded.copyInto(input, 1)
        val attention = LongArray(input.size) { 1L }
        longTensor(input).use { inputIds ->
            longTensor(attention).use { attentionMask ->
                encoder.run(mapOf("input_ids" to inputIds, "attention_mask" to attentionMask)).use { encodedOutput ->
                    val hidden = encodedOutput[0] as OnnxTensor
                    val generated = ArrayList<Long>(64)
                    var past = emptyPast()
                    var currentToken = 2L
                    var useCache = false
                    try {
                        repeat(128) {
                            coroutineContext.ensureActive()
                            longTensor(longArrayOf(currentToken)).use { decoderIds ->
                                val cacheFlag = ByteBuffer.allocateDirect(1).put(if (useCache) 1 else 0)
                                    .apply { rewind() }
                                OnnxTensor.createTensor(
                                    environment,
                                    cacheFlag,
                                    longArrayOf(1),
                                    OnnxJavaType.BOOL,
                                ).use { branch ->
                                    val inputs = HashMap<String, OnnxTensor>(past.size + 4)
                                    inputs.putAll(past)
                                    inputs["input_ids"] = decoderIds
                                    inputs["encoder_hidden_states"] = hidden
                                    inputs["encoder_attention_mask"] = attentionMask
                                    inputs["use_cache_branch"] = branch
                                    decoder.run(inputs).use { result ->
                                        val next = bestToken(result[0] as OnnxTensor)
                                        if (next == 2L) {
                                            return@withContext tokenizer.decode(generated.toLongArray(), true).trim()
                                        }
                                        val updated = rollPast(result, past, useCache)
                                        past.forEach { (key, value) ->
                                            if (updated[key] !== value) value.close()
                                        }
                                        past = updated
                                        currentToken = next
                                        generated += next
                                        useCache = true
                                    }
                                }
                            }
                        }
                    } finally {
                        past.values.forEach(OnnxTensor::close)
                    }
                    tokenizer.decode(generated.toLongArray(), true).trim()
                }
            }
        }
    }

    private fun encodeIds(source: String): LongArray {
        // HuggingFaceTokenizer.encode() also requests character spans, which aborts inside the
        // Android tokenizer JNI for some OCR text. Inference only needs token IDs.
        val native = TokenizersLibrary.LIB
        val encoding = native.encode(tokenizer.handle, source, true)
        try {
            return native.getTokenIds(encoding)
        } finally {
            native.deleteEncoding(encoding)
        }
    }

    private fun longTensor(values: LongArray): OnnxTensor =
        OnnxTensor.createTensor(environment, LongBuffer.wrap(values), longArrayOf(1, values.size.toLong()))

    private fun emptyPast(): Map<String, OnnxTensor> = buildMap {
        repeat(3) { layer ->
            for (kind in listOf("decoder", "encoder")) {
                for (part in listOf("key", "value")) {
                    put(
                        "past_key_values.$layer.$kind.$part",
                        OnnxTensor.createTensor(environment, FloatBuffer.allocate(0), longArrayOf(1, 16, 0, 64)),
                    )
                }
            }
        }
    }

    private fun rollPast(
        result: OrtSession.Result,
        previous: Map<String, OnnxTensor>,
        wasCached: Boolean,
    ): Map<String, OnnxTensor> = buildMap {
        repeat(3) { layer ->
            for (kind in listOf("decoder", "encoder")) {
                for (part in listOf("key", "value")) {
                    val key = "past_key_values.$layer.$kind.$part"
                    if (kind == "encoder" && wasCached) {
                        put(key, previous.getValue(key))
                    } else {
                        val value = result.get("present.$layer.$kind.$part").orElseThrow() as OnnxTensor
                        put(key, copyTensor(value))
                    }
                }
            }
        }
    }

    private fun copyTensor(source: OnnxTensor): OnnxTensor {
        val values = source.floatBuffer
        val copy = FloatBuffer.allocate(values.remaining())
        copy.put(values)
        copy.flip()
        return OnnxTensor.createTensor(environment, copy, source.info.shape)
    }

    private fun bestToken(logits: OnnxTensor): Long {
        val shape = logits.info.shape
        val vocabulary = shape[2].toInt()
        val start = (shape[1].toInt() - 1) * vocabulary
        val values = logits.floatBuffer
        var best = 0
        var score = Float.NEGATIVE_INFINITY
        for (id in 0 until vocabulary) {
            val value = values.get(start + id)
            if (value > score) {
                score = value
                best = id
            }
        }
        return best.toLong()
    }

    override fun close() {
        tokenizer.close()
        decoder.close()
        encoder.close()
    }
}

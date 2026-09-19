package eu.kanade.tachiyomi.data.download.anime.ultra

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Executes the original Maximum/A+ HQ shader graph using signed half-float intermediate textures. */
@UnstableApi
internal class UltraGlEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("Ultra richiede un video SDR; l'originale è stato conservato")
        val shaders = UltraShaderGraph.assets.map {
            context.assets.open("anime4k/$it").bufferedReader().use { it.readText() }
        }
        return Program(shaders)
    }

    private class Program(private val shaders: List<String>) : BaseGlShaderProgram(false, 1) {
        private data class Texture(val id: Int, val fbo: Int, val width: Int, val height: Int)
        private val programs = mutableListOf<GlProgram>()
        private val textures = mutableListOf<Texture>()
        private val slots = mutableMapOf<Int, Int>()
        private lateinit var graph: UltraShaderGraph.Graph

        override fun configure(inputWidth: Int, inputHeight: Int): Size = guarded {
            clear()
            graph = UltraShaderGraph.compile(shaders, inputWidth, inputHeight)
            val maxTexture = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
            val lastUse = mutableMapOf<Int, Int>()
            graph.nodes.forEachIndexed { index, node -> node.inputs.values.forEach { lastUse[it.id] = index } }
            val occupiedUntil = mutableListOf<Int>()
            var allocatedBytes = 0L
            for ((index, node) in graph.nodes.withIndex()) {
                val program = GlProgram(VERTEX, node.fragment)
                programs += program
                program.setBufferAttribute("aPosition", floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f), 2)
                // The final node renders directly into Media3's output framebuffer.
                if (index == graph.nodes.lastIndex) continue
                val image = node.output
                require(maxOf(image.width, image.height) <= maxTexture[0]) {
                    "Risoluzione Ultra non supportata dalla GPU"
                }
                var slot = textures.indices.firstOrNull {
                    occupiedUntil[it] < index &&
                        textures[it].width == image.width &&
                        textures[it].height == image.height
                }
                if (slot == null) {
                    allocatedBytes += image.width.toLong() * image.height * 8
                    require(allocatedBytes <= 896L * 1024 * 1024) { "Memoria GPU insufficiente per Ultra" }
                    val tex = GlUtil.createTexture(image.width, image.height, true)
                    val fbo = try {
                        GlUtil.createFboForTexture(tex)
                    } catch (
                        e: Exception,
                    ) {
                        GlUtil.deleteTexture(tex)
                        throw e
                    }
                    textures += Texture(tex, fbo, image.width, image.height)
                    occupiedUntil += -1
                    slot = textures.lastIndex
                }
                slots[image.id] = slot
                occupiedUntil[slot] = lastUse[image.id] ?: index
            }
            Size(graph.output.width, graph.output.height)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) = guarded {
            val target = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, target, 0)
            try {
                graph.nodes.forEachIndexed { index, node ->
                    val framebuffer = if (index ==
                        graph.nodes.lastIndex
                    ) {
                        target[0]
                    } else {
                        textures[slots.getValue(node.output.id)].fbo
                    }
                    GlUtil.focusFramebufferUsingCurrentContext(framebuffer, node.output.width, node.output.height)
                    val program = programs[index]
                    program.use()
                    node.inputs.values.forEachIndexed { unit, image ->
                        val tex = if (image.id == 0) inputTexId else textures[slots.getValue(image.id)].id
                        program.setSamplerTexIdUniform("uTexture$unit", tex, unit)
                    }
                    program.bindAttributesAndUniforms()
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    GlUtil.checkGlError()
                }
            } finally {
                GlUtil.focusFramebufferUsingCurrentContext(target[0], graph.output.width, graph.output.height)
            }
        }

        override fun release() {
            try {
                clear()
            } finally {
                super.release()
            }
        }

        private fun clear() {
            programs.forEach { runCatching { it.delete() } }
            programs.clear()
            textures.forEach {
                runCatching {
                    GlUtil.deleteFbo(it.fbo)
                    GlUtil.deleteTexture(it.id)
                }
            }
            textures.clear()
            slots.clear()
        }

        private inline fun <T> guarded(block: () -> T): T = try {
            block()
        } catch (e: Exception) {
            throw VideoFrameProcessingException.from(e)
        }

        companion object {
            private val VERTEX = """
                attribute vec2 aPosition;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = vec4(aPosition, 0.0, 1.0);
                    vTexCoord = (aPosition + 1.0) * 0.5;
                }
            """.trimIndent()
        }
    }
}

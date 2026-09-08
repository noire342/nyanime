package eu.kanade.tachiyomi.ui.player

import android.content.Context
import com.hippo.unifile.UniFile
import `is`.xyz.mpv.MPVLib
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.IOException

/**
 * Boundary around filesystem and MPV side effects. The governor and preset rules depend only
 * on the domain API, while this adapter owns shader installation and list replacement.
 */
class Anime4KShaderPipeline(
    private val context: Context,
    private val shaderListReader: () -> String? = {
        MPVLib.getPropertyString("glsl-shaders")
    },
    private val commandSender: (Array<String>) -> Unit = { command ->
        MPVLib.command(command)
    },
) {
    companion object {
        private const val ASSET_DIRECTORY = "anime4k"
        private const val MPV_DIRECTORY = "mpv"
        private const val MPV_SHADERS_DIRECTORY = "anime4k-shaders"
        private const val REVISION_FILE = ".revision"
    }

    private val bundledShaderNamesLowercase = Anime4K.bundledShaderNames
        .map(String::lowercase)
        .toSet()

    /** Copies the curated set outside MPV's replaceable user-shader directory. */
    fun copyAssets(mpvDir: UniFile) {
        val mpvPath = mpvDir.filePath ?: return
        val shadersDir = File(mpvPath, MPV_SHADERS_DIRECTORY)
        if (!shadersDir.exists() && !shadersDir.mkdirs()) return

        val revisionFile = File(shadersDir, REVISION_FILE)
        val complete = Anime4K.bundledShaderNames.all { File(shadersDir, it).isFile }
        if (complete && revisionFile.readTextOrNull() == Anime4K.SHADER_REVISION) return

        val copied = Anime4K.bundledShaderNames.map { filename ->
            copyAsset(
                assetPath = "$ASSET_DIRECTORY/$filename",
                outputFile = File(shadersDir, filename),
            )
        }.all { it }

        if (copied && Anime4K.bundledShaderNames.all { File(shadersDir, it).isFile }) {
            runCatching { revisionFile.writeText(Anime4K.SHADER_REVISION) }
                .onFailure { error ->
                    logcat(LogPriority.ERROR, error) { "Failed to write Anime4K revision marker" }
                }
        }
    }

    /**
     * Replaces only Anime4K entries in MPV's shader list. User-configured shaders are preserved.
     */
    @Synchronized
    fun apply(mode: Anime4KMode): Boolean {
        val shaderDir = shaderDirectory()
        if (mode != Anime4KMode.Off && mode.shaderFileNames.any { !File(shaderDir, it).isFile }) {
            logcat(LogPriority.ERROR) {
                "Anime4K ${Anime4K.VERSION} assets are incomplete; keeping the current shader pipeline"
            }
            return false
        }

        return try {
            val existingShaders = shaderListReader()
                ?.split(File.pathSeparatorChar)
                .orEmpty()
                .filter(String::isNotBlank)
                .filterNot(::isBundledAnime4KPath)
            val targetShaders = (
                existingShaders + mode.shaderFileNames.map { File(shaderDir, it).absolutePath }
                ).distinct()
            val command = if (targetShaders.isEmpty()) {
                arrayOf("change-list", "glsl-shaders", "clr", "")
            } else {
                arrayOf(
                    "change-list",
                    "glsl-shaders",
                    "set",
                    targetShaders.joinToString(File.pathSeparator),
                )
            }
            commandSender(command)
            true
        } catch (error: RuntimeException) {
            logcat(LogPriority.ERROR, error) { "Failed to apply Anime4K mode ${mode.name}" }
            false
        }
    }

    private fun shaderDirectory(): File = File(
        context.filesDir,
        "$MPV_DIRECTORY/$MPV_SHADERS_DIRECTORY",
    )

    private fun isBundledAnime4KPath(path: String): Boolean {
        return File(path).name.lowercase() in bundledShaderNamesLowercase
    }

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    private fun copyAsset(
        assetPath: String,
        outputFile: File,
    ): Boolean {
        return try {
            val temporaryFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
            context.assets.open(assetPath).use { input ->
                temporaryFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporaryFile.renameTo(outputFile)) {
                temporaryFile.copyTo(outputFile, overwrite = true)
                temporaryFile.delete()
            }
            true
        } catch (error: IOException) {
            logcat(LogPriority.ERROR, error) { "Failed to copy Anime4K asset: $assetPath" }
            false
        }
    }
}

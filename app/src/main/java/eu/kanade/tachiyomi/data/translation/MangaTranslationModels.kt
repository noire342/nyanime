package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.Serializable

/** Coordinates refer to the decoded page, never to a source URL or reader viewport. */
@Serializable
data class TranslationRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val original: String,
    val translated: String = "",
    val confidence: Float = 0f,
) {
    fun valid() = left >= 0f &&
        top >= 0f &&
        right <= 1f &&
        bottom <= 1f &&
        right > left &&
        bottom > top &&
        original.isNotBlank() &&
        original.length <= 2000
}

@Serializable
data class TranslationPage(
    val version: Int = 2,
    val imageHash: String,
    val language: String,
    val width: Int,
    val height: Int,
    val regions: List<TranslationRegion>,
) {
    fun valid() = version == 2 &&
        imageHash.length == 64 &&
        language == "eng" &&
        width in 1..8192 &&
        height in 1..8192 &&
        regions.size <= 300 &&
        regions.all(TranslationRegion::valid)
}

enum class TranslationViewMode { OVERLAY, RECONSTRUCTED, ORIGINAL }

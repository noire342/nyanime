package eu.kanade.tachiyomi.data.translation

import kotlin.math.max
import kotlin.math.min

/** Join adjacent OCR lines before translating; a speech bubble needs sentence-level context. */
fun groupTranslationLines(lines: List<TranslationRegion>, vertical: Boolean): List<TranslationRegion> {
    val remaining = lines.filter(TranslationRegion::valid).toMutableList()
    val grouped = mutableListOf<TranslationRegion>()
    while (remaining.isNotEmpty()) {
        val cluster = mutableListOf(remaining.removeAt(0))
        var changed: Boolean
        do {
            changed = false
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (cluster.any { adjacent(it, candidate, vertical) } &&
                    (vertical || sameSpeechBubble(cluster, candidate))
                ) {
                    cluster += candidate
                    iterator.remove()
                    changed = true
                }
            }
        } while (changed)
        val ordered = if (vertical) {
            cluster.sortedWith(compareByDescending<TranslationRegion> { it.left }.thenBy { it.top })
        } else {
            cluster.sortedWith(compareBy<TranslationRegion> { it.top }.thenBy { it.left })
        }
        grouped += TranslationRegion(
            left = cluster.minOf { it.left },
            top = cluster.minOf { it.top },
            right = cluster.maxOf { it.right },
            bottom = cluster.maxOf { it.bottom },
            original = ordered.joinToString(if (vertical) "" else " ") { it.original.trim() }.take(2000),
            confidence = cluster.map { it.confidence }.average().toFloat(),
        )
    }
    return grouped
}

private fun sameSpeechBubble(cluster: List<TranslationRegion>, candidate: TranslationRegion): Boolean {
    val left = min(cluster.minOf { it.left }, candidate.left)
    val right = max(cluster.maxOf { it.right }, candidate.right)
    val top = min(cluster.minOf { it.top }, candidate.top)
    val bottom = max(cluster.maxOf { it.bottom }, candidate.bottom)
    if (right - left > 0.48f || bottom - top > 0.16f) return false
    val nearest = cluster.minByOrNull { kotlin.math.abs((it.left + it.right) - (candidate.left + candidate.right)) }
        ?: return false
    val centerGap = kotlin.math.abs((nearest.left + nearest.right) - (candidate.left + candidate.right)) / 2f
    return centerGap <= max(nearest.right - nearest.left, candidate.right - candidate.left) * 0.32f
}

private fun adjacent(a: TranslationRegion, b: TranslationRegion, vertical: Boolean): Boolean {
    val xOverlap = max(0f, min(a.right, b.right) - max(a.left, b.left))
    val yOverlap = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
    val xGap = max(0f, max(a.left, b.left) - min(a.right, b.right))
    val yGap = max(0f, max(a.top, b.top) - min(a.bottom, b.bottom))
    return if (vertical) {
        yOverlap >= min(a.bottom - a.top, b.bottom - b.top) * 0.55f &&
            xGap <= max(a.right - a.left, b.right - b.left) * 0.7f
    } else {
        xOverlap >= min(a.right - a.left, b.right - b.left) * 0.7f &&
            yGap <= min(a.bottom - a.top, b.bottom - b.top) * 0.6f
    }
}

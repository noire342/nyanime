package tachiyomi.domain.search

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

@Serializable
enum class SearchMedium { VIDEO, MANGA }

/** Data received at runtime. A catalog suggestion is never a source identity. */
@Serializable
data class SearchTitle(
    val key: String,
    val title: String,
    val medium: SearchMedium,
    val aliases: List<String> = emptyList(),
    val origin: String = "extension",
    val sourceId: Long? = null,
) {
    val names get() = (listOf(title) + aliases).filter { it.length in 1..256 }.distinct().take(16)
}

data class TitleMatch(val item: SearchTitle, val score: Int)

interface TitleMatcher {
    fun score(query: String, title: String): Int
    fun rank(query: String, candidates: Collection<SearchTitle>): List<TitleMatch>
}

object TitleNormalizer {
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val spaces = Regex("\\s+")
    private val numbers = Regex("\\d+")

    fun words(value: String): String = Normalizer.normalize(value.take(256), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(punctuation, " ").trim().replace(spaces, " ")

    fun compact(value: String) = words(value).replace(" ", "")

    fun numericParts(value: String) = numbers.findAll(words(value)).map { it.value }.toList()

    fun preservesNumbers(query: List<String>, candidate: List<String>) =
        query.isEmpty() || (candidate.size >= query.size && query.indices.all { query[it] == candidate[it] })

    /** Accent folding is secondary and Latin-only: Japanese voicing marks retain their meaning. */
    fun folded(value: String): String = words(value).map { character ->
        if (Character.UnicodeScript.of(character.code) == Character.UnicodeScript.LATIN) {
            Normalizer.normalize(character.toString(), Normalizer.Form.NFD).first()
        } else {
            character
        }
    }.joinToString("")
}

class LexicalTitleMatcher : TitleMatcher {
    private data class NormalizedTitle(
        val words: String,
        val compact: String,
        val numbers: List<String>,
        val tokens: List<String>,
        val folded: String,
    )
    private data class DistanceKey(val first: String, val second: String, val limit: Int)

    // Bounded, memory-only normalization. It never writes titles or user queries to disk.
    private val normalized = object : LinkedHashMap<String, NormalizedTitle>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NormalizedTitle>) = size > 4_096
    }

    private fun prepare(value: String): NormalizedTitle = synchronized(normalized) {
        normalized.getOrPut(value) {
            val words = TitleNormalizer.words(value)
            NormalizedTitle(
                words,
                words.replace(" ", ""),
                TitleNormalizer.numericParts(words),
                words.split(' '),
                TitleNormalizer.folded(words),
            )
        }
    }

    override fun score(query: String, title: String): Int = score(prepare(query), prepare(title), null)

    private fun score(
        wanted: NormalizedTitle,
        actual: NormalizedTitle,
        distances: MutableMap<DistanceKey, Int>?,
    ): Int {
        fun distance(first: String, second: String, limit: Int): Int {
            if (first == second) return 0
            if (kotlin.math.abs(first.length - second.length) > limit) return limit + 1
            if (distances == null) return editDistance(first, second, limit)
            return distances.getOrPut(DistanceKey(first, second, limit)) { editDistance(first, second, limit) }
        }
        if (wanted.words.isBlank() || actual.words.isBlank()) return 0
        if (!TitleNormalizer.preservesNumbers(wanted.numbers, actual.numbers)) return 0
        val editionPenalty = if (wanted.numbers != actual.numbers) 12 else 0
        if (wanted.words == actual.words) return 100
        val a = wanted.compact
        val b = actual.compact
        if (a == b) return 99
        if (wanted.folded == actual.folded) return 98
        if (actual.words.startsWith(wanted.words) || (a.length >= 3 && b.startsWith(a))) return 94 - editionPenalty
        if (wanted.tokens.all { word -> actual.tokens.any { it == word } }) return 93 - editionPenalty
        if (wanted.words.length < 4) return 0
        val limit = if (a.length < 8) 1 else 2
        val compactDistance = distance(a, b, limit)
        if (compactDistance <= limit) return (96 - compactDistance * 7 - editionPenalty).coerceAtLeast(0)
        // Every query word must belong to this complete candidate, including partial searches.
        var totalCost = 0
        for (word in wanted.tokens) {
            val maximum = if (word.length < 4) {
                0
            } else if (word.length < 8) {
                1
            } else {
                2
            }
            var best = maximum + 1
            for (available in actual.tokens) {
                best = minOf(best, distance(word, available, maximum))
                if (best == 0) break
            }
            if (best > if (word.length < 4) 0 else 1) return 0
            totalCost += best
            if (totalCost > 2) return 0
        }
        return 92 - totalCost * 5 - editionPenalty
    }

    override fun rank(query: String, candidates: Collection<SearchTitle>): List<TitleMatch> {
        val wanted = prepare(query)
        val distances = mutableMapOf<DistanceKey, Int>()
        return candidates.distinctBy { it.key }
            .map { item ->
                TitleMatch(item, item.names.maxOfOrNull { score(wanted, prepare(it), distances) } ?: 0)
            }
            .filter { it.score >= 75 }
            .sortedWith(compareByDescending<TitleMatch> { it.score }.thenBy { it.item.title })
    }

    companion object {
        /** Bounded optimal-string-alignment distance, including adjacent letter transpositions. */
        fun editDistance(a: String, b: String, limit: Int): Int {
            if (a == b) return 0
            if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
            var before = IntArray(b.length + 1) { it }
            var previous = before.copyOf()
            for (i in a.indices) {
                val current = IntArray(b.length + 1) { limit + 1 }
                current[0] = i + 1
                val start = (i - limit).coerceAtLeast(0)
                val end = (i + limit).coerceAtMost(b.lastIndex)
                if (start <= end) {
                    for (j in start..end) {
                        current[j + 1] =
                            minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
                        if (i > 0 && j > 0 && a[i] == b[j - 1] && a[i - 1] == b[j]) {
                            current[j + 1] = minOf(current[j + 1], before[j - 1] + 1)
                        }
                    }
                }
                before = previous
                previous = current
            }
            return previous[b.length].coerceAtMost(limit + 1)
        }
    }
}

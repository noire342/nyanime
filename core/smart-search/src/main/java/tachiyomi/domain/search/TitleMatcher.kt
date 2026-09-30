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
    override fun score(query: String, title: String): Int {
        val wanted = TitleNormalizer.words(query)
        val actual = TitleNormalizer.words(title)
        if (wanted.isBlank() || actual.isBlank()) return 0
        val wantedNumbers = TitleNormalizer.numericParts(wanted)
        val actualNumbers = TitleNormalizer.numericParts(actual)
        if (wantedNumbers.isNotEmpty() && wantedNumbers != actualNumbers) return 0
        val editionPenalty = if (wantedNumbers.isEmpty() && actualNumbers.isNotEmpty()) 12 else 0
        if (wanted == actual) return 100
        val a = wanted.replace(" ", "")
        val b = actual.replace(" ", "")
        if (a == b) return 99
        if (TitleNormalizer.folded(wanted) == TitleNormalizer.folded(actual)) return 98
        if (actual.startsWith(wanted) || (a.length >= 3 && b.startsWith(a))) return 94 - editionPenalty
        val expected = wanted.split(' ')
        val available = actual.split(' ')
        if (expected.all { word -> available.any { it == word } }) return 93 - editionPenalty
        if (wanted.length < 4) return 0
        val limit = if (a.length < 8) 1 else 2
        val distance = editDistance(a, b, limit)
        if (distance <= limit) return (96 - distance * 7 - editionPenalty).coerceAtLeast(0)
        // Partial queries also tolerate mistakes, but every word must be grounded in this title.
        if (expected.size > 1) {
            val costs = expected.map { word ->
                val maximum = if (word.length < 4) {
                    0
                } else if (word.length < 8) {
                    1
                } else {
                    2
                }
                available.minOfOrNull { editDistance(word, it, maximum) } ?: maximum + 1
            }
            if (expected.indices.all { costs[it] <= if (expected[it].length < 4) 0 else 1 } && costs.sum() <= 2) {
                return 90 - costs.sum() * 5 - editionPenalty
            }
        }
        return 0
    }

    override fun rank(query: String, candidates: Collection<SearchTitle>): List<TitleMatch> = candidates
        .distinctBy { it.key }
        .map { item -> TitleMatch(item, item.names.maxOfOrNull { score(query, it) } ?: 0) }
        .filter { it.score >= 75 }
        .sortedWith(compareByDescending<TitleMatch> { it.score }.thenBy { it.item.title })

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

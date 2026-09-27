package tachiyomi.data.discovery

import tachiyomi.domain.discovery.SourceHomeChoice
import tachiyomi.domain.discovery.SourceHomePresentation
import tachiyomi.domain.discovery.homeItemKey
import tachiyomi.domain.discovery.homePresentation
import tachiyomi.domain.entries.anime.model.Anime
import java.text.Normalizer
import java.util.Locale

/** Merge public cards, while keeping every concrete source choice available to open. */
internal fun mergeHomeCards(cards: List<Anime>): List<Anime> {
    val groups = mutableListOf<MutableList<Anime>>()
    cards.distinctBy { it.homeItemKey }.forEach { card ->
        val group = groups.firstOrNull { members ->
            members.none { it.source == card.source || catalogueConflict(it, card) } &&
                members.any { sameWork(it, card) }
        }
        if (group == null) groups.add(mutableListOf(card)) else group.add(card)
    }
    return groups.map { members ->
        val first = members.first()
        if (members.size == 1) return@map first
        val choices = members.map { SourceHomeChoice(it.id, it.source, it.title, it.homePresentation?.episodeTarget) }
        val presentation = (first.homePresentation ?: SourceHomePresentation()).copy(choices = choices)
        first.copy(memo = presentation.attachTo(first.memo))
    }
}

private fun sameWork(left: Anime, right: Anime): Boolean {
    if (left.source == right.source) return false
    if (catalogueConflict(left, right)) return false
    val a = left.homePresentation
    val b = right.homePresentation
    val idsA = a?.catalogIds.orEmpty()
    val idsB = b?.catalogIds.orEmpty()
    val sharedKinds = idsA.keys intersect idsB.keys
    if (sharedKinds.any { idsA[it] == idsB[it] }) return true
    if (a?.releaseYear != null && b?.releaseYear != null && a.releaseYear != b.releaseYear) return false
    // Without catalogue IDs, require another independent signal; identical titles alone can name different works.
    if (a?.releaseYear == null || b?.releaseYear == null) return false
    val titlesA = (listOf(left.title) + a?.aliases.orEmpty()).map(::normalTitle).filter(String::isNotEmpty).toSet()
    val titlesB = (listOf(right.title) + b?.aliases.orEmpty()).map(::normalTitle).filter(String::isNotEmpty).toSet()
    return titlesA.any { it in titlesB }
}

private fun catalogueConflict(left: Anime, right: Anime): Boolean {
    val leftIds = left.homePresentation?.catalogIds.orEmpty()
    val rightIds = right.homePresentation?.catalogIds.orEmpty()
    return (leftIds.keys intersect rightIds.keys).any { leftIds[it] != rightIds[it] }
}

private fun normalTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("(?:\\s*[(\\[]?(?:sub[ -]?ita|dub[ -]?ita|ita)[)\\]]?)+$"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")

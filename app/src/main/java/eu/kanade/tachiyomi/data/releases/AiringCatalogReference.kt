package eu.kanade.tachiyomi.data.releases

/** Saved tracker bindings take precedence over unverified extension hints. */
internal data class AiringCatalogReference(val anilistId: Long?, val malId: Long?, val expectedMalId: Long? = malId) {
    companion object {
        fun choose(
            hintAnilist: Long?,
            hintMal: Long?,
            trackedAnilist: Long?,
            trackedMal: Long?,
        ): AiringCatalogReference {
            val anilist = trackedAnilist?.takeIf { it > 0 }
            val mal = trackedMal?.takeIf { it > 0 }
            return when {
                anilist != null -> AiringCatalogReference(anilist, mal ?: hintMal, expectedMalId = mal)
                mal != null -> AiringCatalogReference(null, mal)
                else -> AiringCatalogReference(hintAnilist, hintMal)
            }
        }
    }
}

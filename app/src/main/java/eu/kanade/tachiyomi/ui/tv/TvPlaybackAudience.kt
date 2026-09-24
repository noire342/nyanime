package eu.kanade.tachiyomi.ui.tv

import android.content.Intent

/** Empty ids means Anonymous: a TV playback session without progress or tracking writes. */
data class TvPlaybackAudience(val profileIds: List<String>) {
    val isAnonymous: Boolean get() = profileIds.isEmpty()
    val writesMain: Boolean get() = TvProfile.MAIN_ID in profileIds
    val secondaryIds: List<String> get() = profileIds.filter { it != TvProfile.MAIN_ID }

    fun writeTo(intent: Intent) {
        intent.putExtra(EXTRA_TV_PLAYBACK, true)
        intent.putStringArrayListExtra(EXTRA_TV_PROFILES, ArrayList(profileIds.distinct()))
    }

    companion object {
        private const val EXTRA_TV_PLAYBACK = "nyanime.tv.playback"
        private const val EXTRA_TV_PROFILES = "nyanime.tv.profiles"

        fun from(intent: Intent?): TvPlaybackAudience? {
            if (intent?.getBooleanExtra(EXTRA_TV_PLAYBACK, false) != true) return null
            return TvPlaybackAudience(intent.getStringArrayListExtra(EXTRA_TV_PROFILES).orEmpty().distinct().take(2))
        }
    }
}

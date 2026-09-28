package eu.kanade.presentation.privacy

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import eu.kanade.tachiyomi.ui.privacy.PrivacyArea
import eu.kanade.tachiyomi.ui.privacy.PrivacyContentIndex
import eu.kanade.tachiyomi.ui.privacy.PrivacyDisplayRuntime
import eu.kanade.tachiyomi.ui.privacy.PrivacyMedia
import nyanime.privacy.display.PrivacyDisplayCapability
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Modifier.nsfwPrivacy(data: Any?, labels: List<String>? = null): Modifier = contentPrivacy { index, ratings ->
    index.isNsfw(data, ratings) || eu.kanade.tachiyomi.ui.privacy.NsfwContentPolicy.isNsfw(false, labels)
}

fun Modifier.nsfwSourcePrivacy(media: PrivacyMedia, sourceIds: Set<Long>): Modifier = contentPrivacy { _, ratings ->
    sourceIds.any { it in if (media == PrivacyMedia.VIDEO) ratings.video else ratings.manga }
}

private fun Modifier.contentPrivacy(
    classified: (PrivacyContentIndex, eu.kanade.tachiyomi.ui.privacy.PrivacySourceRatings) -> Boolean,
): Modifier = composed {
    val view = LocalView.current
    val controller = remember(view) { view.context.baseActivity()?.privacyDisplayController }
    if (controller == null ||
        PrivacyDisplayRuntime.capability(PrivacyArea.NSFW) != PrivacyDisplayCapability.Available
    ) {
        return@composed this
    }
    val enabled by controller.nsfwEnabled.collectAsState()
    if (!enabled) return@composed this
    val index = remember { Injekt.get<PrivacyContentIndex>() }
    val ratings by index.ratings.collectAsState()
    this.privacyRegion(PrivacyArea.NSFW, enabled = classified(index, ratings))
}

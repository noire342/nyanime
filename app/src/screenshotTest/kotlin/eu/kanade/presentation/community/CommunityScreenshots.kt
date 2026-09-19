package eu.kanade.presentation.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.discovery.PreviewImages
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.data.community.CommunityInteractions
import eu.kanade.tachiyomi.data.community.CommunityItem
import eu.kanade.tachiyomi.data.community.CommunityProfile
import eu.kanade.tachiyomi.data.community.CommunityState
import eu.kanade.tachiyomi.data.community.PublicTitle
import eu.kanade.tachiyomi.data.community.ShelfStatus
import eu.kanade.tachiyomi.ui.community.NyanimeSticker
import eu.kanade.tachiyomi.ui.community.ProfilePage

private object PreviewActions : CommunityInteractions {
    override fun requestFriend(peer: String) = Unit
    override fun react(item: CommunityItem) = Unit
    override fun moderate(item: CommunityItem, pin: Boolean) = Unit
    override fun removeFriend(peer: String, block: Boolean) = Unit
}

@PreviewTest
@Preview(name = "CommunityProfile", widthDp = 393, heightDp = 852, locale = "it")
@Preview(name = "CommunityProfileLargeText", widthDp = 320, heightDp = 852, fontScale = 1.5f, locale = "it")
@Preview(name = "CommunityProfileTablet", widthDp = 800, heightDp = 1000, locale = "it")
@Composable
fun CommunityProfileScreenshot() {
    PreviewImages()
    val titles = listOf("Le stelle di domani", "L’ultimo viaggio", "Oltre la luna").mapIndexed { index, title ->
        PublicTitle(index.toString(), title, artwork = "preview://$index", status = ShelfStatus.Watching)
    }
    val profile = CommunityProfile(
        key = "a".repeat(64),
        name = "Viaggiatrice tra le stelle",
        bio = "Colleziono storie che restano. Un episodio, un capitolo e un posto sul divano per gli amici.",
        favorites = titles,
        shelves = titles,
        accent = 0xFFAA7DEB,
    )
    TachiyomiPreviewTheme(appTheme = AppTheme.NYANIME) {
        Surface(Modifier.fillMaxSize()) {
            ProfilePage(
                CommunityState(me = profile, profiles = mapOf(profile.key to profile)),
                profile,
                PreviewActions,
                {}, {}, {}, {}, {}, {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "OriginalStickers", widthDp = 393, heightDp = 110)
@Composable
fun CommunityStickersScreenshot() {
    TachiyomiPreviewTheme(appTheme = AppTheme.NYANIME) {
        Surface {
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("cat", "heart", "star", "popcorn").forEach { NyanimeSticker(it) }
            }
        }
    }
}

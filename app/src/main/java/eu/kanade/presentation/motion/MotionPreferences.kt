package eu.kanade.presentation.motion

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import eu.kanade.presentation.theme.LocalNyanimeStyle
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun modernMotionEnabled(): Boolean {
    if (!LocalNyanimeStyle.current) return false
    return LocalInspectionMode.current || !Injekt.get<PlayerPreferences>().reduceMotion().collectAsState().value
}

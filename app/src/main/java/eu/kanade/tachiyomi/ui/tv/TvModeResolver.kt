package eu.kanade.tachiyomi.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import eu.kanade.domain.ui.model.TvUiMode

/** Android's TV classification is preferable to the Files-app heuristic used by onboarding. */
object TvModeResolver {
    fun useTv(context: Context, mode: TvUiMode): Boolean = when (mode) {
        TvUiMode.TV -> true
        TvUiMode.NORMAL -> false
        TvUiMode.AUTOMATIC ->
            (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType ==
                Configuration.UI_MODE_TYPE_TELEVISION
    }
}

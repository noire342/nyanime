package eu.kanade.tachiyomi.ui.base.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegateImpl
import eu.kanade.tachiyomi.ui.gestures.BackTapAction
import eu.kanade.tachiyomi.ui.gestures.BackTapContext
import eu.kanade.tachiyomi.ui.gestures.BackTapCoordinator
import eu.kanade.tachiyomi.ui.gestures.performBackTapNavigation
import eu.kanade.tachiyomi.ui.privacy.PrivacyDisplayController
import eu.kanade.tachiyomi.ui.privacy.PrivacyDisplayPreferences
import eu.kanade.tachiyomi.util.system.prepareTabletUiContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by SecureActivityDelegateImpl(),
    ThemingDelegate by ThemingDelegateImpl() {

    var privacyDisplayController: PrivacyDisplayController? = null
        private set

    protected open val backTapContext = BackTapContext.Navigation
    protected open fun backTapAvailable() = true
    protected open fun performBackTap(action: BackTapAction) = performBackTapNavigation(this, action)
    private var backTapBinding: BackTapCoordinator.Binding? = null

    protected fun suspendBackTap(suspended: Boolean) {
        backTapBinding?.suspend(suspended)
    }

    protected fun resetBackTap() {
        backTapBinding?.rearm()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        backTapBinding?.focusChanged(hasFocus)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        backTapBinding?.touch(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        backTapBinding?.focusChanged(hasWindowFocus())
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
        backTapBinding = Injekt.get<BackTapCoordinator>().bind(
            this,
            { backTapContext },
            ::backTapAvailable,
            ::performBackTap,
        )
        privacyDisplayController = PrivacyDisplayController(
            this,
            Injekt.get<PrivacyDisplayPreferences>(),
            Injekt.get<BasePreferences>().incognitoMode(),
        )
    }
}

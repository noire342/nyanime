package eu.kanade.tachiyomi.ui.privacy

import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import nyanime.privacy.display.AndroidPrivacyDisplayTarget
import nyanime.privacy.display.PrivacyBounds
import nyanime.privacy.display.PrivacyDisplayCapability
import nyanime.privacy.display.PrivacyDisplaySession
import nyanime.privacy.display.PrivacyDisplayState
import nyanime.privacy.display.PrivacyRegion
import nyanime.privacy.display.PrivacyUnavailableReason
import nyanime.privacy.display.PrivacyViewGeometry
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR

/** Lifecycle/geometry bridge. It never touches the native player or its playback state. */
class PrivacyDisplayController(
    private val activity: BaseActivity,
    private val preferences: PrivacyDisplayPreferences,
) : DefaultLifecycleObserver, ViewTreeObserver.OnPreDrawListener {
    private data class Declaration(
        val area: PrivacyArea,
        val scope: PrivacyArea,
        val rootView: () -> View,
        val boundsInWindow: () -> PrivacyBounds?,
    )
    private val declarations = linkedMapOf<Any, Declaration>()
    private var host: View? = null
    private var observedTree: ViewTreeObserver? = null
    private var session: PrivacyDisplaySession<AndroidPrivacyDisplayTarget>? = null
    private var target: AndroidPrivacyDisplayTarget? = null
    private var started = false
    private var closed = false
    private var policy = PrivacyDisplayPolicy(
        enabled = preferences.enabled().get(),
        selectedAreas = PrivacyArea.entries.filterTo(mutableSetOf()) { preferences.area(it).get() },
    )
    private val mutableState = MutableStateFlow<PrivacyDisplayState>(PrivacyDisplayState.Disabled)
    val state: StateFlow<PrivacyDisplayState> = mutableState
    private val mutableVideoEnabled = MutableStateFlow(policy.permits(PrivacyArea.VIDEO))
    val videoEnabled: StateFlow<Boolean> = mutableVideoEnabled
    private val mutableNsfwEnabled = MutableStateFlow(policy.permits(PrivacyArea.NSFW))
    val nsfwEnabled: StateFlow<Boolean> = mutableNsfwEnabled
    private val mutableEnabledAreas = MutableStateFlow(enabledAreasForPolicy())
    val enabledAreas: StateFlow<Set<PrivacyArea>> = mutableEnabledAreas

    init {
        activity.lifecycle.addObserver(this)
        combine(
            listOf(preferences.enabled().changes()) +
                PrivacyArea.entries.map {
                    preferences.area(it).changes()
                },
        ) { values ->
            values
        }.onEach { values ->
            policy = policy.copy(
                enabled = values[0],
                selectedAreas = PrivacyArea.entries.filterIndexed { index, _ -> values[index + 1] }.toSet(),
            )
            updateVideoPreference()
            reconcileListener()
        }.launchIn(activity.lifecycleScope)
    }

    fun setVideoOverride(enabled: Boolean) {
        policy = policy.copy(temporaryOverrides = policy.temporaryOverrides + (PrivacyArea.VIDEO to enabled))
        updateVideoPreference()
        reconcileListener()
    }

    /** Window-relative pixels; the controller alone translates them to display pixels. */
    fun register(
        key: Any,
        area: PrivacyArea,
        scope: PrivacyArea = area,
        rootView: () -> View,
        boundsInWindow: () -> PrivacyBounds?,
    ) {
        if (closed) return
        declarations[key] = Declaration(area, scope, rootView, boundsInWindow)
        reconcileListener()
    }

    fun unregister(key: Any) {
        declarations.remove(key)
        reconcileListener()
    }

    fun registerView(view: View, area: PrivacyArea, scope: PrivacyArea = area, eligible: () -> Boolean = { true }) {
        register(view to area, area, scope, { view.rootView }) {
            if (eligible()) PrivacyViewGeometry.boundsInWindow(view) else null
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        started = true
        reconcileListener()
    }

    override fun onStop(owner: LifecycleOwner) {
        started = false
        reconcileListener()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        closed = true
        detachListener()
        session?.close()
        session = null
        target?.close()
        target = null
        host = null
        declarations.clear()
        owner.lifecycle.removeObserver(this)
    }

    private fun selected(declaration: Declaration) = policy.permits(declaration.area, declaration.scope)

    private fun updateVideoPreference() {
        mutableVideoEnabled.value = policy.permits(PrivacyArea.VIDEO)
        mutableNsfwEnabled.value = policy.permits(PrivacyArea.NSFW)
        mutableEnabledAreas.value = enabledAreasForPolicy()
    }

    private fun enabledAreasForPolicy(): Set<PrivacyArea> = PrivacyArea.entries.filterTo(mutableSetOf()) {
        policy.permits(it) && PrivacyDisplayRuntime.capability(it) == PrivacyDisplayCapability.Available
    }

    private fun reconcileListener() {
        if (closed) return
        val wanted = started && declarations.values.any { selected(it) }
        if (!wanted) {
            detachListener()
            session?.update(null, false)
            return
        }
        val capability = PrivacyDisplayRuntime.capability()
        if (capability is PrivacyDisplayCapability.Unavailable) {
            session?.update(null, false)
            acceptState(PrivacyDisplayState.Unavailable(capability.reason))
            detachListener()
            return
        }
        val decor = activity.window.decorView
        if (host !== decor) {
            detachListener()
            session?.close()
            target?.close()
            val parent = decor as? ViewGroup ?: run {
                acceptState(PrivacyDisplayState.Unavailable(PrivacyUnavailableReason.WINDOW_MODE))
                return
            }
            host = decor
            val surface = AndroidPrivacyDisplayTarget(parent).also { target = it }
            session = PrivacyDisplaySession(surface, PrivacyDisplayRuntime.backend, ::acceptState)
        }
        if (observedTree?.isAlive != true) {
            observedTree = decor.viewTreeObserver.also { it.addOnPreDrawListener(this) }
        }
        decor.invalidate()
    }

    override fun onPreDraw(): Boolean {
        val decor = host ?: return true
        val displayId = decor.display?.displayId ?: return true
        val unavailable = when {
            displayId != Display.DEFAULT_DISPLAY -> PrivacyUnavailableReason.DISPLAY
            activity.isInPictureInPictureMode || activity.isInMultiWindowMode -> PrivacyUnavailableReason.WINDOW_MODE
            declarations.values.any { selected(it) && it.rootView() !== decor } -> PrivacyUnavailableReason.WINDOW_MODE
            else -> null
        }
        val visible = PrivacyViewGeometry.boundsInWindow(decor)
        if (visible == null) {
            session?.update(null, false)
            return true
        }
        val viewport = PrivacyRegion(PrivacyViewGeometry.windowToDisplay(visible, decor), displayId)
        val screen = IntArray(2)
        val window = IntArray(2)
        decor.getLocationOnScreen(screen)
        decor.getLocationInWindow(window)
        val dx = screen[0] - window[0]
        val dy = screen[1] - window[1]
        val regions = declarations.values.mapNotNull { declaration ->
            if (!selected(declaration) ||
                PrivacyDisplayRuntime.capability(declaration.area) != PrivacyDisplayCapability.Available
            ) {
                return@mapNotNull null
            }
            declaration.boundsInWindow()?.takeUnless { it.empty }?.let { bounds ->
                PrivacyRegion(
                    PrivacyBounds(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy),
                    displayId,
                )
            }
        }
        session?.update(PrivacyRegion.enclosing(regions, viewport), true, unavailable)
        return true
    }

    private fun acceptState(next: PrivacyDisplayState) {
        mutableState.value = next
        if (next is PrivacyDisplayState.Applied && !preferences.everApplied().get()) preferences.everApplied().set(true)
        if (next is PrivacyDisplayState.Failed || next is PrivacyDisplayState.Unavailable) {
            if (preferences.claimFailureWarning()) {
                Toast.makeText(
                    activity,
                    activity.stringResource(AYMR.strings.privacy_display_interrupted),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        if (next is PrivacyDisplayState.Failed) {
            PrivacyDisplayRuntime.recordFailure(next)
            updateVideoPreference()
            detachListener()
        }
    }

    private fun detachListener() {
        observedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observedTree = null
    }
}

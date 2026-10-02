package eu.kanade.tachiyomi.ui.gestures

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import eu.kanade.presentation.motion.ModernMotion
import eu.kanade.presentation.motion.appMotionEnabled
import eu.kanade.presentation.theme.TachiyomiTheme
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

internal fun showBackTapQuickMenu(activity: ComponentActivity, perform: (BackTapAction) -> Boolean) {
    val dialog = ComponentDialog(activity)
    val lifecycle = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) {
            dialog.dismiss()
        }
        override fun onDestroy(owner: LifecycleOwner) {
            dialog.dismiss()
        }
    }
    activity.lifecycle.addObserver(lifecycle)
    dialog.setOnDismissListener { activity.lifecycle.removeObserver(lifecycle) }
    dialog.setContentView(
        ComposeView(activity).apply {
            setContent {
                TachiyomiTheme {
                    val motion = appMotionEnabled()
                    var entered by remember { mutableStateOf(false) }
                    var closing by remember { mutableStateOf(false) }
                    var chosen by remember { mutableStateOf<BackTapAction?>(null) }
                    val close: (BackTapAction?) -> Unit = { action ->
                        if (!closing) {
                            chosen = action
                            closing = true
                            entered = false
                        }
                    }
                    LaunchedEffect(Unit) { entered = true }
                    LaunchedEffect(closing) {
                        if (closing) {
                            if (motion) delay(ModernMotion.EXIT_MILLIS.toLong())
                            dialog.dismiss()
                            chosen?.let { action ->
                                if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                                    !activity.isFinishing &&
                                    !activity.isDestroyed
                                ) {
                                    perform(action)
                                }
                            }
                        }
                    }
                    BackHandler { close(null) }
                    val alpha by animateFloatAsState(
                        if (entered) 1f else 0f,
                        tween(
                            if (!motion) {
                                0
                            } else if (closing) {
                                ModernMotion.EXIT_MILLIS
                            } else {
                                ModernMotion.PAGE_MILLIS
                            },
                        ),
                        label = "rearTapQuickActions",
                    )
                    Surface(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
                    ) {
                        Column(
                            Modifier.padding(24.dp).windowInsetsPadding(WindowInsets.navigationBars),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                stringResource(AYMR.strings.back_tap_quick_menu),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            val actions = listOf(
                                BackTapAction.Search to Icons.Outlined.Search,
                                BackTapAction.Library to Icons.Outlined.CollectionsBookmark,
                                BackTapAction.Releases to Icons.Outlined.Event,
                                BackTapAction.Rooms to Icons.Outlined.Groups,
                            )
                            actions.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { (action, icon) ->
                                        Surface(
                                            onClick = { close(action) },
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Column(
                                                Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                Icon(
                                                    icon,
                                                    null,
                                                    Modifier.size(26.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    stringResource(action.title),
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { close(null) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(MR.strings.action_close))
                            }
                        }
                    }
                }
            }
        },
    )
    dialog.show()
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setGravity(Gravity.BOTTOM)
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NyanimeLogoColor
import eu.kanade.presentation.theme.NyanimeLogoIcon
import eu.kanade.presentation.theme.NyanimeWordmark
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.NyanimeLogoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun LogoColorPreferenceWidget(preferences: UiPreferences) {
    val context = LocalContext.current.applicationContext
    val color by preferences.logoColor().collectAsState()
    val scope = rememberCoroutineScope()
    var changing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LogoColorPreferenceContent(
        value = color,
        enabled = !changing,
        failed = failed,
        onSelect = { selected ->
            if (!changing && selected != color) {
                changing = true
                failed = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            NyanimeLogoManager.select(context, preferences, selected)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failed = true
                        logcat(LogPriority.WARN, error) { "Unable to change the launcher icon" }
                    } finally {
                        changing = false
                    }
                }
            }
        },
    )
}

@Composable
internal fun LogoColorPreferenceContent(
    value: NyanimeLogoColor,
    onSelect: (NyanimeLogoColor) -> Unit,
    enabled: Boolean = true,
    failed: Boolean = false,
) {
    BasePreferenceWidget(
        title = stringResource(R.string.nyanime_logo_color_title),
        subcomponent = {
            Column(
                Modifier.padding(horizontal = PrefsHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.nyanime_logo_color_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
                    if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NyanimeLogoColor.entries.forEach {
                                LogoColorCard(it, value == it, enabled, { onSelect(it) }, Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NyanimeLogoColor.entries.forEach {
                                LogoColorCard(it, value == it, enabled, { onSelect(it) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
                Text(
                    stringResource(
                        if (failed) R.string.nyanime_logo_color_error else R.string.nyanime_logo_color_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun LogoColorCard(
    color: NyanimeLogoColor,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (color) {
        NyanimeLogoColor.RED -> androidx.compose.ui.graphics.Color(0xFFFF3344)
        NyanimeLogoColor.SUN_YELLOW -> colorResource(R.color.nyanime_logo_yellow)
    }
    Surface(
        modifier = modifier.alpha(if (enabled) 1f else 0.65f),
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.ui.graphics.Color(0xFF111214),
        contentColor = androidx.compose.ui.graphics.Color.White,
        border = BorderStroke(2.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NyanimeLogoIcon(
                Modifier.size(
                    88.dp,
                ).background(colorResource(R.color.nyanime_launcher_background), RoundedCornerShape(22.dp)),
                logoColor = color,
            )
            NyanimeWordmark(Modifier.padding(top = 12.dp, bottom = 8.dp), logoColor = color, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = accent,
                        unselectedColor = androidx.compose.ui.graphics.Color.LightGray,
                    ),
                )
                Text(
                    stringResource(
                        when (color) {
                            NyanimeLogoColor.RED -> R.string.nyanime_logo_color_red
                            NyanimeLogoColor.SUN_YELLOW -> R.string.nyanime_logo_color_yellow
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

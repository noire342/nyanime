package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BottomReaderBar(
    backgroundColor: Color,
    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
    onTranslate: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth().background(backgroundColor)) {
        onTranslate?.let { translate ->
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilledTonalButton(onClick = translate, modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Traduci pagina", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClickReadingMode) {
                Icon(
                    painter = painterResource(readingMode.iconRes),
                    contentDescription = stringResource(MR.strings.viewer),
                )
            }

            IconButton(onClick = onClickOrientation) {
                Icon(
                    imageVector = orientation.icon,
                    contentDescription = stringResource(MR.strings.rotation_type),
                )
            }

            IconButton(onClick = onClickCropBorder) {
                Icon(
                    painter = painterResource(
                        if (cropEnabled) R.drawable.ic_crop_24dp else R.drawable.ic_crop_off_24dp,
                    ),
                    contentDescription = stringResource(MR.strings.pref_crop_borders),
                )
            }

            IconButton(
                modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("reader_settings_button"),
                onClick = onClickSettings,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(MR.strings.action_settings),
                )
            }
        }
    }
}

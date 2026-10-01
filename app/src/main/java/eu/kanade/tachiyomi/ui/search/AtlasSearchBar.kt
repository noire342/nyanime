package eu.kanade.tachiyomi.ui.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.R
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AtlasSearchBar(model: AtlasSearchScreenModel, compact: Boolean, onClose: () -> Unit) {
    val state by model.state.collectAsState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(model) {
        if (Injekt.get<SourcePreferences>().atlasKeyboardOnOpen().get()) focus.requestFocus()
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 60.dp else 68.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            keyboard?.hide()
            focusManager.clearFocus()
            onClose()
        }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.atlas_close))
        }
        BasicTextField(
            value = state.input,
            onValueChange = model::edit,
            modifier = Modifier.weight(1f).focusRequester(focus).testTag("atlas_query"),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = androidx.compose.material3.LocalContentColor.current,
            ),
            cursorBrush = SolidColor(androidx.compose.material3.LocalContentColor.current),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                model.submit()
                keyboard?.hide()
            }),
            decorationBox = { input ->
                androidx.compose.foundation.layout.Box(Modifier.padding(vertical = 12.dp)) {
                    if (state.input.isEmpty()) {
                        Text(
                            stringResource(R.string.atlas_hint),
                            color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    input()
                }
            },
        )
        if (state.input.isNotEmpty()) {
            IconButton(onClick = { model.edit("") }) {
                Icon(Icons.Outlined.Close, stringResource(R.string.atlas_clear))
            }
        }
        IconButton(onClick = {
            model.submit()
            keyboard?.hide()
        }) {
            Icon(Icons.Outlined.Search, stringResource(R.string.atlas_search))
        }
    }
}

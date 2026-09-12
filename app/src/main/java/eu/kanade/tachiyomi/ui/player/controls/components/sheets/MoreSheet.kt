/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.tachiyomi.ui.player.Anime4KMode
import eu.kanade.tachiyomi.ui.player.Anime4KProfile
import eu.kanade.tachiyomi.ui.player.Decoder
import eu.kanade.tachiyomi.ui.player.applyAudioChannels
import eu.kanade.tachiyomi.ui.player.execute
import eu.kanade.tachiyomi.ui.player.executeLongPress
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioChannels
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as collectPreferenceAsState

@Composable
fun MoreSheet(
    selectedDecoder: Decoder,
    onSelectDecoder: (Decoder) -> Unit,
    remainingTime: Int,
    timerAtEpisodeEnd: Boolean,
    onOpenSleepTimer: () -> Unit,
    onOpenWatchTogether: () -> Unit,
    onDismissRequest: () -> Unit,
    onEnterFiltersPanel: () -> Unit,
    customButtons: ImmutableList<CustomButton>,
    onSelectAnime4KCustom: (Anime4KMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val advancedPreferences = remember { Injekt.get<AdvancedPlayerPreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    val statisticsPage by advancedPreferences.playerStatisticsPage().collectPreferenceAsState()
    val anime4kSelection by advancedPreferences.anime4kActiveSelection().collectAsState()
    val anime4kDiagnosticsEnabled by advancedPreferences.anime4kDiagnosticsEnabled().collectPreferenceAsState()

    PlayerSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AYMR.strings.player_sheets_more_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                TextButton(onClick = onEnterFiltersPanel) {
                    Icon(imageVector = Icons.Default.Tune, contentDescription = null)
                    Text(
                        text = stringResource(AYMR.strings.player_sheets_filters_title),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            SleepTimerEntry(remainingTime, onOpenSleepTimer, timerAtEpisodeEnd)
            TextButton(onClick = onOpenWatchTogether, modifier = Modifier.fillMaxWidth()) {
                Text("Guarda insieme", style = MaterialTheme.typography.titleMedium)
            }

            Text(stringResource(AYMR.strings.player_hwdec_mode))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(Decoder.entries.minus(Decoder.Auto)) { decoder ->
                    FilterChip(
                        selected = decoder == selectedDecoder,
                        onClick = { onSelectDecoder(decoder) },
                        label = { Text(text = decoder.title) },
                    )
                }
            }

            Text(stringResource(AYMR.strings.player_sheets_stats_page_title))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(6) { page ->
                    FilterChip(
                        label = {
                            Text(
                                stringResource(
                                    if (page ==
                                        0
                                    ) {
                                        AYMR.strings.player_sheets_tracks_off
                                    } else {
                                        AYMR.strings.player_sheets_stats_page_chip
                                    },
                                    page,
                                ),
                            )
                        },
                        onClick = {
                            if ((page == 0) xor (statisticsPage == 0)) {
                                MPVLib.command(arrayOf("script-binding", "stats/display-stats-toggle"))
                            }
                            if (page != 0) {
                                MPVLib.command(arrayOf("script-binding", "stats/display-page-$page"))
                            }
                            advancedPreferences.playerStatisticsPage().set(page)
                        },
                        selected = statisticsPage == page,
                    )
                }
            }

            Text(stringResource(AYMR.strings.pref_anime4k))
            FlowRow(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Anime4KMode.entries.forEach { mode ->
                    FilterChip(
                        selected = if (mode == Anime4KMode.Off) {
                            anime4kSelection.profile == Anime4KProfile.Off
                        } else {
                            anime4kSelection.profile == Anime4KProfile.Custom && anime4kSelection.mode == mode
                        },
                        onClick = { onSelectAnime4KCustom(mode) },
                        label = { Text(text = stringResource(mode.titleRes)) },
                    )
                }
                FilterChip(
                    selected = anime4kDiagnosticsEnabled,
                    onClick = {
                        advancedPreferences.anime4kDiagnosticsEnabled().set(!anime4kDiagnosticsEnabled)
                    },
                    label = { Text(text = stringResource(AYMR.strings.pref_anime4k_debug_overlay)) },
                )
            }

            if (customButtons.isNotEmpty()) {
                Text(text = stringResource(AYMR.strings.player_sheets_custom_buttons_title))
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.mediumSmall),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    maxItemsInEachRow = Int.MAX_VALUE,
                ) {
                    customButtons.forEach { button ->

                        val inputChipInteractionSource = remember { MutableInteractionSource() }

                        Box {
                            FilterChip(
                                onClick = {},
                                label = { Text(text = button.name) },
                                selected = false,
                                interactionSource = inputChipInteractionSource,
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .combinedClickable(
                                        onClick = { button.execute() },
                                        onLongClick = { button.executeLongPress() },
                                        interactionSource = inputChipInteractionSource,
                                        indication = null,
                                    ),
                            )
                        }
                    }
                }
            }
            Text(text = stringResource(AYMR.strings.pref_audio_channels))
            val audioChannels by audioPreferences.audioChannels().collectPreferenceAsState()
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(AudioChannels.entries) {
                    FilterChip(
                        selected = audioChannels == it,
                        onClick = {
                            applyAudioChannels(it, previous = audioChannels)
                            audioPreferences.audioChannels().set(it)
                        },
                        label = { Text(text = stringResource(it.titleRes)) },
                    )
                }
            }
        }
    }
}

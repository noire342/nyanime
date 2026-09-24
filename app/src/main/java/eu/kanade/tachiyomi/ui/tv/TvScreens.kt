package eu.kanade.tachiyomi.ui.tv

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.discovery.SourceHomeArtwork
import eu.kanade.tachiyomi.data.watch.WatchQr
import eu.kanade.tachiyomi.data.watch.WatchShortRooms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.SourceHomeGroup
import tachiyomi.domain.discovery.SourceHomeRequest
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun TvRoot(ui: TvUiController) {
    val catalog by ui.catalog.state.collectAsState()
    val headerOffsetPx = with(LocalDensity.current) {
        (if (LocalConfiguration.current.screenWidthDp < 1200) 106.dp else 82.dp).roundToPx()
    }
    ui.scrollRequest?.let { request ->
        LaunchedEffect(request.id, ui.screen) {
            if (request.screen == ui.screen) {
                val state = ui.scrollState(request.key)
                if (ui.reduceMotion) {
                    state.scrollToItem(request.index, scrollOffset = -headerOffsetPx)
                } else {
                    state.animateScrollToItem(request.index, scrollOffset = -headerOffsetPx)
                }
            }
            ui.finishScrollRequest(request.id)
        }
    }
    Box(Modifier.fillMaxSize().background(TvColors.background)) {
        when (ui.screen) {
            TvScreen.PICKER -> TvProfilePicker(ui)
            TvScreen.HOME -> TvHome(ui, catalog)
            TvScreen.SEARCH -> TvSearch(ui, catalog)
            TvScreen.DETAIL -> TvDetail(ui, catalog)
            TvScreen.SETTINGS -> TvSettings(ui)
            TvScreen.PROFILES -> TvProfileManager(ui)
            TvScreen.ROOMS -> TvRooms(ui)
            TvScreen.COMPANION -> TvCompanionScreen(ui)
        }
        ui.pinProfile?.let { profile -> TvPinPrompt(ui, profile) }
        ui.pinEditProfile?.let { profile -> TvPinEditPrompt(ui, profile) }
        ui.pendingDelete?.let { profile ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.background(TvColors.surface, RoundedCornerShape(18.dp)).padding(30.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Eliminare ${profile.name}?", color = TvColors.text, fontSize = 27.sp)
                    Text("La cronologia di questo profilo verrà rimossa.", color = TvColors.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvAction("Annulla", ui::cancelDeleteProfile, reduceMotion = ui.reduceMotion)
                        TvAction(
                            "Elimina",
                            ui::confirmDeleteProfile,
                            cue = TvColors.red,
                            reduceMotion = ui.reduceMotion,
                        )
                    }
                }
            }
        }
        ui.statusMessage?.let { message ->
            Text(
                message,
                Modifier.align(Alignment.BottomCenter).padding(15.dp),
                color = TvColors.red,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun TvProfilePicker(ui: TvUiController) {
    val choosingTeam = ui.teamSelection != null
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF30242F), TvColors.background), radius = 1000f),
        ).padding(horizontal = 52.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NYANIME · PROFILI", color = TvColors.muted, fontSize = 13.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(13.dp))
        Text(
            if (choosingTeam) "Chi guarda insieme?" else "Chi guarda?",
            color = TvColors.text,
            fontSize = 43.sp,
            fontWeight = FontWeight.Medium,
        )
        if (choosingTeam) {
            Spacer(Modifier.height(8.dp))
            Text("${ui.teamSelection!!.size + 1} di 2", color = TvColors.violet, fontSize = 18.sp)
        }
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(25.dp), verticalAlignment = Alignment.CenterVertically) {
            ui.profiles.forEachIndexed { index, profile ->
                val cue = if (ui.profiles.size <= 2) {
                    if (index == 0) TvColors.red else TvColors.green
                } else {
                    TvColors.text
                }
                TvProfileTile(
                    label = profile.name,
                    initial = profile.name.firstOrNull()?.uppercase().orEmpty(),
                    artwork = profile.artwork,
                    cue = cue,
                    hint = if (ui.profiles.size > 2) "${index + 1}" else null,
                    selected = profile.id in ui.teamSelection.orEmpty(),
                    reduceMotion = ui.reduceMotion,
                ) { ui.selectProfile(profile) }
            }
            if (!choosingTeam) {
                TvProfileTile(
                    "Anonimo",
                    "✦",
                    10,
                    if (ui.profiles.size <= 2) TvColors.yellow else TvColors.text,
                    if (ui.profiles.size > 2) "0" else null,
                    false,
                    ui.reduceMotion,
                    ui::anonymous,
                )
            }
        }
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            if (choosingTeam) {
                TvAction("Annulla", ui::cancelTeam, reduceMotion = ui.reduceMotion)
            } else {
                TvAction("Team Watch", ui::startTeam, cue = TvColors.violet, reduceMotion = ui.reduceMotion)
                TvAction(
                    "Gestisci profili",
                    ui::showProfileManager,
                    reduceMotion = ui.reduceMotion,
                )
                TvAction(
                    "Esci",
                    { (context as? TvActivity)?.finish() },
                    reduceMotion = ui.reduceMotion,
                )
            }
        }
    }
}

@Composable
private fun TvProfileTile(
    label: String,
    initial: String,
    artwork: Int,
    cue: Color,
    hint: String?,
    selected: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(126.dp)) {
        val palettes = listOf(
            listOf(Color(0xFFF24F68), Color(0xFF8D3BCC)),
            listOf(Color(0xFF366CE8), Color(0xFF8125D8)),
            listOf(Color(0xFF22A978), Color(0xFF086886)),
            listOf(Color(0xFFF2A547), Color(0xFFBD3A3A)),
        )
        val colors = palettes[artwork.mod(palettes.size)]
        val tileColors = if (artwork == 10) {
            listOf(Color(0xFF55515D), Color(0xFF24232B))
        } else {
            colors
        }
        TvFocusFrame(onClick, Modifier.size(111.dp), cue, selected, reduceMotion) {
            Box(
                Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(tileColors)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, color = Color.White, fontSize = 53.sp, fontWeight = FontWeight.Light)
            }
            hint?.let {
                Text(
                    it,
                    Modifier.align(Alignment.TopEnd).padding(9.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (selected) {
                Text(
                    "✓",
                    Modifier.align(Alignment.BottomEnd).padding(9.dp),
                    color = Color.White,
                    fontSize = 19.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = TvColors.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvPinPrompt(ui: TvUiController, profile: TvProfile) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .86f)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.background(TvColors.surface, RoundedCornerShape(20.dp)).padding(40.dp),
        ) {
            Text(profile.name, color = TvColors.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("Inserisci il PIN dal telecomando", color = TvColors.muted, fontSize = 18.sp)
            Spacer(Modifier.height(19.dp))
            Text(
                "● ".repeat(ui.pinBuffer.length).ifBlank { "○ ○ ○ ○" },
                color = TvColors.text,
                fontSize = 29.sp,
                letterSpacing = 5.sp,
            )
            if (ui.pinError) Text("PIN non corretto", color = TvColors.red, fontSize = 16.sp)
            Spacer(Modifier.height(18.dp))
            Text("OK per confermare · Indietro per annullare", color = TvColors.muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TvPinEditPrompt(ui: TvUiController, profile: TvProfile) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(460.dp).clip(RoundedCornerShape(19.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF30232F), TvColors.surface)))
                .padding(39.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(profile.name, color = TvColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Text(
                if (ui.pinEditConfirmation == null) {
                    "Scegli un PIN di quattro cifre"
                } else {
                    "Ripeti il PIN per confermare"
                },
                color = TvColors.muted,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "● ".repeat(ui.pinEditBuffer.length).padEnd(8, '○'),
                color = TvColors.text,
                fontSize = 30.sp,
                letterSpacing = 7.sp,
            )
            if (ui.pinEditError) {
                Text(
                    "I PIN non coincidono. Riprova.",
                    color = TvColors.red,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(19.dp))
            Text(
                "Usa i numeri o i tasti colore del telecomando",
                color = TvColors.muted,
                fontSize = 14.sp,
            )
            Text("Indietro per annullare", color = TvColors.muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TvHeader(ui: TvUiController, catalog: TvCatalogState, scrolled: Boolean = false) {
    val compact = LocalConfiguration.current.screenWidthDp < 1200
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else .02f,
        animationSpec = if (ui.reduceMotion) snap() else tween(190),
        label = "TV header opacity",
    )
    Column(
        Modifier.fillMaxWidth().zIndex(3f).background(
            Brush.verticalGradient(
                listOf(
                    TvColors.background.copy(alpha = .98f + backgroundAlpha * .02f),
                    TvColors.background.copy(alpha = .75f + backgroundAlpha * .25f),
                    TvColors.background.copy(alpha = backgroundAlpha),
                ),
            ),
        ).padding(horizontal = 44.dp, vertical = if (compact) 12.dp else 17.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "NYANIME",
                color = TvColors.accent,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.width(if (compact) 0.dp else 17.dp))
            if (compact) {
                Spacer(Modifier.weight(1f))
            } else {
                TvHomeTabs(ui, catalog, Modifier.weight(1f))
            }
            TvAction("Cerca", ui::openSearch, cue = TvColors.red, reduceMotion = ui.reduceMotion)
            TvAction("Stanze", ui::openRooms, reduceMotion = ui.reduceMotion)
            if (!compact) {
                TvAction("Collega telefono", ui::openCompanion, reduceMotion = ui.reduceMotion)
            }
            val profileName = ui.profiles.firstOrNull { it.id == ui.activeProfiles.firstOrNull() }?.name
                ?: "Anonimo"
            TvAction(profileName, ui::changeProfile, reduceMotion = ui.reduceMotion)
            TvAction("⋯", ui::openSettings, reduceMotion = ui.reduceMotion)
        }
        if (compact) {
            TvHomeTabs(ui, catalog, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TvHomeTabs(ui: TvUiController, catalog: TvCatalogState, modifier: Modifier) {
    LazyRow(
        modifier,
        state = ui.scrollState("home-tabs"),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        items(catalog.homes, key = { it.id }) { home ->
            TvNavAction(
                home.title,
                home.id == catalog.selectedHome,
                {
                    ui.catalog.selectHome(home.id)
                    ui.backToHome()
                },
                ui.reduceMotion,
            )
        }
    }
}

@Composable
private fun TvHome(ui: TvUiController, catalog: TvCatalogState) {
    val group = catalog.access.group
    val compact = LocalConfiguration.current.screenHeightDp < 500
    val list = ui.scrollState("home:${group?.id ?: "empty"}")
    val shortcuts = buildList {
        add(TvColors.red to "Cerca")
        ui.homeGreenLabel?.let { add(TvColors.green to it) }
        ui.homeYellowLabel?.let { add(TvColors.yellow to it) }
        if (catalog.homes.size > 1) add(TvColors.blue to "Cambia Home")
    }
    Box(Modifier.fillMaxSize()) {
        if (catalog.loading || catalog.access.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Caricamento delle Home…", color = TvColors.muted, fontSize = 21.sp)
            }
        } else if (group == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Installa e abilita un’estensione con Home per iniziare.",
                    color = TvColors.muted,
                    fontSize = 22.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(bottom = if (compact) 24.dp else 85.dp),
            ) {
                val heroSection = group.rows.asSequence().flatMap { it.sections.asSequence() }
                    .firstOrNull { it.layout == "featured" }
                    ?: group.rows.firstOrNull()?.sections?.firstOrNull()
                if (heroSection != null) {
                    item(key = "hero:${group.id}") {
                        LaunchedEffect(group.id, heroSection.id) { ui.catalog.load(heroSection.id) }
                        val item = catalog.sections[heroSection.id]?.data?.items?.firstOrNull()
                        if (item != null) {
                            TvHero(
                                item,
                                ui::open,
                                ui.reduceMotion,
                                greenCue = ui.homeGreenAction == TvHomeGreenAction.HERO,
                            )
                        } else {
                            Spacer(Modifier.height(145.dp))
                        }
                    }
                }
                if (compact) {
                    item(key = "shortcuts:${group.id}") {
                        TvLegend(
                            shortcuts,
                            Modifier.padding(start = 44.dp, top = 8.dp, bottom = 12.dp),
                            ui.legendTick,
                            ui.reduceMotion,
                        )
                    }
                }
                if (group.categories.isNotEmpty()) {
                    item(key = "categories:${group.id}") {
                        Text(
                            "Esplora per categoria",
                            Modifier.padding(start = 44.dp, top = 8.dp, bottom = 11.dp),
                            color = TvColors.text,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        LazyRow(
                            state = ui.scrollState("categories:${group.id}"),
                            contentPadding = PaddingValues(horizontal = 44.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(group.categories, key = { it.id }) { category ->
                                TvAction(
                                    category.title,
                                    { ui.openCategory(category.id) },
                                    reduceMotion = ui.reduceMotion,
                                )
                            }
                        }
                    }
                }
                item(key = "continue:${group.id}") {
                    TvSectionTitle("Continua a guardare")
                    val localItems = catalog.resume.data.orEmpty()
                    val personalItems = catalog.personalResume
                    if (localItems.isEmpty() && personalItems.isEmpty()) {
                        Text(
                            "Qui troverai ciò che hai iniziato a guardare.",
                            Modifier.padding(horizontal = 44.dp, vertical = 14.dp),
                            color = TvColors.muted,
                        )
                    } else {
                        LazyRow(
                            state = ui.scrollState("continue:${group.id}"),
                            contentPadding = PaddingValues(horizontal = 44.dp),
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                        ) {
                            items(localItems, key = { it.anime.id }) { item ->
                                TvPoster(
                                    item.anime,
                                    { ui.play(item.anime, item.episode) },
                                    ui.reduceMotion,
                                    "${item.episode.name} · ${(item.progress * 100).toInt()}%",
                                    cue = if (item == localItems.firstOrNull() &&
                                        ui.homeGreenAction == TvHomeGreenAction.RESUME
                                    ) {
                                        TvColors.green
                                    } else {
                                        null
                                    },
                                )
                            }
                            items(personalItems, key = { "${it.source}:${it.titleUrl}" }) { item ->
                                TvAction(
                                    "${item.title}\n${item.episodeName}",
                                    { ui.playPersonalContinue(item) },
                                    modifier = Modifier.width(190.dp),
                                    cue = if (localItems.isEmpty() &&
                                        item == personalItems.firstOrNull() &&
                                        ui.homeGreenAction == TvHomeGreenAction.RESUME
                                    ) {
                                        TvColors.green
                                    } else {
                                        null
                                    },
                                    reduceMotion = ui.reduceMotion,
                                )
                            }
                        }
                    }
                }
                if (catalog.savedTitles.isNotEmpty()) {
                    item(key = "saved:${group.id}") {
                        val key = "saved-category:${group.id}"
                        val category = ui.selectedSections[key].orEmpty()
                        val visible = catalog.savedTitles.filter { category.isEmpty() || it.category == category }
                        TvSectionTitle("La mia lista")
                        if (ui.activeProfiles.firstOrNull() != TvProfile.MAIN_ID) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 44.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(listOf("Tutti") + TvProfileRepository.categories) { option ->
                                    val value = if (option == "Tutti") "" else option
                                    TvAction(
                                        option,
                                        { ui.selectedSections[key] = value },
                                        selected = category == value,
                                        reduceMotion = ui.reduceMotion,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        if (visible.isEmpty()) {
                            Text(
                                "Nessun titolo in questa lista.",
                                Modifier.padding(horizontal = 44.dp, vertical = 9.dp),
                                color = TvColors.muted,
                            )
                        } else {
                            LazyRow(
                                state = ui.scrollState("saved:${group.id}"),
                                contentPadding = PaddingValues(horizontal = 44.dp),
                                horizontalArrangement = Arrangement.spacedBy(15.dp),
                            ) {
                                items(visible, key = { "${it.anime.source}:${it.anime.url}" }) { item ->
                                    TvPoster(item.anime, { ui.open(item.anime) }, ui.reduceMotion)
                                }
                            }
                        }
                    }
                }
                items(group.rows, key = { it.id }) { row ->
                    val selectionKey = "${group.id}:${row.id}"
                    val selected = row.selected(ui.selectedSections[selectionKey])
                    LaunchedEffect(group.id, selected.id) { ui.catalog.load(selected.id) }
                    val state = catalog.sections[selected.id]
                    Column(Modifier.padding(top = 25.dp, bottom = 15.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TvAction(
                                state?.data?.title ?: row.title,
                                { ui.openCategory(selected.id) },
                                cue = if (row.id == group.rows.firstOrNull()?.id &&
                                    ui.homeYellowLabel == "Sezioni"
                                ) {
                                    TvColors.yellow
                                } else {
                                    null
                                },
                                reduceMotion = ui.reduceMotion,
                            )
                            if (row.sections.size > 1) {
                                Spacer(Modifier.width(18.dp))
                                row.sections.forEach { section ->
                                    TvAction(
                                        section.group?.tab ?: section.title,
                                        { ui.selectedSections[selectionKey] = section.id },
                                        selected = selected.id == section.id,
                                        reduceMotion = ui.reduceMotion,
                                    )
                                    Spacer(Modifier.width(7.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(11.dp))
                        val entries = state?.data?.items.orEmpty()
                        if (entries.isNotEmpty()) {
                            LazyRow(
                                state = ui.scrollState("row:$selectionKey:${selected.id}"),
                                contentPadding = PaddingValues(horizontal = 44.dp),
                                horizontalArrangement = Arrangement.spacedBy(15.dp),
                            ) {
                                items(entries, key = { "${it.source}:${it.url}" }) { anime ->
                                    TvPoster(anime, { ui.open(anime) }, ui.reduceMotion)
                                }
                            }
                        } else if (state?.loading == true) {
                            Text("Caricamento…", Modifier.padding(horizontal = 44.dp), color = TvColors.muted)
                        } else if (state?.error != null) {
                            TvAction(
                                "Riprova",
                                { ui.catalog.load(selected.id, refresh = true) },
                                Modifier.padding(horizontal = 44.dp),
                                reduceMotion = ui.reduceMotion,
                            )
                        }
                    }
                }
            }
        }
        TvHeader(ui, catalog, scrolled = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 24)
        if (!compact) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().zIndex(2f)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                TvColors.background.copy(alpha = .96f),
                            ),
                        ),
                    ),
            ) {
                TvLegend(
                    shortcuts,
                    Modifier.padding(start = 44.dp, top = 17.dp, bottom = 12.dp),
                    ui.legendTick,
                    ui.reduceMotion,
                )
            }
        }
    }
}

@Composable
private fun TvHero(
    anime: Anime,
    open: (Anime) -> Unit,
    reduceMotion: Boolean,
    greenCue: Boolean,
) {
    val availableHeight = LocalConfiguration.current.screenHeightDp
    val height = (availableHeight * .68f).dp.coerceIn(265.dp, 600.dp)
    val compact = availableHeight < 500
    Box(Modifier.fillMaxWidth().height(height).background(TvColors.surface)) {
        SourceHomeArtwork(anime, Modifier.fillMaxSize(), background = true)
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        TvColors.background.copy(alpha = .99f),
                        TvColors.background.copy(alpha = .76f),
                        TvColors.background.copy(alpha = .10f),
                    ),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = .23f),
                        Color.Transparent,
                        TvColors.background.copy(alpha = .97f),
                    ),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(
                    start = if (compact) 30.dp else 58.dp,
                    bottom = if (compact) 18.dp else 49.dp,
                )
                .fillMaxWidth(if (compact) .68f else .59f),
        ) {
            Text(
                "IN EVIDENZA",
                color = TvColors.muted,
                fontSize = if (compact) 10.sp else 14.sp,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(if (compact) 5.dp else 12.dp))
            val titleSize = (availableHeight * .055f).coerceIn(25f, 58f)
            Text(
                anime.title,
                color = TvColors.text,
                fontSize = titleSize.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (titleSize * 1.05f).sp,
            )
            if (!compact) {
                anime.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        description,
                        color = TvColors.text.copy(alpha = .83f),
                        fontSize = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 24.sp,
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 11.dp else 24.dp))
            TvAction(
                "Apri episodi  →",
                { open(anime) },
                cue = if (greenCue) TvColors.green else null,
                reduceMotion = reduceMotion,
            )
        }
    }
}

@Composable
private fun TvSectionTitle(title: String) {
    Text(
        title,
        Modifier.padding(horizontal = 44.dp, vertical = 11.dp),
        color = TvColors.text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TvPoster(
    anime: Anime,
    onClick: () -> Unit,
    reduceMotion: Boolean,
    subtitle: String = "",
    cue: Color? = null,
) {
    Column(Modifier.width(155.dp)) {
        TvFocusFrame(
            onClick,
            Modifier.fillMaxWidth().height(218.dp),
            cue = cue ?: TvColors.text,
            reduceMotion = reduceMotion,
        ) {
            Box(
                Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(8.dp))
                    .background(TvColors.surface),
            ) {
                SourceHomeArtwork(anime, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            anime.title,
            color = TvColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp,
            lineHeight = 19.sp,
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                color = TvColors.muted,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvSearch(ui: TvUiController, catalog: TvCatalogState) {
    val group = catalog.access.group
    var query by remember(group?.id) { mutableStateOf(ui.searchQuery) }
    var filters by remember(group?.id) { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var categoryId by remember(group?.id) { mutableStateOf(ui.browseCategoryId) }
    var showFilters by remember { mutableStateOf(false) }
    LaunchedEffect(query, filters, group?.id, categoryId) {
        delay(320)
        if (group == null) return@LaunchedEffect
        if (query.isNotBlank() || filters.isNotEmpty() || categoryId == null) {
            if (group.searchable) ui.catalog.search(query, filters)
        } else {
            ui.catalog.load(categoryId!!)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TvHeader(ui, catalog)
            Text(
                "Esplora ${group?.title.orEmpty()}",
                Modifier.padding(start = 44.dp, top = 22.dp),
                color = TvColors.text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                query,
                {
                    query = it
                    ui.searchQuery = it
                    if (it.isNotBlank()) categoryId = null
                },
                Modifier.fillMaxWidth(.75f).padding(start = 44.dp, top = 15.dp),
                placeholder = { Text("Cerca un titolo…") },
                singleLine = true,
            )
            if (group != null) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 44.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(group.categories) { category ->
                        TvAction(
                            category.title,
                            {
                                query = ""
                                categoryId = category.id
                                filters = emptyMap()
                            },
                            selected = categoryId == category.id,
                            reduceMotion = ui.reduceMotion,
                        )
                    }
                }
                if (group.browseFilters.isNotEmpty()) {
                    TvAction(
                        "Filtri${if (filters.isEmpty()) "" else " · ${filters.size}"}",
                        { showFilters = true },
                        Modifier.padding(start = 44.dp),
                        cue = TvColors.yellow,
                        reduceMotion = ui.reduceMotion,
                    )
                }
            }
            val results = catalog.sections[categoryId ?: SourceHomeRequest.SEARCH]
            Text(
                group?.categories?.firstOrNull { it.id == categoryId }?.title
                    ?: if (query.isBlank()) "Scopri tutti i titoli" else "Risultati",
                Modifier.padding(start = 44.dp, top = 15.dp),
                color = TvColors.text,
                fontSize = 21.sp,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(175.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 15.dp, bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                gridItems(results?.data?.items.orEmpty(), key = { "${it.source}:${it.url}" }) { anime ->
                    TvPoster(anime, { ui.open(anime) }, ui.reduceMotion)
                }
            }
            if (results?.loading == true) {
                Text(
                    "Ricerca in corso…",
                    Modifier.padding(start = 44.dp),
                    color = TvColors.muted,
                )
            }
            if (results?.error != null) {
                Text(
                    "Ricerca non disponibile: ${results.error}",
                    Modifier.padding(start = 44.dp),
                    color = TvColors.red,
                )
            }
        }
        if (showFilters && group != null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .88f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxWidth(.80f).fillMaxHeight(.84f)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF29232C), Color(0xFF151518))))
                        .padding(28.dp),
                ) {
                    Text(
                        "Esplora e filtra",
                        color = TvColors.text,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(15.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(19.dp)) {
                        items(group.browseFilters.filter { it.options.isNotEmpty() }) { filter ->
                            Column {
                                Text(filter.name, color = TvColors.muted, fontSize = 20.sp)
                                Spacer(Modifier.height(7.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(filter.options) { option ->
                                        val active = option in filters[filter.name].orEmpty()
                                        TvAction(option, {
                                            val next = if (active) emptyList() else listOf(option)
                                            filters = if (next.isEmpty()) {
                                                filters - filter.name
                                            } else {
                                                filters + (filter.name to next)
                                            }
                                            categoryId = null
                                        }, selected = active, reduceMotion = ui.reduceMotion)
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvAction(
                            "Mostra risultati",
                            { showFilters = false },
                            cue = TvColors.red,
                            reduceMotion = ui.reduceMotion,
                        )
                        TvAction(
                            "Azzera filtri",
                            {
                                filters = emptyMap()
                                categoryId = null
                            },
                            reduceMotion = ui.reduceMotion,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDetail(ui: TvUiController, catalog: TvCatalogState) {
    val anime = catalog.detail ?: return
    val compact = LocalConfiguration.current.screenHeightDp < 500
    val listState = ui.scrollState("detail:${anime.id}")
    val summaryVisible = listState.firstVisibleItemIndex == 0
    val activeEpisode = recommendedTvEpisode(
        catalog.detailEpisodes,
        catalog.detailProfileStates,
        catalog.detailUsesMainState,
    )
    val shortcuts = buildList {
        add(TvColors.red to "Cerca")
        if (activeEpisode != null) add(TvColors.green to "Riproduci")
        add(TvColors.yellow to "Episodi")
        if (catalog.homes.size > 1) add(TvColors.blue to "Cambia Home")
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            item(key = "summary:${anime.id}") {
                Box(
                    Modifier.fillMaxWidth().height(if (compact) 310.dp else 425.dp)
                        .background(TvColors.surface),
                ) {
                    SourceHomeArtwork(anime, Modifier.fillMaxSize(), background = true)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(
                                    TvColors.background,
                                    TvColors.background.copy(alpha = .85f),
                                    TvColors.background.copy(alpha = .14f),
                                ),
                            ),
                        ),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, TvColors.background.copy(alpha = .98f))),
                        ),
                    )
                    Row(
                        Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth().padding(
                                start = if (compact) 32.dp else 58.dp,
                                end = if (compact) 32.dp else 58.dp,
                                bottom = if (compact) 16.dp else 38.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 30.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box(
                            Modifier.width(if (compact) 105.dp else 175.dp)
                                .height(if (compact) 148.dp else 245.dp).clip(RoundedCornerShape(9.dp))
                                .background(TvColors.surface),
                        ) {
                            SourceHomeArtwork(anime, Modifier.fillMaxSize())
                        }
                        Column(Modifier.fillMaxWidth(.72f)) {
                            Text(
                                anime.title,
                                color = TvColors.text,
                                fontSize = if (compact) 28.sp else 48.sp,
                                lineHeight = if (compact) 31.sp else 51.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!compact) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    anime.description.orEmpty(),
                                    color = TvColors.text.copy(alpha = .84f),
                                    fontSize = 17.sp,
                                    lineHeight = 23.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                activeEpisode?.let { episode ->
                                    val lastPosition = catalog.detailProfileStates[episode.url]?.positionMs
                                        ?: if (catalog.detailUsesMainState) episode.lastSecondSeen else 0L
                                    TvAction(
                                        "${if (lastPosition > 0) "Riprendi" else "Riproduci"}  →",
                                        { ui.play(anime, episode) },
                                        cue = if (summaryVisible) TvColors.green else null,
                                        reduceMotion = ui.reduceMotion,
                                    )
                                }
                                if (ui.activeProfiles.firstOrNull() != TvCatalogController.ANONYMOUS_ID) {
                                    TvAction(
                                        if (catalog.detailFavorite) "✓ Nella mia lista" else "+ La mia lista",
                                        ui.catalog::toggleFavorite,
                                        reduceMotion = ui.reduceMotion,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (compact) {
                item(key = "shortcuts:${anime.id}") {
                    TvLegend(
                        shortcuts,
                        Modifier.padding(start = 32.dp, top = 9.dp, bottom = 7.dp),
                        ui.legendTick,
                        ui.reduceMotion,
                    )
                }
            }
            item(key = "episodes:${anime.id}") {
                Column(Modifier.padding(horizontal = if (compact) 32.dp else 58.dp)) {
                    if (ui.activeProfiles.firstOrNull()?.let {
                            it != TvProfile.MAIN_ID &&
                                it != TvCatalogController.ANONYMOUS_ID
                        } == true
                    ) {
                        Text(
                            "Organizza nella lista",
                            color = TvColors.text,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(9.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(TvProfileRepository.categories) { category ->
                                TvAction(
                                    category,
                                    {
                                        ui.catalog.setDetailCategory(
                                            if (catalog.detailCategory == category) "" else category,
                                        )
                                    },
                                    selected = catalog.detailCategory == category,
                                    reduceMotion = ui.reduceMotion,
                                )
                            }
                        }
                        Spacer(Modifier.height(19.dp))
                    }
                    Text(
                        "Episodi",
                        color = TvColors.text,
                        fontSize = 29.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(15.dp))
                    if (catalog.detailLoading) Text("Caricamento degli episodi…", color = TvColors.muted)
                    catalog.detailError?.let { error -> Text(error, color = TvColors.red) }
                    if (catalog.detailSeasons.isNotEmpty()) {
                        Row(Modifier.height(440.dp), horizontalArrangement = Arrangement.spacedBy(23.dp)) {
                            LazyColumn(Modifier.weight(.3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(catalog.detailSeasons, key = { it.id }) { season ->
                                    TvAction(
                                        season.title,
                                        { ui.catalog.open(season) },
                                        Modifier.fillMaxWidth(),
                                        reduceMotion = ui.reduceMotion,
                                    )
                                }
                            }
                            TvEpisodeList(
                                ui,
                                anime,
                                catalog.detailEpisodes,
                                catalog.detailProfileStates,
                                catalog.detailUsesMainState,
                                Modifier.weight(.7f),
                                activeEpisode?.id?.takeUnless { summaryVisible },
                            )
                        }
                    } else {
                        TvEpisodeList(
                            ui,
                            anime,
                            catalog.detailEpisodes,
                            catalog.detailProfileStates,
                            catalog.detailUsesMainState,
                            Modifier.height(440.dp).fillMaxWidth(),
                            activeEpisode?.id?.takeUnless { summaryVisible },
                        )
                    }
                }
            }
        }
        TvHeader(
            ui,
            catalog,
            scrolled =
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24,
        )
        if (!compact) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().zIndex(2f)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                TvColors.background.copy(alpha = .96f),
                            ),
                        ),
                    ),
            ) {
                TvLegend(
                    shortcuts,
                    Modifier.padding(start = 44.dp, top = 17.dp, bottom = 12.dp),
                    ui.legendTick,
                    ui.reduceMotion,
                )
            }
        }
    }
}

@Composable
private fun TvEpisodeList(
    ui: TvUiController,
    anime: Anime,
    episodes: List<Episode>,
    states: Map<String, TvEpisodeState>,
    useMainState: Boolean,
    modifier: Modifier,
    recommendedEpisodeId: Long?,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(episodes, key = { it.id }) { episode ->
            val state = states[episode.url]
            val progress = state?.let { value ->
                if (value.durationMs > 0) {
                    (value.positionMs * 100 / value.durationMs).coerceIn(0, 100)
                } else {
                    0
                }
            } ?: if (useMainState && episode.totalSeconds > 0) {
                (episode.lastSecondSeen * 100 / episode.totalSeconds).coerceIn(0, 100)
            } else {
                0
            }
            val episodeLabel = if (episode.episodeNumber >= 0) {
                "${episode.episodeNumber.toInt()}. ${episode.name}"
            } else {
                episode.name
            }
            TvAction(
                label = episodeLabel +
                    when {
                        state?.seen ?: (useMainState && episode.seen) -> "  ·  Visto"
                        progress > 0 -> "  ·  $progress%"
                        else -> ""
                    },
                onClick = { ui.play(anime, episode) },
                modifier = Modifier.fillMaxWidth(),
                cue = if (episode.id == recommendedEpisodeId) TvColors.green else null,
                reduceMotion = ui.reduceMotion,
            )
        }
    }
}

@Composable
private fun TvSettings(ui: TvUiController) {
    Column(Modifier.fillMaxSize().padding(50.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("Impostazioni TV", color = TvColors.text, fontSize = 35.sp, fontWeight = FontWeight.Bold)
        TvAction("Gestisci profili", { ui.showProfileManager() }, reduceMotion = ui.reduceMotion)
        TvAction(
            "Collega telefono",
            ui::openCompanion,
            cue = TvColors.blue,
            reduceMotion = ui.reduceMotion,
        )
        TvAction(
            if (ui.reduceMotion) "Animazioni ridotte: sì" else "Animazioni ridotte: no",
            { ui.updateReduceMotion(!ui.reduceMotion) },
            reduceMotion = ui.reduceMotion,
        )
        TvAction("Usa l’interfaccia normale", ui::useNormalUi, reduceMotion = ui.reduceMotion)
        Text(
            "Nell’interfaccia normale trovi manga, download e tutte le impostazioni dell’app.",
            color = TvColors.muted,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun TvCompanionScreen(ui: TvUiController) {
    val connection by ui.companion.state.collectAsState()
    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF172331), TvColors.background), radius = 1200f),
        ),
    ) {
        val compact = maxHeight < 500.dp
        Row(
            Modifier.fillMaxSize().padding(
                horizontal = if (compact) 38.dp else 76.dp,
                vertical = if (compact) 25.dp else 70.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 30.dp else 75.dp),
        ) {
            Column(Modifier.weight(.9f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "NYANIME · COMPANION",
                    color = TvColors.blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Text(
                    "Collega il telefono",
                    color = TvColors.text,
                    fontSize = if (compact) 30.sp else 52.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = if (compact) 34.sp else 56.sp,
                )
                Text(
                    "Apri un video sul telefono, scegli Cast e seleziona questa schermata. " +
                        "Conferma il codice mostrato su entrambi i dispositivi.",
                    color = TvColors.muted,
                    fontSize = if (compact) 16.sp else 21.sp,
                    lineHeight = if (compact) 21.sp else 28.sp,
                )
                Text(
                    "Il telefono conserva il controllo e il progresso della visione.",
                    color = TvColors.text.copy(alpha = .72f),
                    fontSize = if (compact) 14.sp else 17.sp,
                )
                TvAction("Torna alla Home", ui::backToHome, reduceMotion = ui.reduceMotion)
            }
            Column(
                Modifier.weight(1f)
                    .background(Color(0xED202329), RoundedCornerShape(22.dp))
                    .padding(if (compact) 22.dp else 37.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 13.dp else 22.dp),
            ) {
                when {
                    connection.error.isNotBlank() -> {
                        Text(
                            "Collegamento non disponibile",
                            color = TvColors.text,
                            fontSize = if (compact) 24.sp else 31.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(connection.error, color = TvColors.muted, fontSize = 17.sp)
                    }
                    connection.pairingCode.isNotBlank() -> {
                        Text(
                            "Conferma sullo schermo",
                            color = TvColors.text,
                            fontSize = if (compact) 24.sp else 31.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${connection.pairingName} vuole collegarsi",
                            color = TvColors.muted,
                            fontSize = 17.sp,
                        )
                        Text(
                            connection.pairingCode.chunked(3).joinToString("  "),
                            color = TvColors.text,
                            fontSize = if (compact) 39.sp else 59.sp,
                            letterSpacing = 6.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvAction(
                                "Conferma",
                                ui.companion::approve,
                                cue = TvColors.green,
                                reduceMotion = ui.reduceMotion,
                            )
                            TvAction(
                                "Rifiuta",
                                ui.companion::reject,
                                cue = TvColors.red,
                                reduceMotion = ui.reduceMotion,
                            )
                        }
                    }
                    connection.connectedName.isNotBlank() -> {
                        Text(
                            "Telefono collegato",
                            color = TvColors.text,
                            fontSize = if (compact) 24.sp else 31.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            connection.connectedName,
                            color = TvColors.green,
                            fontSize = if (compact) 19.sp else 25.sp,
                        )
                        Text(
                            "Puoi scegliere e controllare i video dal telefono.",
                            color = TvColors.muted,
                            fontSize = 17.sp,
                        )
                        TvAction(
                            "Scollega",
                            ui.companion::disconnect,
                            cue = TvColors.red,
                            reduceMotion = ui.reduceMotion,
                        )
                    }
                    else -> {
                        Text(
                            "Pronto per il collegamento",
                            color = TvColors.text,
                            fontSize = if (compact) 24.sp else 31.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Trova Nyanime TV nel menu Cast del telefono.",
                            color = TvColors.muted,
                            fontSize = 17.sp,
                        )
                        if (connection.address.isNotBlank()) {
                            Text(
                                "Se non compare, inserisci questo indirizzo sul telefono:",
                                color = TvColors.muted,
                                fontSize = 15.sp,
                            )
                            Text(
                                connection.address,
                                color = TvColors.blue,
                                fontSize = if (compact) 24.sp else 31.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvProfileManager(ui: TvUiController) {
    var name by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember(editingId) {
        mutableStateOf(ui.profiles.firstOrNull { it.id == editingId }?.name.orEmpty())
    }
    var artwork by remember(editingId) {
        mutableStateOf(ui.profiles.firstOrNull { it.id == editingId }?.artwork ?: 0)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(50.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Profili", color = TvColors.text, fontSize = 35.sp, fontWeight = FontWeight.Bold)
        if (editingId == null) {
            ui.profiles.forEach { profile ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        profile.name,
                        color = TvColors.text,
                        fontSize = 21.sp,
                        modifier = Modifier.width(180.dp),
                    )
                    TvAction(
                        "Modifica",
                        { editingId = profile.id },
                        reduceMotion = ui.reduceMotion,
                    )
                    if (!profile.isMain) {
                        TvAction(
                            "Elimina",
                            { ui.deleteProfile(profile) },
                            cue = TvColors.red,
                            reduceMotion = ui.reduceMotion,
                        )
                    }
                }
            }
            if (ui.profiles.size < 5) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nome nuovo profilo") })
                TvAction(
                    "Aggiungi profilo",
                    {
                        ui.createProfile(name)
                        name = ""
                    },
                    reduceMotion = ui.reduceMotion,
                )
            }
        } else {
            val profile = ui.profiles.firstOrNull { it.id == editingId }
            if (profile != null) {
                OutlinedTextField(
                    editName,
                    { editName = it.take(40) },
                    label = { Text("Nome") },
                    singleLine = true,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvAction(
                        "Aspetto precedente",
                        { artwork = (artwork + 11) % 12 },
                        reduceMotion = ui.reduceMotion,
                    )
                    TvProfileTile(
                        editName.ifBlank { profile.name },
                        editName.firstOrNull()?.uppercase().orEmpty(),
                        artwork,
                        TvColors.violet,
                        null,
                        false,
                        ui.reduceMotion,
                    ) { }
                    TvAction(
                        "Aspetto successivo",
                        { artwork = (artwork + 1) % 12 },
                        reduceMotion = ui.reduceMotion,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    TvAction(
                        "Salva",
                        {
                            ui.renameProfile(profile, editName, artwork)
                            editingId = null
                        },
                        cue = TvColors.green,
                        reduceMotion = ui.reduceMotion,
                    )
                    TvAction(
                        if (profile.hasPin) "Cambia PIN" else "Imposta PIN",
                        { ui.editPin(profile) },
                        reduceMotion = ui.reduceMotion,
                    )
                    if (profile.hasPin) {
                        TvAction(
                            "Rimuovi PIN",
                            { ui.removePin(profile) },
                            reduceMotion = ui.reduceMotion,
                        )
                    }
                }
            }
        }
        TvAction(
            "Indietro",
            { if (editingId != null) editingId = null else ui.back() },
            reduceMotion = ui.reduceMotion,
        )
    }
}

@Composable
private fun TvRooms(ui: TvUiController) {
    val room by ui.watch.controller.state.collectAsState()
    val short by ui.watch.shortRooms.state.collectAsState()
    val opening by ui.watch.opening.collectAsState()
    val inviteUrl = remember(short.code) {
        short.code.takeIf { it.length == 8 }?.let { WatchShortRooms.link(it) }
    }
    val compact = LocalConfiguration.current.screenHeightDp < 500
    Column(
        Modifier.fillMaxSize().verticalScroll(
            rememberScrollState(),
        ).background(Brush.radialGradient(listOf(Color(0xFF2A1F31), TvColors.background), radius = 1100f))
            .padding(
                horizontal = if (compact) 32.dp else 64.dp,
                vertical = if (compact) 18.dp else 38.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 15.dp),
    ) {
        Text(
            "NYANIME · GUARDA INSIEME",
            color = TvColors.violet,
            fontSize = 13.sp,
            letterSpacing = 3.sp,
        )
        Text(
            if (room.active) "La tua stanza" else "Guardiamo insieme?",
            color = TvColors.text,
            fontSize = if (compact) 32.sp else 49.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (room.active) {
                room.message.ifBlank { "Scegli un episodio da condividere." }
            } else {
                "Crea una stanza o inserisci il codice di un amico."
            },
            color = TvColors.muted,
            fontSize = if (compact) 16.sp else 20.sp,
        )
        if (room.active) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inviteUrl != null) TvRoomQr(inviteUrl, compact)
                Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
                    Text(
                        "CODICE PER ENTRARE",
                        color = TvColors.muted,
                        fontSize = 15.sp,
                        letterSpacing = 3.sp,
                    )
                    Text(
                        short.code.ifBlank { "Preparazione…" },
                        color = TvColors.text,
                        fontSize = if (compact) 34.sp else 49.sp,
                        letterSpacing = if (compact) 4.sp else 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${room.members.size} nella stanza · ${room.relayCount} relay",
                        color = TvColors.muted,
                        fontSize = 18.sp,
                    )
                    short.requests.forEach { request ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${request.name} vuole entrare",
                                color = TvColors.text,
                                fontSize = 19.sp,
                            )
                            TvAction(
                                "Accetta",
                                { ui.watch.shortRooms.approve(request.id) },
                                cue = TvColors.green,
                                reduceMotion = ui.reduceMotion,
                            )
                            TvAction(
                                "Rifiuta",
                                { ui.watch.shortRooms.reject(request.id) },
                                cue = TvColors.red,
                                reduceMotion = ui.reduceMotion,
                            )
                        }
                    }
                    if (opening.loading) {
                        Text(
                            "Preparo l’episodio condiviso…",
                            color = TvColors.muted,
                            fontSize = 17.sp,
                        )
                    }
                    if (opening.error != null) {
                        Text(
                            opening.error.orEmpty(),
                            color = TvColors.red,
                            fontSize = 17.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvAction(
                            if (room.media == null) "Scegli un episodio" else "Apri episodio",
                            { if (room.media == null) ui.backToHome() else ui.watch.openSelectedVideo() },
                            reduceMotion = ui.reduceMotion,
                        )
                        TvAction(
                            "Esci dalla stanza",
                            ui::leaveRoom,
                            reduceMotion = ui.reduceMotion,
                        )
                    }
                }
            }
        } else if (ui.roomJoining) {
            Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
            Text("Codice di otto cifre", color = TvColors.text, fontSize = 26.sp)
            Text(
                ui.roomCodeInput.padEnd(8, '·'),
                color = TvColors.text,
                fontSize = if (compact) 34.sp else 56.sp,
                letterSpacing = if (compact) 6.sp else 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Inserisci i numeri dal telecomando. OK per entrare.",
                color = TvColors.muted,
                fontSize = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvAction(
                    "Entra",
                    ui::joinRoom,
                    cue = TvColors.green,
                    reduceMotion = ui.reduceMotion,
                )
                TvAction("Correggi", ui::removeRoomDigit, reduceMotion = ui.reduceMotion)
                TvAction("Annulla", ui::cancelJoinRoom, reduceMotion = ui.reduceMotion)
            }
            if (short.waiting) {
                Text(
                    short.message.ifBlank { "Attendo la conferma…" },
                    color = TvColors.muted,
                    fontSize = 16.sp,
                )
            }
        } else {
            Spacer(Modifier.height(if (compact) 10.dp else 30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvAction(
                    "Crea stanza",
                    ui::createRoom,
                    reduceMotion = ui.reduceMotion,
                )
                TvAction(
                    "Inserisci codice",
                    ui::beginJoinRoom,
                    cue = TvColors.blue,
                    reduceMotion = ui.reduceMotion,
                )
            }
            if (short.waiting) {
                Text(
                    short.message.ifBlank { "Attendo la conferma…" },
                    color = TvColors.muted,
                    fontSize = 17.sp,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 12.dp else 30.dp))
        TvAction("Torna alla Home", ui::backToHome, reduceMotion = ui.reduceMotion)
    }
}

@Composable
private fun TvRoomQr(url: String, compact: Boolean) {
    val bitmap = remember(url) {
        val matrix = WatchQr.encode(url)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }.asImageBitmap()
    }
    Box(
        Modifier.size(if (compact) 160.dp else 218.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.White).padding(8.dp),
    ) {
        Image(
            bitmap,
            contentDescription = "QR di invito alla stanza",
            Modifier.fillMaxSize(),
            filterQuality = FilterQuality.None,
        )
    }
}

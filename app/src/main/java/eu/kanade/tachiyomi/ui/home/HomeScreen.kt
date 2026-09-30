package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.motion.ModernMotion
import eu.kanade.presentation.motion.modernMotionEnabled
import eu.kanade.presentation.motion.posterForeground
import eu.kanade.presentation.theme.LocalDarkTheme
import eu.kanade.presentation.theme.LocalNyanimeStyle
import eu.kanade.presentation.theme.MangaSectionTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.cast.CastMiniController
import eu.kanade.tachiyomi.ui.download.DownloadsTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.LibrariesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.more.ReadyAppUpdateSurface
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.NavigationBar
import tachiyomi.presentation.core.components.material.NavigationRail
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

val LocalFloatingNavigationInset = compositionLocalOf { 0.dp }

object HomeScreen : Screen() {

    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()

    private const val TAB_FADE_DURATION = 200
    private const val TAB_NAVIGATOR_KEY = "HomeTabs"

    private val uiPreferences: UiPreferences by injectLazy()

    @Composable
    override fun Content() {
        remember { uiPreferences.installModernNavigationOnce() }
        val startScreen = uiPreferences.startScreen().get()
        val defaultTab = startScreen.tab
        remember(startScreen) {
            if (startScreen == eu.kanade.domain.ui.model.StartScreen.MANGA) LibrariesTab.showManga()
            if (startScreen == eu.kanade.domain.ui.model.StartScreen.ANIME) LibrariesTab.showAnime()
        }
        val navStyle = eu.kanade.domain.ui.model.NavStyle.DISCOVERY
        val navigator = LocalNavigator.currentOrThrow
        TabNavigator(
            tab = defaultTab,
            key = TAB_NAVIGATOR_KEY,
        ) { tabNavigator ->
            MangaSectionTheme(legacy = false) {
                val modern = LocalNyanimeStyle.current
                val motion = modernMotionEnabled()
                // Provide usable navigator to content screen
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    val bottomNavVisible by produceState(initialValue = true) {
                        showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
                    }
                    val showNavigation = !isTabletUi() &&
                        bottomNavVisible &&
                        tabNavigator.current !in navStyle.overflowTabs
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val compactNavigation = maxWidth - 24.dp < 350.dp || LocalDensity.current.fontScale > 1.3f
                        val systemNavigationInset = with(LocalDensity.current) {
                            WindowInsets.navigationBars.getBottom(this).toDp()
                        }
                        val floatingInset = if (modern && showNavigation) {
                            (if (compactNavigation) 60.dp else 68.dp) + 26.dp + systemNavigationInset
                        } else {
                            0.dp
                        }
                        Scaffold(
                            modifier = Modifier.semantics { testTagsAsResourceId = true },
                            startBar = {
                                if (isTabletUi()) {
                                    NavigationRail(modifier = Modifier.posterForeground(zIndex = 3f)) {
                                        navStyle.visibleTabs.fastForEach {
                                            NavigationRailItem(it)
                                        }
                                    }
                                }
                            },
                            bottomBar = {
                                if (!modern || !showNavigation) {
                                    HomeBottomControls(
                                        showNavigation,
                                        false,
                                        motion,
                                        tabNavigator.current,
                                        navStyle.visibleTabs,
                                    )
                                }
                            },
                            contentWindowInsets = WindowInsets(0),
                        ) { contentPadding ->
                            Box(
                                modifier = Modifier
                                    .padding(contentPadding)
                                    .consumeWindowInsets(contentPadding),
                            ) {
                                AnimatedContent(
                                    targetState = tabNavigator.current,
                                    transitionSpec = {
                                        if (modern) {
                                            ModernMotion.transform(motion).using(null)
                                        } else {
                                            val fade = materialFadeThroughIn(
                                                initialScale = 1f,
                                                durationMillis = TAB_FADE_DURATION,
                                            ) togetherWith materialFadeThroughOut(durationMillis = TAB_FADE_DURATION)
                                            fade
                                        }
                                    },
                                    label = "tabContent",
                                ) {
                                    tabNavigator.saveableState(key = "currentTab", it) {
                                        CompositionLocalProvider(
                                            LocalFloatingNavigationInset provides floatingInset,
                                        ) {
                                            Box(Modifier.testTag("content_${navigationTag(it)}")) {
                                                it.Content()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (modern && showNavigation) {
                            HomeBottomControls(
                                showNavigation,
                                true,
                                motion,
                                tabNavigator.current,
                                navStyle.visibleTabs,
                                Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }

            val goToStartScreen = {
                tabNavigator.current = defaultTab
            }
            BackHandler(
                enabled = tabNavigator.current != defaultTab,
                onBack = goToStartScreen,
            )

            LaunchedEffect(Unit) {
                launch {
                    librarySearchEvent.receiveAsFlow().collectLatest {
                        tabNavigator.current = LibrariesTab
                        if (startScreen == eu.kanade.domain.ui.model.StartScreen.MANGA) {
                            LibrariesTab.showManga()
                            MangaLibraryTab.search(it)
                        } else {
                            LibrariesTab.showAnime()
                            AnimeLibraryTab.search(it)
                        }
                    }
                }
                launch {
                    openTabEvent.receiveAsFlow().collectLatest {
                        tabNavigator.current = when (it) {
                            is Tab.Home -> eu.kanade.tachiyomi.ui.discovery.DiscoveryTab
                            is Tab.AnimeLib -> LibrariesTab.also { LibrariesTab.showAnime() }
                            is Tab.Library -> LibrariesTab.also { LibrariesTab.showManga() }
                            is Tab.Releases -> eu.kanade.tachiyomi.ui.releases.ReleasesTab
                            is Tab.Updates -> UpdatesTab
                            is Tab.History -> HistoriesTab
                            is Tab.Browse -> {
                                if (it.toExtensions) {
                                    if (!it.anime) {
                                        BrowseTab.showExtension()
                                    } else {
                                        BrowseTab.showAnimeExtension()
                                    }
                                }
                                BrowseTab
                            }
                            is Tab.More -> MoreTab
                        }

                        if (it is Tab.AnimeLib && it.animeIdToOpen != null) {
                            navigator.push(AnimeScreen(it.animeIdToOpen))
                        }
                        if (it is Tab.Library && it.mangaIdToOpen != null) {
                            navigator.push(MangaScreen(it.mangaIdToOpen))
                        }
                        if (it is Tab.More && it.toDownloads) {
                            navigator.push(DownloadsTab)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HomeBottomControls(
        showNavigation: Boolean,
        modern: Boolean,
        motion: Boolean,
        currentTab: cafe.adriel.voyager.navigator.tab.Tab,
        tabs: List<eu.kanade.presentation.util.Tab>,
        modifier: Modifier = Modifier,
    ) {
        Column(modifier.posterForeground(zIndex = 3f)) {
            if (currentTab != MoreTab) ReadyAppUpdateSurface(allowDismiss = true)
            eu.kanade.tachiyomi.ui.watch.WatchMiniController(includeNavigationInsets = !showNavigation)
            CastMiniController(includeNavigationInsets = !showNavigation)
            AnimatedVisibility(
                visible = showNavigation,
                enter = if (modern) {
                    expandVertically(
                        tween(if (motion) ModernMotion.RESIZE_MILLIS else 0),
                    )
                } else {
                    expandVertically()
                },
                exit = if (modern) {
                    shrinkVertically(
                        tween(if (motion) ModernMotion.RESIZE_MILLIS else 0),
                    )
                } else {
                    shrinkVertically()
                },
            ) {
                if (modern) {
                    FloatingNavigationBar(tabs)
                } else {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.fastForEach { NavigationBarItem(it) }
                    }
                }
            }
        }
    }

    @Composable
    private fun FloatingNavigationBar(tabs: List<eu.kanade.presentation.util.Tab>) {
        val shape = RoundedCornerShape(32.dp)
        val dark = LocalDarkTheme.current
        val barColors = if (dark) {
            listOf(Color(0xFF26080E), Color(0xFF490D19))
        } else {
            listOf(Color(0xFFFFFCF8), Color(0xFFFFF3E9))
        }
        Box(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().shadow(
                    elevation = 12.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.32f),
                    spotColor = Color.Black.copy(alpha = 0.22f),
                ),
                shape = shape,
                color = Color.Transparent,
                contentColor = if (dark) Color.White else Color(0xFF32251F),
                border = BorderStroke(
                    1.dp,
                    if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFFE4DAD2),
                ),
            ) {
                BoxWithConstraints {
                    val compact = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.3f
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Brush.horizontalGradient(barColors), shape)
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.fastForEach { FloatingNavigationItem(it, compact) }
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.FloatingNavigationItem(tab: eu.kanade.presentation.util.Tab, compact: Boolean) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val title = tab.options.title
        val motion = modernMotionEnabled()
        val dark = LocalDarkTheme.current
        val scale by animateFloatAsState(
            if (selected) 1.1f else 1f,
            tween(if (motion) 180 else 0),
            label = "floatingTabScale",
        )
        val contentColor by animateColorAsState(
            if (dark) {
                if (selected) Color.White else Color.White.copy(alpha = 0.68f)
            } else {
                if (selected) Color(0xFFB64008) else Color(0xFF44352D)
            },
            tween(if (motion) 180 else 0),
            label = "floatingTabColor",
        )
        Column(
            Modifier.weight(1f).heightIn(min = if (compact) 60.dp else 68.dp)
                .clip(RoundedCornerShape(27.dp))
                .clickable {
                    if (!selected) {
                        tabNavigator.current = tab
                    } else {
                        scope.launch { tab.onReselect(navigator) }
                    }
                }
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                    contentDescription = title
                }
                .testTag(navigationTag(tab)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    ) { NavigationIconItem(tab) }
                }
            }
            Text(
                title,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun RowScope.NavigationBarItem(tab: eu.kanade.presentation.util.Tab) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationBarItem(
            colors = if (LocalNyanimeStyle.current) {
                NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                NavigationBarItemDefaults.colors()
            },
            modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(navigationTag(tab)),
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = if (LocalNyanimeStyle.current) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    maxLines = if (tab == LibrariesTab) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
        )
    }

    @Composable
    fun NavigationRailItem(tab: eu.kanade.presentation.util.Tab) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationRailItem(
            colors = if (LocalNyanimeStyle.current) {
                NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = Color.Transparent,
                )
            } else {
                NavigationRailItemDefaults.colors()
            },
            modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(navigationTag(tab)),
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = if (LocalNyanimeStyle.current) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    maxLines = if (tab == LibrariesTab) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
        )
    }

    @Composable
    private fun NavigationIconItem(tab: eu.kanade.presentation.util.Tab) {
        BadgedBox(
            badge = {
                when {
                    UpdatesTab::class.isInstance(tab) ||
                        (
                            tab == eu.kanade.tachiyomi.ui.discovery.DiscoveryTab &&
                                uiPreferences.navStyle().get() == eu.kanade.domain.ui.model.NavStyle.DISCOVERY
                            ) -> {
                        val count by produceState(initialValue = 0) {
                            val pref = Injekt.get<LibraryPreferences>()
                            combine(
                                pref.newAnimeUpdatesCount().changes(),
                                pref.newMangaUpdatesCount().changes(),
                            ) { countAnime, countManga -> countAnime + countManga }
                                .collectLatest { value = if (pref.newShowUpdatesCount().get()) it else 0 }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.notification_chapters_generic,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                    BrowseTab::class.isInstance(tab) -> {
                        val count by produceState(initialValue = 0) {
                            val pref = Injekt.get<SourcePreferences>()
                            combine(
                                pref.mangaExtensionUpdatesCount().changes(),
                                pref.animeExtensionUpdatesCount().changes(),
                            ) { extCount, animeExtCount -> extCount + animeExtCount }
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.update_check_notification_ext_updates,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                }
            },
        ) {
            Icon(
                painter = tab.options.icon!!,
                contentDescription = tab.options.title,
            )
        }
    }

    private fun navigationTag(tab: cafe.adriel.voyager.navigator.tab.Tab) = when (tab) {
        eu.kanade.tachiyomi.ui.releases.ReleasesTab -> "releases"
        LibrariesTab -> "libraries"
        AnimeLibraryTab -> "library_anime"
        MangaLibraryTab -> "library_manga"
        BrowseTab -> "browse"
        MoreTab -> "more"
        eu.kanade.tachiyomi.ui.discovery.DiscoveryTab -> "discovery"
        else -> "navigation_other"
    }

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed interface Tab {
        data object Home : Tab
        data class AnimeLib(val animeIdToOpen: Long? = null) : Tab
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data object Releases : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false, val anime: Boolean = false) : Tab
        data class More(val toDownloads: Boolean) : Tab
    }
}

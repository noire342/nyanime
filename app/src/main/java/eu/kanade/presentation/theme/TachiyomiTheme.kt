package eu.kanade.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.NyanimeLogoColor
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CloudflareColorScheme
import eu.kanade.presentation.theme.colorscheme.CottoncandyColorScheme
import eu.kanade.presentation.theme.colorscheme.DoomColorScheme
import eu.kanade.presentation.theme.colorscheme.GreenAppleColorScheme
import eu.kanade.presentation.theme.colorscheme.LavenderColorScheme
import eu.kanade.presentation.theme.colorscheme.MatrixColorScheme
import eu.kanade.presentation.theme.colorscheme.MidnightDuskColorScheme
import eu.kanade.presentation.theme.colorscheme.MochaColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetColorScheme
import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme
import eu.kanade.presentation.theme.colorscheme.NordColorScheme
import eu.kanade.presentation.theme.colorscheme.NyanimeColorScheme
import eu.kanade.presentation.theme.colorscheme.SapphireColorScheme
import eu.kanade.presentation.theme.colorscheme.StrawberryColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.TealTurqoiseColorScheme
import eu.kanade.presentation.theme.colorscheme.TidalWaveColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val LocalNyanimeStyle = staticCompositionLocalOf { false }
internal val LocalMangaSurfaces = staticCompositionLocalOf<SnapshotStateMap<Any, Color>?> { null }

/** Manga retains the theme selected before the anime interface was redesigned. */
@Composable
fun LegacyMangaTheme(content: @Composable () -> Unit) {
    val preferences = Injekt.get<UiPreferences>()
    BaseTachiyomiTheme(preferences.legacyMangaTheme().get(), preferences.themeDarkAmoled().get(), modernUi = false) {
        val surfaces = LocalMangaSurfaces.current
        val surface = MaterialTheme.colorScheme.surface
        DisposableEffect(surfaces, surface) {
            val owner = Any()
            surfaces?.set(owner, surface)
            onDispose { surfaces?.remove(owner) }
        }
        content()
    }
}

@Composable
fun MangaSectionTheme(legacy: Boolean, content: @Composable () -> Unit) {
    if (legacy) LegacyMangaTheme(content) else content()
}

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val logoColor by uiPreferences.logoColor().collectAsState()
    CompositionLocalProvider(LocalNyanimeLogoColor provides logoColor) {
        BaseTachiyomiTheme(
            appTheme = appTheme ?: uiPreferences.activeAppTheme(),
            isAmoled = amoled ?: uiPreferences.themeDarkAmoled().get(),
            modernUi = appTheme?.let { it == AppTheme.NYANIME } ?: uiPreferences.modernUi().get(),
            content = content,
        )
    }
}

@Composable
fun TachiyomiPreviewTheme(
    appTheme: AppTheme = AppTheme.NYANIME,
    isAmoled: Boolean = false,
    modernUi: Boolean = appTheme == AppTheme.NYANIME,
    logoColor: NyanimeLogoColor = NyanimeLogoColor.RED,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNyanimeLogoColor provides logoColor) {
        BaseTachiyomiTheme(appTheme, isAmoled, modernUi, content)
    }
}

@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    modernUi: Boolean,
    content: @Composable () -> Unit,
) {
    val mangaSurfaces = LocalMangaSurfaces.current ?: remember { mutableStateMapOf() }
    CompositionLocalProvider(
        LocalNyanimeStyle provides modernUi,
        LocalMangaSurfaces provides mangaSurfaces,
    ) {
        MaterialTheme(
            colorScheme = getThemeColorScheme(appTheme, isAmoled),
            shapes = if (modernUi) NyanimeShapes else androidx.compose.material3.Shapes(),
            typography = if (modernUi) {
                NyanimeTypography
            } else {
                androidx.compose.material3.Typography()
            },
            content = content,
        )
    }
}

@Composable
@ReadOnlyComposable
private fun getThemeColorScheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
): ColorScheme {
    val colorScheme = if (appTheme == AppTheme.MONET) {
        MonetColorScheme(LocalContext.current)
    } else {
        colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
    }
    return colorScheme.getColorScheme(
        appTheme == AppTheme.NYANIME || isSystemInDarkTheme(),
        isAmoled,
    )
}

private const val RIPPLE_DRAGGED_ALPHA = .1f
private const val RIPPLE_FOCUSED_ALPHA = .1f
private const val RIPPLE_HOVERED_ALPHA = .1f
private const val RIPPLE_PRESSED_ALPHA = .1f

val playerRippleConfiguration
    @Composable get() = RippleConfiguration(
        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
        rippleAlpha = RippleAlpha(
            draggedAlpha = RIPPLE_DRAGGED_ALPHA,
            focusedAlpha = RIPPLE_FOCUSED_ALPHA,
            hoveredAlpha = RIPPLE_HOVERED_ALPHA,
            pressedAlpha = RIPPLE_PRESSED_ALPHA,
        ),
    )

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.NYANIME to NyanimeColorScheme,
    AppTheme.DEFAULT to TachiyomiColorScheme,
    AppTheme.CLOUDFLARE to CloudflareColorScheme,
    AppTheme.COTTONCANDY to CottoncandyColorScheme,
    AppTheme.DOOM to DoomColorScheme,
    AppTheme.GREEN_APPLE to GreenAppleColorScheme,
    AppTheme.LAVENDER to LavenderColorScheme,
    AppTheme.MATRIX to MatrixColorScheme,
    AppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
    AppTheme.MONOCHROME to MonochromeColorScheme,
    AppTheme.MOCHA to MochaColorScheme,
    AppTheme.SAPPHIRE to SapphireColorScheme,
    AppTheme.NORD to NordColorScheme,
    AppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
    AppTheme.TAKO to TakoColorScheme,
    AppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
    AppTheme.TIDAL_WAVE to TidalWaveColorScheme,
    AppTheme.YINYANG to YinYangColorScheme,
    AppTheme.YOTSUBA to YotsubaColorScheme,
)

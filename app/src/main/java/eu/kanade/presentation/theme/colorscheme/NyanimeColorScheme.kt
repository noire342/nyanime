package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Neutral cinema surfaces keep artwork prominent; red is reserved for emphasis. */
internal object NyanimeColorScheme : BaseColorScheme() {
    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFF3344),
        onPrimary = Color(0xFF230002),
        primaryContainer = Color(0xFFB20710),
        onPrimaryContainer = Color.White,
        inversePrimary = Color(0xFFB90D2C),
        secondary = Color(0xFFF5F5F5),
        onSecondary = Color(0xFF141414),
        secondaryContainer = Color(0xFF282828),
        onSecondaryContainer = Color(0xFFF5F5F5),
        tertiary = Color(0xFFFFC878),
        onTertiary = Color(0xFF302000),
        tertiaryContainer = Color(0xFF44351F),
        onTertiaryContainer = Color(0xFFFFDFAB),
        background = Color(0xFF080808),
        onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF080808),
        onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color(0xFF252525),
        onSurfaceVariant = Color(0xFFB9B9B9),
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFFE6E6EB),
        inverseOnSurface = Color(0xFF19191D),
        outline = Color(0xFF858585),
        outlineVariant = Color(0xFF343434),
        surfaceContainerLowest = Color(0xFF050505),
        surfaceContainerLow = Color(0xFF121212),
        surfaceContainer = Color(0xFF191919),
        surfaceContainerHigh = Color(0xFF232323),
        surfaceContainerHighest = Color(0xFF303030),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFB90D2C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDADF),
        onPrimaryContainer = Color(0xFF470012),
        secondary = Color(0xFF25252B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4E4EA),
        onSecondaryContainer = Color(0xFF19191F),
        tertiary = Color(0xFF755427),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDFAB),
        onTertiaryContainer = Color(0xFF302000),
        background = Color(0xFFFAFAFC),
        onBackground = Color(0xFF18181D),
        surface = Color(0xFFFAFAFC),
        onSurface = Color(0xFF18181D),
        surfaceVariant = Color(0xFFE8E8EF),
        onSurfaceVariant = Color(0xFF555560),
        surfaceTint = Color.Transparent,
        outline = Color(0xFF767681),
        outlineVariant = Color(0xFFD5D5DD),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF3F3F7),
        surfaceContainer = Color(0xFFEDEDF2),
        surfaceContainerHigh = Color(0xFFE6E6EC),
        surfaceContainerHighest = Color(0xFFDEDEE6),
    )
}

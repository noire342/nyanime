package eu.kanade.presentation.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.model.NyanimeLogoColor
import eu.kanade.tachiyomi.R

val LocalNyanimeLogoColor = staticCompositionLocalOf { NyanimeLogoColor.RED }

internal val NyanimeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val baseTypography = Typography()
internal val NyanimeTypography = Typography(
    headlineLarge = baseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = baseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = baseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = baseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = baseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun NyanimeWordmark(
    modifier: Modifier = Modifier,
    logoColor: NyanimeLogoColor = LocalNyanimeLogoColor.current,
    fontSize: TextUnit = 24.sp,
) {
    val brush = when (logoColor) {
        NyanimeLogoColor.RED -> SolidColor(Color(0xFFE50914))
        NyanimeLogoColor.SUN_YELLOW -> Brush.horizontalGradient(
            listOf(
                colorResource(R.color.nyanime_logo_light),
                colorResource(R.color.nyanime_logo_yellow),
                colorResource(R.color.nyanime_logo_gold),
            ),
        )
    }
    Text(
        "NYANIME",
        modifier,
        style = TextStyle(brush = brush),
        fontSize = fontSize,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
    )
}

@Composable
fun NyanimeLogoIcon(
    modifier: Modifier = Modifier,
    logoColor: NyanimeLogoColor = LocalNyanimeLogoColor.current,
) {
    Image(
        painter = painterResource(
            when (logoColor) {
                NyanimeLogoColor.RED -> R.drawable.ic_launcher_foreground
                NyanimeLogoColor.SUN_YELLOW -> R.drawable.ic_launcher_foreground_yellow
            },
        ),
        contentDescription = null,
        modifier = modifier,
    )
}

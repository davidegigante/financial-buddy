package com.davidegigante.spesesmart.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidegigante.spesesmart.R

// --- Palette: azzurro / verde acqua ---

private val Aqua10 = Color(0xFF00201E)
private val Aqua20 = Color(0xFF003733)
private val Aqua30 = Color(0xFF00504A)
private val Aqua40 = Color(0xFF006B64)
private val Aqua80 = Color(0xFF5EDBCD)
private val Aqua90 = Color(0xFFB4F1E8)
private val Aqua95 = Color(0xFFDCFAF5)

private val Sky30 = Color(0xFF004C6B)
private val Sky40 = Color(0xFF00658D)
private val Sky80 = Color(0xFF7DD0FF)
private val Sky90 = Color(0xFFC5E7FF)

private val LightColors = lightColorScheme(
    primary = Aqua40,
    onPrimary = Color.White,
    primaryContainer = Aqua90,
    onPrimaryContainer = Aqua10,
    secondary = Sky40,
    onSecondary = Color.White,
    secondaryContainer = Sky90,
    onSecondaryContainer = Color(0xFF001E2D),
    tertiary = Color(0xFF4A5C92),
    tertiaryContainer = Color(0xFFDBE1FF),
    background = Color(0xFFF3FAF9),
    onBackground = Color(0xFF151D1C),
    surface = Color(0xFFF3FAF9),
    onSurface = Color(0xFF151D1C),
    surfaceVariant = Color(0xFFDAE5E3),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEDF5F4),
    surfaceContainer = Color(0xFFE7F0EE),
    surfaceContainerHigh = Color(0xFFE1EAE8),
    surfaceContainerHighest = Color(0xFFDBE4E3),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    error = Color(0xFFC4323F),
    errorContainer = Color(0xFFFFDADA),
)

private val DarkColors = darkColorScheme(
    primary = Aqua80,
    onPrimary = Aqua20,
    primaryContainer = Aqua30,
    onPrimaryContainer = Aqua90,
    secondary = Sky80,
    onSecondary = Color(0xFF003549),
    secondaryContainer = Sky30,
    onSecondaryContainer = Sky90,
    tertiary = Color(0xFFB4C4FF),
    tertiaryContainer = Color(0xFF324478),
    background = Color(0xFF0D1514),
    onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF0D1514),
    onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFF080F0F),
    surfaceContainerLow = Color(0xFF151D1C),
    surfaceContainer = Color(0xFF192120),
    surfaceContainerHigh = Color(0xFF232B2A),
    surfaceContainerHighest = Color(0xFF2E3635),
    outline = Color(0xFF889391),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB3B3),
    errorContainer = Color(0xFF8C1D2A),
)

/** Colori in più rispetto a Material: stati del budget, gradiente della home, colori delle categorie. */
@Immutable
data class AppColors(
    val good: Color,
    val warning: Color,
    val danger: Color,
    val heroGradient: Brush,
    val onHero: Color,
    val categories: List<Color>,
) {
    /** Colore stabile per una categoria (null = senza categoria). */
    fun category(id: Long?): Color = if (id == null) categories.last() else categories[(id % (categories.size - 1)).toInt()]
}

private val CategoryPalette = listOf(
    Color(0xFF14B8A6), Color(0xFF0EA5E9), Color(0xFF6366F1), Color(0xFFF59E0B),
    Color(0xFFEC4899), Color(0xFF22C55E), Color(0xFF8B5CF6), Color(0xFFF97316),
    Color(0xFF06B6D4), Color(0xFFEF4444), Color(0xFF84CC16), Color(0xFF94A3B8),
)

private val LightAppColors = AppColors(
    good = Aqua40,
    warning = Color(0xFFD97706),
    danger = Color(0xFFC4323F),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF00897B), Color(0xFF0288D1))),
    onHero = Color.White,
    categories = CategoryPalette,
)

private val DarkAppColors = LightAppColors.copy(
    good = Aqua80,
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFFF8A8A),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF00695F), Color(0xFF01579B))),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Accesso comodo: AppTheme.colors.warning */
object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
}

// --- Font: Plus Jakarta Sans (variabile, incluso nell'app: niente download, niente internet) ---

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

/** Cifre tutte della stessa larghezza: gli importi non "ballano" quando cambiano. */
const val TABULAR = "tnum"

private val Base = Typography()

private fun TextStyle.j(weight: Int? = null) = copy(
    fontFamily = Jakarta,
    fontWeight = weight?.let { FontWeight(it) } ?: fontWeight,
)

private val AppTypography = Typography(
    displayLarge = Base.displayLarge.j(700),
    displayMedium = Base.displayMedium.j(700),
    displaySmall = Base.displaySmall.j(700).copy(letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.j(700),
    headlineMedium = Base.headlineMedium.j(700).copy(letterSpacing = (-0.25).sp),
    headlineSmall = Base.headlineSmall.j(700),
    titleLarge = Base.titleLarge.j(700),
    titleMedium = Base.titleMedium.j(600),
    titleSmall = Base.titleSmall.j(600),
    bodyLarge = Base.bodyLarge.j(500),
    bodyMedium = Base.bodyMedium.j(500),
    bodySmall = Base.bodySmall.j(500),
    labelLarge = Base.labelLarge.j(600),
    labelMedium = Base.labelMedium.j(600),
    labelSmall = Base.labelSmall.j(600),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SpeseSmartTheme(content: @Composable () -> Unit) {
    // Colori fissi dell'app (niente colori presi dallo sfondo del telefono).
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

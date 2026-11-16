package io.github.swashbuckler104.budgetwidget.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.model.CategoryPalette

/**
 * App theme: Material 3 in the brand palette ([BrandColors]), or the wallpaper's colors on
 * Android 12+ when the user turns them on (ADR-0051). Money colors ([MoneyColors]) are fixed hues,
 * tuned for light and dark, so income always reads green and spending red whatever the palette.
 */
@Composable
fun BudgetWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> BrandColors.dark

        else -> BrandColors.light
    }
    MaterialTheme(colorScheme = colorScheme, typography = BudgetTypography, shapes = BudgetShapes, content = content)
}

/** Income green and money-out red, readable on every surface role of either theme (ADR-0040). */
@Immutable
data class MoneyColors(
    val income: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val spend: Color,
    val spendContainer: Color,
    val onSpendContainer: Color,
) {
    companion object {
        val light = MoneyColors(
            income = Color(0xFF1B7A3D),
            incomeContainer = Color(0xFFC8F0CF),
            onIncomeContainer = Color(0xFF00210B),
            spend = Color(0xFFB3261E),
            spendContainer = Color(0xFFFFDAD5),
            onSpendContainer = Color(0xFF410001),
        )
        val dark = MoneyColors(
            income = Color(0xFF4BE38F),
            incomeContainer = Color(0xFF0C4A2A),
            onIncomeContainer = Color(0xFFBDF7D3),
            spend = Color(0xFFFF7B7B),
            spendContainer = Color(0xFF6E1B22),
            onSpendContainer = Color(0xFFFFDADA),
        )
    }
}

/** Shortcut: `BudgetTheme.money.income`. Follows the color scheme in use (light or dark surface). */
object BudgetTheme {
    val money: MoneyColors
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkSurface) MoneyColors.dark else MoneyColors.light

    /**
     * A category's stored color as drawn on the current surface: the bright shade on dark, a
     * darker shade of the same hue on light (see [CategoryPalette.forLightSurface]).
     */
    @Composable
    @ReadOnlyComposable
    fun categoryColor(argb: Int): Color = Color(if (isDarkSurface) argb else CategoryPalette.forLightSurface(argb))

    /**
     * The tile behind a category's icon: a deep shade of the category's own hue on dark, a pale
     * one on light (see [CategoryPalette.container]). Never a see-through tint, which turns muddy.
     */
    @Composable
    @ReadOnlyComposable
    fun categoryContainer(argb: Int): Color = Color(CategoryPalette.container(argb, isDarkSurface))

    /** The hairline that lifts a card off the background; tonal steps alone look flat on dark. */
    val cardBorder: BorderStroke
        @Composable
        @ReadOnlyComposable
        get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = CARD_BORDER_ALPHA))

    /**
     * Fill for the one headline card of a screen: the accent container fading into the surface.
     * Text on it uses `onPrimaryContainer` in dark and `onSurface`-safe tones in light alike.
     */
    val heroBrush: Brush
        @Composable
        @ReadOnlyComposable
        get() {
            val scheme = MaterialTheme.colorScheme
            return Brush.linearGradient(
                listOf(scheme.primaryContainer, lerp(scheme.primaryContainer, scheme.surface, HERO_FADE)),
            )
        }

    private val isDarkSurface: Boolean
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE

    private const val DARK_SURFACE = 0.5f
    private const val CARD_BORDER_ALPHA = 0.7f
    private const val HERO_FADE = 0.55f
}

/**
 * Brand palette "Midnight iris" (ADR-0051): ink-blue surfaces in clear steps, an electric iris
 * accent that is neither the income green nor the spending red, and amber as the second voice
 * (streaks, reference lines). The default look; wallpaper colors are opt-in.
 */
object BrandColors {
    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF3D4BD8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEE1FF),
        onPrimaryContainer = Color(0xFF0A1470),
        inversePrimary = Color(0xFFB4BDFF),
        secondary = Color(0xFF545C85),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDDE1FA),
        onSecondaryContainer = Color(0xFF131A3D),
        tertiary = Color(0xFF7A5800),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE08A),
        onTertiaryContainer = Color(0xFF261A00),
        background = Color(0xFFF6F7FC),
        onBackground = Color(0xFF161A26),
        surface = Color(0xFFF6F7FC),
        onSurface = Color(0xFF161A26),
        surfaceVariant = Color(0xFFE0E3F1),
        onSurfaceVariant = Color(0xFF444B61),
        surfaceTint = Color(0xFF3D4BD8),
        inverseSurface = Color(0xFF2B2F3D),
        inverseOnSurface = Color(0xFFEEF0FA),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF737A92),
        outlineVariant = Color(0xFFC4C9DB),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF6F7FC),
        surfaceDim = Color(0xFFD6D9E6),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F2FA),
        surfaceContainer = Color(0xFFEAEDF7),
        surfaceContainerHigh = Color(0xFFE3E7F3),
        surfaceContainerHighest = Color(0xFFDCE1EF),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFF9AA8FF),
        onPrimary = Color(0xFF0D1657),
        primaryContainer = Color(0xFF2A3690),
        onPrimaryContainer = Color(0xFFDFE2FF),
        inversePrimary = Color(0xFF3D4BD8),
        secondary = Color(0xFFBCC4EE),
        onSecondary = Color(0xFF1E2650),
        secondaryContainer = Color(0xFF283062),
        onSecondaryContainer = Color(0xFFDDE1FF),
        tertiary = Color(0xFFFFC857),
        onTertiary = Color(0xFF402D00),
        tertiaryContainer = Color(0xFF4F3A00),
        onTertiaryContainer = Color(0xFFFFE2A6),
        background = Color(0xFF0B0E17),
        onBackground = Color(0xFFE6E9F5),
        surface = Color(0xFF0B0E17),
        onSurface = Color(0xFFE6E9F5),
        surfaceVariant = Color(0xFF2C3348),
        onSurfaceVariant = Color(0xFFAEB5CC),
        surfaceTint = Color(0xFF9AA8FF),
        inverseSurface = Color(0xFFE6E9F5),
        inverseOnSurface = Color(0xFF2B2F3D),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF7A829C),
        outlineVariant = Color(0xFF2C3348),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF2E364C),
        surfaceDim = Color(0xFF0B0E17),
        surfaceContainerLowest = Color(0xFF070910),
        surfaceContainerLow = Color(0xFF111522),
        surfaceContainer = Color(0xFF161B2A),
        surfaceContainerHigh = Color(0xFF1D2335),
        surfaceContainerHighest = Color(0xFF262D42),
    )
}

/** Material 3 type scale; headlines a touch heavier so amounts and totals carry the screen. */
private val BudgetTypography: Typography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val BudgetShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Tabular (fixed-width) digits, so amounts line up in lists and don't jitter while they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

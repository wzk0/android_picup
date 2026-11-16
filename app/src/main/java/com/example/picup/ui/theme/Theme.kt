package com.example.picup.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.picup.data.ThemeChoice

/**
 * Card surfaces.
 *
 * Light theme: a faint primary wash *darker* than the page — the shelf plates
 * in the reference app work the same way, and white cards on a near-white page
 * had no edge. Dark theme: one step up from the near-black page, because a
 * darker card disappears completely.
 */
val ColorScheme.cardSurface: Color
    get() = if (surface.luminance() > 0.5f) {
        androidx.compose.ui.graphics.lerp(surface, primary, 0.08f)
    } else {
        surfaceContainer
    }

/** Chips, segmented tracks and thumbnail placeholders that sit on [cardSurface]. */
val ColorScheme.chipSurface: Color
    get() = if (surface.luminance() > 0.5f) surfaceContainerHighest else surfaceContainerHigh

/** Floating panels (snackbar, sheets) sit one step above the page in both themes. */
val ColorScheme.floatingSurface: Color
    get() = if (surface.luminance() > 0.5f) surfaceContainerLowest else surfaceContainerHigh

private val LightScheme: ColorScheme = lightColorScheme(
    // Fixed (non-dynamic) theme: a deep green, so the app has an identity of
    // its own instead of following the wallpaper's hue.
    primary = Color(0xFF246B50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF0E6),
    onPrimaryContainer = Color(0xFF20513C),
    secondary = Color(0xFF4F6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E8DB),
    onSecondaryContainer = Color(0xFF0C1F16),
    tertiary = Color(0xFF7C4E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA8),
    onTertiaryContainer = Color(0xFF271900),
    // The page is a soft neutral grey; cards are a faint primary wash *darker*
    // than it, so every row has an edge without an outline.
    background = Color(0xFFF3F5F4),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF3F5F4),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFE2E7E4),
    onSurfaceVariant = Color(0xFF49534E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FBFA),
    surfaceContainer = Color(0xFFF0F3F1),
    surfaceContainerHigh = Color(0xFFE8ECEA),
    surfaceContainerHighest = Color(0xFFE0E5E2),
    outline = Color(0xFF79847D),
    outlineVariant = Color(0xFFD5DBD7),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA3D6BB),
    onPrimary = Color(0xFF123527),
    primaryContainer = Color(0xFF2B4C3B),
    onPrimaryContainer = Color(0xFFDDF0E6),
    secondary = Color(0xFFB6CCBE),
    onSecondary = Color(0xFF223529),
    secondaryContainer = Color(0xFF384C3F),
    onSecondaryContainer = Color(0xFFD2E8DB),
    tertiary = Color(0xFFF2BE58),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5E4200),
    onTertiaryContainer = Color(0xFFFFDEA8),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE2E8E4),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE2E8E4),
    surfaceVariant = Color(0xFF434B47),
    onSurfaceVariant = Color(0xFFC2CAC5),
    surfaceContainerLowest = Color(0xFF0A0D0C),
    surfaceContainerLow = Color(0xFF161A18),
    surfaceContainer = Color(0xFF1E2421),
    surfaceContainerHigh = Color(0xFF272E2A),
    surfaceContainerHighest = Color(0xFF313A35),
    outline = Color(0xFF8C958F),
    outlineVariant = Color(0xFF3C4440),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val PicUpTypography: Typography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp,
        ),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PicUpTheme(
    theme: ThemeChoice = ThemeChoice.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val scheme = remember(dark, dynamicColor, context) {
        when {
            dark && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                dynamicDarkColorScheme(context)

            !dark && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                dynamicLightColorScheme(context)

            dark -> DarkScheme
            else -> LightScheme
        }
    }
    // Switching between light and dark interpolates instead of flashing a
    // whole new palette on top of the current screen.
    val animatedScheme = animatedColorScheme(scheme)
    MaterialExpressiveTheme(
        colorScheme = animatedScheme,
        typography = PicUpTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/**
 * Interpolates every role of [target] so a theme switch is a 320 ms crossfade
 * rather than a hard cut. Rapid retargets (tapping light/dark twice) are
 * handled by `updateTransition`, which always animates from the current value.
 */
@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val transition = updateTransition(target, label = "palette")
    return target.copy(
        primary = transition.color("primary") { it.primary },
        onPrimary = transition.color("onPrimary") { it.onPrimary },
        primaryContainer = transition.color("primaryContainer") { it.primaryContainer },
        onPrimaryContainer = transition.color("onPrimaryContainer") { it.onPrimaryContainer },
        inversePrimary = transition.color("inversePrimary") { it.inversePrimary },
        secondary = transition.color("secondary") { it.secondary },
        onSecondary = transition.color("onSecondary") { it.onSecondary },
        secondaryContainer = transition.color("secondaryContainer") { it.secondaryContainer },
        onSecondaryContainer = transition.color("onSecondaryContainer") { it.onSecondaryContainer },
        tertiary = transition.color("tertiary") { it.tertiary },
        onTertiary = transition.color("onTertiary") { it.onTertiary },
        tertiaryContainer = transition.color("tertiaryContainer") { it.tertiaryContainer },
        onTertiaryContainer = transition.color("onTertiaryContainer") { it.onTertiaryContainer },
        background = transition.color("background") { it.background },
        onBackground = transition.color("onBackground") { it.onBackground },
        surface = transition.color("surface") { it.surface },
        onSurface = transition.color("onSurface") { it.onSurface },
        surfaceVariant = transition.color("surfaceVariant") { it.surfaceVariant },
        onSurfaceVariant = transition.color("onSurfaceVariant") { it.onSurfaceVariant },
        surfaceTint = transition.color("surfaceTint") { it.surfaceTint },
        inverseSurface = transition.color("inverseSurface") { it.inverseSurface },
        inverseOnSurface = transition.color("inverseOnSurface") { it.inverseOnSurface },
        error = transition.color("error") { it.error },
        onError = transition.color("onError") { it.onError },
        errorContainer = transition.color("errorContainer") { it.errorContainer },
        onErrorContainer = transition.color("onErrorContainer") { it.onErrorContainer },
        outline = transition.color("outline") { it.outline },
        outlineVariant = transition.color("outlineVariant") { it.outlineVariant },
        scrim = transition.color("scrim") { it.scrim },
        surfaceBright = transition.color("surfaceBright") { it.surfaceBright },
        surfaceDim = transition.color("surfaceDim") { it.surfaceDim },
        surfaceContainerLowest = transition.color("surfaceContainerLowest") { it.surfaceContainerLowest },
        surfaceContainerLow = transition.color("surfaceContainerLow") { it.surfaceContainerLow },
        surfaceContainer = transition.color("surfaceContainer") { it.surfaceContainer },
        surfaceContainerHigh = transition.color("surfaceContainerHigh") { it.surfaceContainerHigh },
        surfaceContainerHighest = transition.color("surfaceContainerHighest") { it.surfaceContainerHighest },
    )
}

@Composable
private fun Transition<ColorScheme>.color(label: String, target: (ColorScheme) -> Color): Color {
    val animated by animateColor(
        transitionSpec = { tween(320, easing = FastOutSlowInEasing) },
        label = label,
        targetValueByState = { target(it) },
    )
    return animated
}

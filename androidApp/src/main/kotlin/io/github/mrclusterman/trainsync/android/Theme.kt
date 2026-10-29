package io.github.mrclusterman.trainsync.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Pulso (docs/DISENO_PULSO.md): tonos de bosque y piel, formas suaves; el color dice cómo estás. Mismos valores en iOS.
data class Colors(
    val bg: Color, val surface: Color, val chip: Color, val text: Color, val muted: Color,
    val ok: Color, val onOk: Color, val okSoft: Color, val band: Color,
    val caution: Color, val cautionSoft: Color, val danger: Color,
)

val DarkColors = Colors(
    bg = Color(0xFF0F1412), surface = Color(0xFF18201C), chip = Color(0xFF243029), text = Color(0xFFEEF2EE), muted = Color(0xFFA3B0A8),
    ok = Color(0xFF5FD3A4), onOk = Color(0xFF0B1F16), okSoft = Color(0xFF16302A), band = Color(0xFF1E4A3B),
    caution = Color(0xFFF2B95A), cautionSoft = Color(0xFF3A2E17), danger = Color(0xFFFF8A73),
)

val LightColors = Colors(
    bg = Color(0xFFF3F0E9), surface = Color(0xFFFFFFFF), chip = Color(0xFFECE7DC), text = Color(0xFF1B2420), muted = Color(0xFF5D6762),
    ok = Color(0xFF1F7A5C), onOk = Color(0xFFFFFFFF), okSoft = Color(0xFFDCEFE5), band = Color(0xFFCDE8DA),
    caution = Color(0xFF9A6400), cautionSoft = Color(0xFFF6E7C8), danger = Color(0xFFB3402A),
)

val LocalColors = staticCompositionLocalOf { DarkColors }

/** Acceso corto a los colores del modo actual. */
object Palette {
    val bg: Color @Composable @ReadOnlyComposable get() = LocalColors.current.bg
    val surface: Color @Composable @ReadOnlyComposable get() = LocalColors.current.surface
    val chip: Color @Composable @ReadOnlyComposable get() = LocalColors.current.chip
    val text: Color @Composable @ReadOnlyComposable get() = LocalColors.current.text
    val muted: Color @Composable @ReadOnlyComposable get() = LocalColors.current.muted
    val ok: Color @Composable @ReadOnlyComposable get() = LocalColors.current.ok
    val onOk: Color @Composable @ReadOnlyComposable get() = LocalColors.current.onOk
    val okSoft: Color @Composable @ReadOnlyComposable get() = LocalColors.current.okSoft
    val band: Color @Composable @ReadOnlyComposable get() = LocalColors.current.band
    val caution: Color @Composable @ReadOnlyComposable get() = LocalColors.current.caution
    val cautionSoft: Color @Composable @ReadOnlyComposable get() = LocalColors.current.cautionSoft
    val danger: Color @Composable @ReadOnlyComposable get() = LocalColors.current.danger

    // Las zonas no cambian con el modo: son el mismo código de color en la app, el reloj y la documentación
    val zones = mapOf("Z1" to Color(0xFF4ECDC4), "Z2" to Color(0xFF45B7D1), "Z3" to Color(0xFFFFA500),
        "Z4" to Color(0xFFFF6B35), "Z5" to Color(0xFFFF4757), "Z6" to Color(0xFFC0392B))
    val onZone = Color(0xFF0B1F22)
}

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal), Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semi_bold, FontWeight.SemiBold), Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extra_bold, FontWeight.ExtraBold),
)

val Mono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal), Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semi_bold, FontWeight.SemiBold),
)

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Figtree), displayMedium = displayMedium.copy(fontFamily = Figtree),
        displaySmall = displaySmall.copy(fontFamily = Figtree), headlineLarge = headlineLarge.copy(fontFamily = Figtree),
        headlineMedium = headlineMedium.copy(fontFamily = Figtree), headlineSmall = headlineSmall.copy(fontFamily = Figtree),
        titleLarge = titleLarge.copy(fontFamily = Figtree), titleMedium = titleMedium.copy(fontFamily = Figtree),
        titleSmall = titleSmall.copy(fontFamily = Figtree), bodyLarge = bodyLarge.copy(fontFamily = Figtree),
        bodyMedium = bodyMedium.copy(fontFamily = Figtree), bodySmall = bodySmall.copy(fontFamily = Figtree),
        labelLarge = labelLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontFamily = Figtree), labelSmall = labelSmall.copy(fontFamily = Figtree),
    )
}

@Composable
fun TrainSyncTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.chip, onSurfaceVariant = c.muted, surfaceContainer = c.surface, surfaceContainerLow = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.chip, surfaceContainerLowest = c.bg,
        primary = c.ok, onPrimary = c.onOk, primaryContainer = c.okSoft, onPrimaryContainer = c.text,
        secondary = c.ok, onSecondary = c.onOk, secondaryContainer = c.chip, onSecondaryContainer = c.text,
        tertiary = c.caution, error = c.danger, outline = c.muted, outlineVariant = c.chip,
        inverseSurface = c.text, inverseOnSurface = c.bg,
    )
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

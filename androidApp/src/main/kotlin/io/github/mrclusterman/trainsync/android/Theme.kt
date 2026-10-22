package io.github.mrclusterman.trainsync.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// Athletic Precision (docs/DISENO_ATHLETIC_PRECISION.md): oscuro, números en monoespaciada, color de zona constante
object Palette {
    val bg = Color(0xFF0A0A0F)
    val surface1 = Color(0xFF12121A)
    val surface2 = Color(0xFF1A1A26)
    val surface3 = Color(0xFF22223A)
    val accent = Color(0xFF3D7EFF)
    val bio = Color(0xFF00E5A0)
    val alert = Color(0xFFFF8C42)
    val danger = Color(0xFFFF4D6D)
    val text = Color(0xFFF0F0FF)
    val secondary = Color(0xFF8888AA)
    val zones = mapOf("Z1" to Color(0xFF4ECDC4), "Z2" to Color(0xFF45B7D1), "Z3" to Color(0xFFFFA500),
        "Z4" to Color(0xFFFF6B35), "Z5" to Color(0xFFFF4757), "Z6" to Color(0xFFC0392B))
    val sports = mapOf("running" to Color(0xFFFF6B35), "cycling" to Color(0xFF3D7EFF), "swimming_pool" to Color(0xFF45B7D1),
        "swimming_open" to Color(0xFF45B7D1), "walking" to Color(0xFF00E5A0), "strength" to Color(0xFFB388FF))
}

val Mono = FontFamily.Monospace

@Composable
fun TrainSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.bg, surface = Palette.surface1, surfaceVariant = Palette.surface2,
            primary = Palette.accent, onPrimary = Palette.bg,
            secondaryContainer = Color(0xFF1E3A70), onSecondaryContainer = Palette.text, secondary = Palette.bio, error = Palette.danger,
            onBackground = Palette.text, onSurface = Palette.text, onSurfaceVariant = Palette.secondary,
            outline = Palette.surface3, surfaceContainer = Palette.surface1, surfaceContainerHigh = Palette.surface2,
        ),
        content = content,
    )
}

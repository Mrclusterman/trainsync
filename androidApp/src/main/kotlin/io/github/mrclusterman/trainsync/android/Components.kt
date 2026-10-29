package io.github.mrclusterman.trainsync.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mrclusterman.trainsync.app.EVIDENCE_MEANING
import io.github.mrclusterman.trainsync.app.PlanItem
import io.github.mrclusterman.trainsync.app.Reason
import io.github.mrclusterman.trainsync.app.SPORT_LABEL

@Composable
fun Module(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.surface1)
            .then(if (accent != null) Modifier.border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp)) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) = Text(text, color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)

@Composable
fun Secondary(text: String, color: Color = Palette.secondary) = Text(text, color = color, fontSize = 14.sp)

@Composable
fun Pill(text: String, color: Color = Palette.accent) {
    Text(text, fontFamily = Mono, fontSize = 12.sp, color = Palette.text,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
fun SportDot(sport: String, size: Int = 10) =
    Box(Modifier.size(size.dp).clip(CircleShape).background(Palette.sports[sport] ?: Palette.accent))

/** Motivo con su etiqueta de evidencia: se toca para ver qué significa. */
@Composable
fun ReasonRow(reason: Reason, color: Color = Palette.alert) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(2.dp).padding(vertical = 2.dp).background(color).size(2.dp, 18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(reason.text, fontSize = 14.sp, color = Palette.text)
            reason.evidence?.let { tag ->
                Text(tag, fontSize = 11.sp, color = Palette.secondary,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Palette.surface2)
                        .clickable { open = !open }.padding(horizontal = 8.dp, vertical = 2.dp))
                if (open) Text("${EVIDENCE_MEANING[tag]} Fuentes en la documentación del motor.", fontSize = 12.sp,
                    color = Palette.secondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Una sesión con su nombre llano, cómo saber si vas bien y sus pasos; el nombre técnico al tocar. */
@Composable
fun SessionCard(item: PlanItem, done: Boolean, actions: @Composable ColumnScope.() -> Unit = {}) {
    var details by remember { mutableStateOf(false) }
    Module(accent = if (done) Palette.bio else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportDot(item.sport, 12)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (item.isStrength) item.title else "${SPORT_LABEL[item.sport]} · ${item.title}",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (item.chosen) Text("Elegida por ti", fontSize = 12.sp, color = Palette.accent)
            }
            Text("${item.durationMin}′", fontFamily = Mono, fontSize = 16.sp, color = Palette.text)
        }
        Secondary(item.hint)
        if (item.steps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (s in item.steps) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp, 18.dp).clip(RoundedCornerShape(3.dp)).background(Palette.zones[s.intensityZone] ?: Palette.accent))
                        Spacer(Modifier.width(8.dp))
                        val reps = if (s.reps > 1) "${s.reps} × " else ""
                        Text("$reps${s.meters?.let { "$it m" } ?: minutesText(s.minutes)} ${s.name}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        s.target?.let { Text(it, fontFamily = Mono, fontSize = 12.sp, color = Palette.secondary) }
                    }
                }
            }
        }
        if (item.exercises.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item.exercises.forEach { Text("• $it", fontSize = 14.sp) }
            }
        }
        TextButton(onClick = { details = !details }) { Text(if (details) "Menos detalles" else "Más detalles", fontSize = 13.sp) }
        if (details) {
            Secondary("Nombre técnico: ${item.technicalName}")
            item.notes?.let { Secondary(it) }
            if (item.steps.any { it.detail != null }) item.steps.mapNotNull { s -> s.detail?.let { "${s.name}: $it" } }.forEach { Secondary(it) }
        }
        if (done) Text("Hecha", color = Palette.bio, fontWeight = FontWeight.Medium)
        actions()
    }
}

fun minutesText(m: Double): String = when {
    m < 1 -> "${(m * 60).toInt()}″"
    m % 1.0 == 0.0 -> "${m.toInt()}′"
    else -> "${m.toInt()}′${((m % 1) * 60).toInt().toString().padStart(2, '0')}″"
}

@Composable
fun ChipRow(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

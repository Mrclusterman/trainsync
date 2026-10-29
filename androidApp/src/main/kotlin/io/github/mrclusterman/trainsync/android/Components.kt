package io.github.mrclusterman.trainsync.android

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mrclusterman.trainsync.app.EVIDENCE_MEANING
import io.github.mrclusterman.trainsync.app.PlanItem
import io.github.mrclusterman.trainsync.app.Reason
import io.github.mrclusterman.trainsync.app.SPORT_LABEL
import io.github.mrclusterman.trainsync.app.Tone
import io.github.mrclusterman.trainsync.app.WeekDot

/** Tarjeta base: superficie, radio 24 y relleno 18. `accent` la remarca (sesión hecha). */
@Composable
fun Module(modifier: Modifier = Modifier, accent: Color? = null, background: Color = Palette.surface,
           content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (accent != null) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Título de pantalla, con algo a la derecha si hace falta (selector, botón). */
@Composable
fun ScreenTitle(text: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            subtitle?.let { Text(it, color = Palette.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            Text(text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        trailing()
    }
}

@Composable
fun SectionTitle(text: String) = Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)

@Composable
fun Secondary(text: String, color: Color = Palette.muted) = Text(text, color = color, fontSize = 14.sp, lineHeight = 20.sp)

@Composable
fun Pill(text: String, color: Color = Palette.chip) {
    Text(text, fontFamily = Mono, fontSize = 12.sp, color = Palette.text,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp))
}

@DrawableRes
fun sportIcon(sport: String): Int = when (sport) {
    "running" -> R.drawable.ic_sport_running
    "cycling" -> R.drawable.ic_sport_cycling
    "swimming_pool", "swimming_open" -> R.drawable.ic_sport_swimming
    "walking" -> R.drawable.ic_sport_walking
    else -> R.drawable.ic_sport_strength
}

@Composable
fun Glyph(@DrawableRes id: Int, size: Dp = 22.dp, tint: Color = Palette.text, label: String? = null) =
    Icon(painterResource(id), contentDescription = label, tint = tint, modifier = Modifier.size(size))

/** Icono de deporte en su pastilla, como en la cabecera de la sesión. */
@Composable
fun SportBadge(sport: String, size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Palette.chip), contentAlignment = Alignment.Center) {
        Glyph(sportIcon(sport), size * 0.56f, label = SPORT_LABEL[sport])
    }
}

@Composable
fun ZoneChip(zone: String) {
    Box(Modifier.width(40.dp).height(24.dp).clip(RoundedCornerShape(999.dp)).background(Palette.zones[zone] ?: Palette.chip),
        contentAlignment = Alignment.Center) {
        Text(zone, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.onZone)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick, modifier.heightIn(min = 50.dp)) { Text(text, fontSize = 15.sp) }

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick, modifier.heightIn(min = 50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.chip, contentColor = Palette.text)) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

/** Cómo estás hoy: el color y el icono dicen bien, con cuidado o descanso; debajo, el porqué. */
@Composable
fun StateCard(tone: Tone, title: String, detail: List<Reason>) {
    val (bg, fg, icon) = when (tone) {
        Tone.OK -> Triple(Palette.okSoft, Palette.ok, R.drawable.ic_state_ok)
        Tone.CAUTION -> Triple(Palette.cautionSoft, Palette.caution, R.drawable.ic_state_caution)
        Tone.REST -> Triple(Palette.chip, Palette.muted, R.drawable.ic_state_rest)
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = if (detail.isEmpty()) Alignment.CenterVertically else Alignment.Top) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(fg), contentAlignment = Alignment.Center) {
            Glyph(icon, tint = if (tone == Tone.OK) Palette.onOk else Palette.bg)
        }
        Column(Modifier.weight(1f).padding(top = if (detail.isEmpty()) 0.dp else 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            detail.forEach { ReasonRow(it, fg) }
        }
    }
}

/** Motivo con su etiqueta de evidencia: se toca para ver qué significa. */
@Composable
fun ReasonRow(reason: Reason, color: Color = Palette.caution) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(top = 3.dp).size(3.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(reason.text, fontSize = 14.sp, lineHeight = 20.sp)
            reason.evidence?.let { tag ->
                Text(tag, fontSize = 11.sp, color = Palette.muted, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Palette.chip)
                        .clickable { open = !open }.padding(horizontal = 8.dp, vertical = 2.dp))
                if (open) Text("${EVIDENCE_MEANING[tag]} Fuentes en la documentación del motor.", fontSize = 12.sp,
                    color = Palette.muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Una sesión: deporte y duración, nombre llano, cómo saber si vas bien y sus pasos con su zona; el nombre técnico al tocar. */
@Composable
fun SessionCard(item: PlanItem, done: Boolean, actions: @Composable ColumnScope.() -> Unit = {}) {
    var details by remember { mutableStateOf(false) }
    Module(accent = if (done) Palette.ok else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportBadge(item.sport)
            Spacer(Modifier.width(10.dp))
            Text(if (item.chosen) "${SPORT_LABEL[item.sport]} · elegida por ti" else SPORT_LABEL[item.sport] ?: "",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted, modifier = Modifier.weight(1f))
            if (done) {
                Glyph(R.drawable.ic_check, 18.dp, Palette.ok)
                Spacer(Modifier.width(4.dp))
                Text("Hecha", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.ok)
                Spacer(Modifier.width(10.dp))
            }
            Text("${item.durationMin}", fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(" min", fontFamily = Mono, fontSize = 13.sp, color = Palette.muted)
        }
        Text(item.title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
        Secondary(item.hint)
        if (item.steps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in item.steps) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ZoneChip(s.intensityZone)
                        Column(Modifier.weight(1f)) {
                            Text(s.name.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            s.target?.let { Text(it, fontFamily = Mono, fontSize = 12.sp, color = Palette.muted) }
                        }
                        val reps = if (s.reps > 1) "${s.reps}×" else ""
                        Text("$reps${s.meters?.let { "$it m" } ?: minutesText(s.minutes)}", fontFamily = Mono, fontSize = 14.sp, color = Palette.muted)
                    }
                }
            }
        }
        if (item.exercises.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.exercises.forEach { Text(it, fontSize = 15.sp) }
            }
        }
        actions()
        TextButton(onClick = { details = !details }) { Text(if (details) "Menos detalles" else "Más detalles", fontSize = 13.sp) }
        if (details) {
            Secondary("Nombre técnico: ${item.technicalName}")
            item.notes?.let { Secondary(it) }
            if (item.steps.any { it.detail != null }) item.steps.mapNotNull { s -> s.detail?.let { "${s.name}: $it" } }.forEach { Secondary(it) }
        }
    }
}

/** La semana en círculos: relleno si entrenaste, anillo de color hoy. */
@Composable
fun WeekDots(days: List<WeekDot>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val state = when { d.done -> "hecho"; d.planned -> "planificado"; else -> "libre" }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics { contentDescription = "${d.letter}: $state" }) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(if (d.done) Palette.ok else Color.Transparent)
                    .border(2.dp, if (d.isToday) Palette.ok else Palette.chip, CircleShape), contentAlignment = Alignment.Center) {
                    if (d.planned && !d.done) Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.muted))
                }
                Text(d.letter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
            }
        }
    }
}

/** Un valor frente a tu rango normal: banda verde = tu rango, marca = el valor (ámbar si se sale hacia el lado malo). */
@Composable
fun RangeBar(value: Double, low: Double, high: Double, domainMin: Double, domainMax: Double, good: Boolean) {
    val span = (domainMax - domainMin).takeIf { it > 0 } ?: 1.0
    fun f(v: Double) = ((v - domainMin) / span).coerceIn(0.0, 1.0).toFloat()
    val mark = if (good) Palette.ok else Palette.caution
    BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(Palette.chip))
        Box(Modifier.offset(x = maxWidth * f(low)).width(maxWidth * (f(high) - f(low))).height(8.dp)
            .clip(RoundedCornerShape(99.dp)).background(Palette.band))
        Box(Modifier.offset(x = maxWidth * f(value) - 2.dp).size(4.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(mark))
    }
}

/** Barra de progreso fina hacia un objetivo (minutos OMS, días de fuerza). */
@Composable
fun ProgressLine(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(Palette.chip)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(99.dp)).background(Palette.ok))
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

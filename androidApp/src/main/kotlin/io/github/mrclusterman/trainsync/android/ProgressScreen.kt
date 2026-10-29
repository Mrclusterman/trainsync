package io.github.mrclusterman.trainsync.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mrclusterman.trainsync.app.Metric
import io.github.mrclusterman.trainsync.app.Point
import io.github.mrclusterman.trainsync.app.RecentActivity
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.dateLabel
import kotlin.math.roundToInt

/** Tu evolución: sueño, VFC, FC en reposo, VO2max, peso, pasos y forma, con tu rango normal (punto 8 de la revisión). */
@Composable
fun ProgressTab(app: TrainSyncApp, version: Int, openProfile: () -> Unit) {
    val screen = remember(version) { app.progress() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tu evolución", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (!screen.linked) {
            Module {
                Secondary("Para ver tu sueño, tu VFC, tu pulso en reposo o tu VO2max, conecta en Perfil Health Connect " +
                    "(los datos de tu reloj o tu báscula en este móvil) o tu cuenta de intervals.icu (gratis).")
                Button(onClick = openProfile) { Text("Ir a Perfil") }
            }
            return@Column
        }
        screen.status?.let { st ->
            Module(accent = if (st.label == "Sobrecarga" || st.label == "Perdiendo forma") Palette.alert else Palette.bio) {
                SectionTitle("Estado de entrenamiento")
                Text(st.label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Secondary(st.text)
            }
        }
        if (screen.predictions.isNotEmpty()) Module {
            SectionTitle("Tiempos previstos")
            screen.predictions.forEach { p ->
                Row {
                    Text(p.distance, modifier = Modifier.weight(1f))
                    Text(p.time, fontFamily = Mono)
                }
            }
            screen.predictionSource?.let { Secondary("Calculado con $it (fórmula de Daniels).") }
        }
        Module {
            Text("Minutos de actividad por semana", fontWeight = FontWeight.Bold)
            Secondary("La OMS recomienda al menos 150 minutos moderados (o 75 intensos) a la semana.")
            Bars(screen.weeklyMinutes, target = 150.0)
        }
        screen.metrics.forEach { MetricCard(it) }
        if (screen.recent.isNotEmpty()) Module {
            SectionTitle("Tus últimos entrenamientos")
            screen.recent.forEach { r -> RecentRow(r) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Un entrenamiento: al tocarlo, lo medido y los minutos en cada zona. */
@Composable
private fun RecentRow(r: RecentActivity) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportDot(r.sport)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, fontSize = 14.sp)
                Secondary(dateLabel(r.date))
            }
            Text("${r.minutes}′", fontFamily = Mono)
        }
        if (open) {
            r.detail?.let { Secondary(it) }
            if (r.zoneMinutes.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(10.dp).padding(top = 4.dp)) {
                    r.zoneMinutes.forEachIndexed { i, m ->
                        if (m > 0) Box(Modifier.weight(m.toFloat()).fillMaxHeight().background(Palette.zones["Z${minOf(i + 1, 6)}"] ?: Palette.accent))
                    }
                }
                Secondary(r.zoneMinutes.mapIndexedNotNull { i, m -> if (m > 0) "Z${i + 1} $m′" else null }.joinToString(" · "))
            }
        }
    }
}

@Composable
private fun MetricCard(m: Metric) {
    var open by remember { mutableStateOf(false) }
    Module(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(m.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(format(m.latest) + if (m.unit.isNotEmpty()) " ${m.unit}" else "", fontFamily = Mono, fontSize = 22.sp)
        }
        if (m.normalLow != null && m.normalHigh != null) {
            val latest = m.latest ?: 0.0
            val inRange = latest in m.normalLow!!..m.normalHigh!!
            Secondary("Tu rango normal: ${format(m.normalLow)}–${format(m.normalHigh)}" + if (!inRange) " · hoy fuera de tu rango" else "",
                if (inRange) Palette.secondary else Palette.alert)
        }
        Sparkline(m.points, m.normalLow, m.normalHigh)
        if (open) Secondary(m.explanation)
    }
}

private fun format(v: Double?): String = when {
    v == null -> "—"
    v >= 100 -> v.roundToInt().toString()
    else -> ((v * 10).roundToInt() / 10.0).toString()
}

@Composable
private fun Sparkline(points: List<Point>, low: Double?, high: Double?) {
    if (points.size < 2) return
    val values = points.map { it.value }
    val min = minOf(values.min(), low ?: values.min())
    val max = maxOf(values.max(), high ?: values.max())
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (size.height * (1 - (v - min) / span)).toFloat()
        if (low != null && high != null) {
            drawRect(Palette.bio.copy(alpha = 0.10f), Offset(0f, y(high)), Size(size.width, y(low) - y(high)))
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, Palette.accent, style = Stroke(width = 4f))
    }
}

@Composable
private fun Bars(points: List<Point>, target: Double) {
    val max = maxOf(points.maxOfOrNull { it.value } ?: 0.0, target * 1.2)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val w = size.width / points.size
        points.forEachIndexed { i, p ->
            val h = (size.height * p.value / max).toFloat()
            drawRect(if (p.value >= target) Palette.bio else Palette.accent.copy(alpha = 0.6f),
                Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h))
        }
        val ty = (size.height * (1 - target / max)).toFloat()
        drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, ty), Offset(size.width, ty), strokeWidth = 2f)
    }
}

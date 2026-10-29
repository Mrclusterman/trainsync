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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mrclusterman.trainsync.app.Metric
import io.github.mrclusterman.trainsync.app.Point
import io.github.mrclusterman.trainsync.app.RecentActivity
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.dateLabel
import kotlin.math.roundToInt

private val BODY = listOf("sleep", "sleepScore", "hrv", "restingHR", "weight", "steps")

/** Tu evolución: cómo está tu cuerpo frente a tu rango normal, tu forma y tu actividad (punto 8 de la revisión). */
@Composable
fun ProgressTab(app: TrainSyncApp, version: Int, openProfile: () -> Unit) {
    val screen = remember(version) { app.progress() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Progreso", subtitle = "Últimos 90 días")
        if (!screen.linked) {
            Module {
                Secondary("Para ver tu sueño, tu VFC, tu pulso en reposo o tu VO2max, conecta en Perfil Health Connect " +
                    "(los datos de tu reloj o tu báscula en este móvil) o tu cuenta de intervals.icu (gratis).")
                PrimaryButton("Ir a Perfil", onClick = openProfile)
            }
            return@Column
        }
        val body = screen.metrics.filter { it.key in BODY }
        if (body.isNotEmpty()) Module {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionTitle("Tu cuerpo")
                Spacer(Modifier.weight(1f))
                Text("último dato · banda = tu rango", fontSize = 12.sp, color = Palette.muted)
            }
            body.forEachIndexed { i, m -> MetricRow(m, first = i == 0) }
        }
        val training = screen.metrics.filter { it.key !in BODY }
        if (screen.status != null || training.isNotEmpty()) Module {
            screen.status?.let { st ->
                val warn = st.label == "Sobrecarga" || st.label == "Perdiendo forma"
                SectionTitle("Forma: ${st.label.lowercase()}")
                screen.metrics.firstOrNull { it.key == "ctl" }?.let { Sparkline(it.points, null, null, if (warn) Palette.caution else Palette.ok) }
                Secondary(st.text)
            }
            training.forEachIndexed { i, m -> MetricRow(m, first = i == 0 && screen.status == null) }
        }
        Module {
            val last = screen.weeklyMinutes.lastOrNull()?.value ?: 0.0
            Row(verticalAlignment = Alignment.Bottom) {
                SectionTitle("Actividad semanal")
                Spacer(Modifier.weight(1f))
                Text("${last.roundToInt()} / 150 min", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            ProgressLine((last / 150).toFloat())
            Secondary("La OMS recomienda al menos 150 minutos moderados (o 75 intensos) a la semana.")
            Bars(screen.weeklyMinutes, target = 150.0)
        }
        if (screen.predictions.isNotEmpty()) Module {
            SectionTitle("Tiempos previstos")
            screen.predictions.forEach { p ->
                Row {
                    Text(p.distance, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(p.time, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            screen.predictionSource?.let { Secondary("Calculado con $it (fórmula de Daniels).") }
        }
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
    Column(Modifier.clickable { open = !open }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportBadge(r.sport, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(dateLabel(r.date), fontSize = 12.sp, color = Palette.muted)
            }
            Text("${r.minutes}′", fontFamily = Mono, fontSize = 15.sp)
        }
        if (open) {
            r.detail?.let { Secondary(it) }
            if (r.zoneMinutes.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp))) {
                    r.zoneMinutes.forEachIndexed { i, m ->
                        if (m > 0) Box(Modifier.weight(m.toFloat()).fillMaxHeight().background(Palette.zones["Z${minOf(i + 1, 6)}"] ?: Palette.ok))
                    }
                }
                Secondary(r.zoneMinutes.mapIndexedNotNull { i, m -> if (m > 0) "Z${i + 1} $m′" else null }.joinToString(" · "))
            }
        }
    }
}

/** Un indicador: valor, barra frente a tu rango y una frase; al tocarlo, su evolución y qué significa. */
@Composable
private fun MetricRow(m: Metric, first: Boolean) {
    var open by remember { mutableStateOf(false) }
    val low = m.normalLow
    val high = m.normalHigh
    val latest = m.latest
    Column(Modifier.fillMaxWidth().clickable { open = !open }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.chip))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = if (first) 0.dp else 6.dp)) {
            Text(m.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(format(latest) + if (m.unit.isNotEmpty()) " ${m.unit}" else "", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (low != null && high != null && latest != null && format(high) != format(low)) {
            val values = m.points.map { it.value }
            val lo = minOf(low, values.min())
            val hi = maxOf(high, values.max())
            val pad = (hi - lo) * 0.15
            val inRange = latest in low..high
            // Fuera de rango hacia el lado bueno (más VO2max, menos pulso en reposo) no es un aviso
            val good = inRange || (m.higherIsBetter == true && latest > high) || (m.higherIsBetter == false && latest < low)
            RangeBar(latest, low, high, lo - pad, hi + pad, good)
            val range = if (low < 0) "${format(low)} a ${format(high)}" else "${format(low)}–${format(high)}"
            Text(
                when {
                    inRange -> "Dentro de tu rango ($range)"
                    latest > high -> "Por encima de lo tuyo ($range)"
                    else -> "Por debajo de lo tuyo ($range)"
                },
                fontSize = 12.sp, color = if (good) Palette.muted else Palette.caution,
            )
        }
        if (open) {
            Sparkline(m.points, low, high, Palette.ok)
            Secondary(m.explanation)
        }
    }
}

private fun format(v: Double?): String = when {
    v == null -> "—"
    v >= 20 -> v.roundToInt().toString()
    else -> ((v * 10).roundToInt() / 10.0).toString().replace('.', ',')
}

@Composable
private fun Sparkline(points: List<Point>, low: Double?, high: Double?, color: Color) {
    if (points.size < 2) return
    val band = Palette.band
    val values = points.map { it.value }
    val min = minOf(values.min(), low ?: values.min())
    val max = maxOf(values.max(), high ?: values.max())
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (size.height * (1 - (v - min) / span)).toFloat()
        if (low != null && high != null) {
            drawRect(band, Offset(0f, y(high)), Size(size.width, y(low) - y(high)))
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun Bars(points: List<Point>, target: Double) {
    val max = maxOf(points.maxOfOrNull { it.value } ?: 0.0, target * 1.2)
    val ok = Palette.ok
    val under = Palette.chip
    val line = Palette.muted
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val w = size.width / points.size
        points.forEachIndexed { i, p ->
            val h = (size.height * p.value / max).toFloat()
            drawRoundRect(if (p.value >= target) ok else under, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h),
                CornerRadius(6f, 6f))
        }
        val ty = (size.height * (1 - target / max)).toFloat()
        drawLine(line, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    }
}

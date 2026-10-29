package io.github.mrclusterman.trainsync.app

// Lo que muestra cada apartado, calculado una vez para Android e iOS (docs/VISION.md §5): las dos apps solo pintan.

import io.github.mrclusterman.trainsync.engine.plusDays
import io.github.mrclusterman.trainsync.engine.RACE_DISTANCES
import io.github.mrclusterman.trainsync.engine.danielsTime
import io.github.mrclusterman.trainsync.engine.fmt
import io.github.mrclusterman.trainsync.engine.pyRound
import io.github.mrclusterman.trainsync.engine.trainingStatus
import io.github.mrclusterman.trainsync.engine.vdot
import io.github.mrclusterman.trainsync.engine.weekday
import io.github.mrclusterman.trainsync.intervals.HEALTH_ACTIVITY_SPORTS
import io.github.mrclusterman.trainsync.intervals.date
import io.github.mrclusterman.trainsync.intervals.num
import io.github.mrclusterman.trainsync.intervals.nums
import io.github.mrclusterman.trainsync.intervals.str
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlin.math.sqrt

/** "Viernes 23 de octubre". */
fun dateLabel(date: String): String {
    val d = LocalDate.parse(date)
    return "${WEEKDAY_LABEL[d.weekday()]} ${d.day} de ${MONTH_LABEL[d.month.number - 1]}"
}

// ---------------------------------------------------------------- Hoy

/** Color de la tarjeta de estado: bien, con cuidado (ajuste o mucha fatiga) o descanso. */
enum class Tone { OK, CAUTION, REST }

/** Un día de la semana en Hoy: hecho, planificado o libre. */
data class WeekDot(val letter: String, val date: String, val isToday: Boolean, val planned: Boolean, val done: Boolean)

data class TodayScreen(
    val dateLabel: String,
    val goalLine: String?,  // "Maratón de Valencia · en 6 semanas · Base" o "Bloque 2 · semana 3 de 4"
    val headline: String,  // tu cuerpo hoy en una frase, salida de la misma decisión que la sesión
    val headlineDetail: List<Reason>,
    val stateTitle: String,  // la tarjeta de estado: cómo estás ("Estás en tu punto normal.") o el ajuste de hoy
    val stateDetail: List<Reason>,
    val tone: Tone,
    val day: DayView,
    val weekLine: String,  // "Esta semana: 80 min de actividad y fuerza 2 días"
    val meetsWho: Boolean,
    val weekMinutes: Int,
    val strengthDays: Int,
    val weekDots: List<WeekDot>,
    val warnings: List<String>,
    val info: List<String>,
    val askReadiness: Boolean,  // sin reloj vinculado: tres preguntas para ajustar el día
    val linked: Boolean,
    val goalFinished: Boolean,  // la carrera ya pasó: toca elegir objetivo otra vez
)

fun buildToday(data: AppData, today: LocalDate): TodayScreen {
    val planner = Planner(data, today)
    val week = planner.week(mondayOf(today))
    val day = week.days.first { it.isToday }
    val race = data.goal?.race?.takeIf { data.goal.kind == GoalKind.RACE }
    val goalFinished = race != null && LocalDate.parse(race.date) < today
    val goalLine = when {
        data.goal == null -> null
        race != null && !goalFinished -> {
            val days = today.daysUntil(LocalDate.parse(race.date))
            val left = when {
                days == 0 -> "hoy"
                days < 14 -> "en $days días"
                else -> "en ${days / 7} semanas"
            }
            listOfNotNull(race.name, left, planner.racePhase()?.let { Planner.PHASE_LABEL[it] }).joinToString(" · ")
        }
        week.block != null -> "${goalLabel(data.goal)} · bloque ${week.block + 1}, semana ${week.weekInBlock!! + 1} de 4" +
            if (week.weekInBlock == 3) " (más suave)" else ""
        else -> goalLabel(data.goal)
    }
    val (headline, detail) = headline(day, planner)
    val form = detail.firstOrNull()?.takeIf { it.evidence == null && day.adjustments.isEmpty() && day.items.isNotEmpty() }
    val tone = when {
        day.adjustments.isNotEmpty() || form?.text == HEAVY_FATIGUE -> Tone.CAUTION
        day.items.isEmpty() -> Tone.REST
        else -> Tone.OK
    }
    val strength = week.strengthDays
    return TodayScreen(
        dateLabel = dateLabel(today.toString()),
        goalLine = goalLine, headline = headline, headlineDetail = detail, day = day,
        stateTitle = form?.text ?: headline, stateDetail = if (form != null) detail - form else detail, tone = tone,
        weekMinutes = week.activeMinutes, strengthDays = strength,
        weekDots = week.days.map { d ->
            WeekDot(WEEKDAY_SHORT[d.weekday] ?: d.weekday.take(1), d.date, d.isToday, d.items.isNotEmpty(), d.doneSports.isNotEmpty())
        },
        weekLine = "Esta semana: ${week.activeMinutes} min de actividad y fuerza $strength ${if (strength == 1) "día" else "días"}",
        meetsWho = week.activeMinutes >= 150 && strength >= 2,
        warnings = week.warnings, info = week.info,
        askReadiness = !planner.hasRecoverySignals && data.readiness[today.toString()] == null && day.items.isNotEmpty(),
        linked = data.cache != null, goalFinished = goalFinished,
    )
}

private const val HEAVY_FATIGUE = "Llevas mucha fatiga acumulada."

/** La frase de arriba sale de lo mismo que decide la sesión: nunca "recuperación" arriba y "ritmo cómodo" abajo. */
private fun headline(day: DayView, planner: Planner): Pair<String, List<Reason>> {
    if (day.adjustments.isNotEmpty()) {
        val main = day.items.firstOrNull { !it.isStrength }
        return (if (main != null) "Hoy toca bajar el ritmo: ${main.title.lowercase()}" else "Hoy toca descansar") to day.adjustments
    }
    if (day.items.isEmpty()) {
        return "Hoy descansas" to (day.note?.let { listOf(Reason(it, "Práctica")) } ?: listOf(
            Reason("Recuperar también es entrenar: el cuerpo se adapta en los días sin carga.", "Práctica")))
    }
    val main = day.items.firstOrNull { !it.isStrength } ?: day.items.first()
    val form = planner.state?.let { s ->
        val tsb = s.tsb
        when {
            tsb > 5 -> "Estás descansado."
            tsb >= -10 -> "Estás en tu punto normal."
            tsb >= -30 -> "Llevas carga acumulada: es normal cuando estás construyendo."
            else -> HEAVY_FATIGUE
        }
    }
    val text = "Hoy: ${main.title.lowercase()}, ${main.durationMin} min" + if (day.items.size > 1) " y fuerza" else ""
    return text to (listOfNotNull(form?.let { Reason(it, null) }) + day.reasons)
}

// ---------------------------------------------------------------- Calendario

data class DoneActivity(val title: String, val sport: String, val minutes: Int, val start: String?)

data class CalendarDay(
    val date: String,
    val dayOfMonth: Int,
    val inMonth: Boolean,
    val isToday: Boolean,
    val planned: List<PlanItem>,
    val done: List<DoneActivity>,
    val doneSports: Set<String>,
    val manual: List<String>,  // deportes marcados a mano
)

data class MonthScreen(val title: String, val year: Int, val month: Int, val weeks: List<List<CalendarDay>>)

fun buildMonth(data: AppData, today: LocalDate, year: Int, month: Int): MonthScreen {
    val planner = Planner(data, today)
    val first = LocalDate(year, month, 1)
    var monday = mondayOf(first)
    val activities = data.deviceData(today)?.activities ?: emptyList()
    val weeks = mutableListOf<List<CalendarDay>>()
    while (monday.month.number == month || monday < first) {
        val week = planner.week(monday)
        weeks += week.days.map { d ->
            val date = LocalDate.parse(d.date)
            CalendarDay(
                date = d.date, dayOfMonth = date.day, inMonth = date.month.number == month, isToday = d.isToday,
                // Antes de elegir el objetivo no había plan: solo se muestra lo que hiciste
                planned = if (d.date < (data.goal?.since ?: "")) emptyList() else d.items, doneSports = d.doneSports,
                done = activities.filter { it.date() == d.date && HEALTH_ACTIVITY_SPORTS[it["type"].str()] != null }
                    .map { a ->
                        DoneActivity(title = activityTitle(a) ?: a["name"].str() ?: "Actividad",
                            sport = HEALTH_ACTIVITY_SPORTS[a["type"].str()]!!,
                            minutes = ((a["moving_time"].num() ?: 0.0) / 60).toInt(),
                            start = a["start_date_local"].str()?.drop(11)?.take(5))
                    },
                manual = data.manualDone.filter { it.date == d.date }.map { it.sport },
            )
        }
        monday = monday.plusDays(7)
    }
    return MonthScreen("${MONTH_LABEL[month - 1].replaceFirstChar { it.uppercase() }} $year", year, month, weeks)
}

// ---------------------------------------------------------------- Progreso

data class Point(val date: String, val value: Double)

data class Metric(
    val key: String,
    val label: String,
    val unit: String,
    val explanation: String,
    val points: List<Point>,
    val latest: Double?,
    val normalLow: Double?,  // tu rango normal: media ± 1 DT de los últimos 60 días con dato
    val normalHigh: Double?,
    val higherIsBetter: Boolean?,
)

/** Estado de entrenamiento: una palabra y qué significa. */
data class StatusView(val label: String, val text: String)

/** Tiempo previsto en una distancia, y de dónde sale. */
data class Prediction(val distance: String, val time: String)

data class RecentActivity(
    val date: String,
    val title: String,
    val sport: String,
    val minutes: Int,
    val load: Int?,
    val detail: String? = null,  // "10,2 km · 5:12 /km · 148 ppm · carga 62"
    val zoneMinutes: List<Int> = emptyList(),  // minutos en Z1–Z7 (de intervals.icu o del móvil)
)

/** Una línea con lo medido: distancia, ritmo (a pie, /km) o velocidad (bici, km/h), pulso medio y carga. */
internal fun activityDetail(sport: String, meters: Double?, seconds: Double?, avgHr: Double?, load: Double?): String? {
    val parts = mutableListOf<String>()
    if (meters != null && meters > 0) {
        parts += "${fmt(meters / 1000, 1).replace('.', ',')} km"
        if (seconds != null && seconds > 0) {
            parts += if (sport == "cycling") "${fmt(meters / seconds * 3.6, 1).replace('.', ',')} km/h"
            else if (sport == "swimming_pool" || sport == "swimming_open") "${raceTime(seconds / 60 / (meters / 100))} /100 m"
            else "${raceTime(seconds / 60 / (meters / 1000))} /km"
        }
    }
    avgHr?.let { parts += "${pyRound(it)} ppm" }
    load?.let { parts += "carga ${pyRound(it)}" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

data class ProgressScreen(
    val linked: Boolean,
    val metrics: List<Metric>,
    val weeklyMinutes: List<Point>,
    val status: StatusView? = null,
    val predictions: List<Prediction> = emptyList(),
    val predictionSource: String? = null,  // "tu VO2max (52)" o "tu mejor carrera de entrenamiento"
    val recent: List<RecentActivity> = emptyList(),
)

/** h:mm:ss o mm:ss. */
fun raceTime(minutes: Double): String {
    val total = pyRound(minutes * 60)
    val (h, m, s) = Triple(total / 3600, total % 3600 / 60, total % 60)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}

fun buildProgress(data: AppData, today: LocalDate, days: Int = 90): ProgressScreen {
    val cache = data.deviceData(today) ?: return ProgressScreen(false, emptyList(), emptyList())
    val since = today.plusDays(-days).toString()
    val wellness = cache.wellness.filter { (it["id"].str() ?: "") >= since }.sortedBy { it["id"].str() }
    fun series(key: String, label: String, unit: String, explanation: String, better: Boolean?,
               value: (kotlinx.serialization.json.JsonObject) -> Double?): Metric? {
        val points = wellness.mapNotNull { w -> value(w)?.takeIf { it != 0.0 }?.let { Point(w["id"].str()!!, it) } }
        if (points.isEmpty()) return null
        val recent = points.takeLast(60).map { it.value }
        val mean = recent.average()
        val sd = if (recent.size > 1) sqrt(recent.sumOf { (it - mean) * (it - mean) } / (recent.size - 1)) else 0.0
        return Metric(key, label, unit, explanation, points, points.last().value,
            if (recent.size >= 7) mean - sd else null, if (recent.size >= 7) mean + sd else null, better)
    }
    val metrics = listOfNotNull(
        series("sleep", "Sueño", "h", "Horas dormidas cada noche, según tu reloj.", true) { w -> w["sleepSecs"].num()?.let { it / 3600 } },
        series("sleepScore", "Calidad del sueño", "/100", "Puntuación del sueño de tu reloj.", true) { it["sleepScore"].num() },
        series("hrv", "VFC", "ms", GLOSSARY["VFC"]!!, true) { it["hrv"].num() },
        series("restingHR", "FC en reposo", "ppm", GLOSSARY["FC en reposo"]!!, false) { it["restingHR"].num() },
        series("vo2max", "VO2max", "", GLOSSARY["VO2max"]!!, true) { it["vo2max"].num() },
        series("weight", "Peso", "kg", "Tu peso, de la báscula o del reloj.", null) { it["weight"].num() },
        series("steps", "Pasos", "", "Pasos al día.", true) { it["steps"].num() },
        series("ctl", "Forma física", "", GLOSSARY["Forma física"]!!, true) { it["ctl"].num() },
        series("tsb", "Forma", "", GLOSSARY["Forma"]!!, null) { w ->
            val ctl = w["ctl"].num(); val atl = w["atl"].num()
            if (ctl != null && atl != null) ctl - atl else null
        },
    )
    // Minutos moderados equivalentes por semana (OMS): Z1 no cuenta, Z2–Z3 ×1, Z4 o más ×2 (zonas de intervals.icu); sin
    // zonas (entrenamientos del móvil sin pulso), el tiempo entero cuenta como moderado [Inferencia]
    val weekly = (0 until days / 7).reversed().map { w ->
        val monday = mondayOf(today).plusDays(-7 * w)
        val end = monday.plusDays(7).toString()
        val minutes = cache.activities.filter { it.date() >= monday.toString() && it.date() < end }.sumOf { a ->
            val z = a["icu_hr_zone_times"].nums()
            if (z.size < 7) (a["moving_time"].num() ?: 0.0) / 60 else (z[1] + z[2] + 2 * (z[3] + z[4] + z[5] + z[6])) / 60
        }
        Point(monday.toString(), minutes)
    }

    // Estado de entrenamiento: carga de esta semana frente a la habitual y forma de hoy frente a la de hace 4 semanas
    val withLoad = cache.wellness.filter { it["ctl"].num() != null }.sortedBy { it["id"].str() }
    val status = withLoad.lastOrNull()?.let { last ->
        val ago = today.plusDays(-28).toString()
        val ctl28 = withLoad.lastOrNull { (it["id"].str() ?: "") <= ago }?.get("ctl").num()
        val vo2 = wellness.mapNotNull { w -> w["vo2max"].num()?.takeIf { it > 0 }?.let { w["id"].str()!! to it } }
        val vo2Trend = vo2.lastOrNull()?.let { (_, now) -> vo2.lastOrNull { it.first <= ago }?.second?.let { now - it } }
        val (label, text) = trainingStatus(last["ctl"].num()!!, ctl28, last["atl"].num() ?: 0.0, vo2Trend)
        StatusView(label, text)
    }

    // Tiempos previstos (Daniels y Gilbert 1979): con el VO2max del reloj de los últimos 30 días; si no, con el VDOT de tu
    // mejor carrera de 3 km o más en 90 días (un entrenamiento no es a tope: la previsión queda por lo alto)
    val recentVo2 = wellness.lastOrNull { (it["id"].str() ?: "") >= today.plusDays(-30).toString() && (it["vo2max"].num() ?: 0.0) > 0 }
        ?.get("vo2max").num()
    val bestRun = cache.activities.filter { it["type"].str() == "Run" && (it["distance"].num() ?: 0.0) >= 3000 &&
        (it["moving_time"].num() ?: 0.0) >= 12 * 60 && it.date() >= since }
        .maxOfOrNull { vdot(it["distance"].num()!!, it["moving_time"].num()!! / 60) }?.takeIf { it in 20.0..85.0 }
    val (vo2, source) = when {
        recentVo2 != null -> recentVo2 to "tu VO2max (${fmt(recentVo2, 0)}), estimado por tu reloj"
        bestRun != null -> bestRun to "tu mejor carrera de entrenamiento de los últimos 90 días; con una carrera a tope sale más preciso"
        else -> null to null
    }
    val predictions = vo2?.let { v -> RACE_DISTANCES.map { (name, m) -> Prediction(name, raceTime(danielsTime(v, m))) } } ?: emptyList()

    val recent = cache.activities.filter { !it["start_date_local"].str().isNullOrEmpty() }
        .sortedByDescending { it["start_date_local"].str() }.take(10).map { a ->
            val sport = HEALTH_ACTIVITY_SPORTS[a["type"].str()] ?: "other"
            RecentActivity(a.date(), a["name"].str()?.takeIf { it.isNotBlank() } ?: activityTitle(a) ?: (SPORT_LABEL[sport] ?: "Entrenamiento"),
                sport, pyRound((a["moving_time"].num() ?: 0.0) / 60), a["icu_training_load"].num()?.let { pyRound(it) },
                activityDetail(sport, a["distance"].num(), a["moving_time"].num(), a["average_heartrate"].num(), a["icu_training_load"].num()),
                a["icu_hr_zone_times"].nums().takeIf { it.sum() > 0 }?.map { pyRound(it / 60) } ?: emptyList())
        }
    return ProgressScreen(true, metrics, weekly, status, predictions, source, recent)
}

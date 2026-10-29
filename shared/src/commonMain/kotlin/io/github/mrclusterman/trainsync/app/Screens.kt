package io.github.mrclusterman.trainsync.app

// Lo que muestra cada apartado, calculado una vez para Android e iOS (docs/VISION.md §5): las dos apps solo pintan.

import io.github.mrclusterman.trainsync.engine.plusDays
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

data class TodayScreen(
    val dateLabel: String,
    val goalLine: String?,  // "Maratón de Valencia · en 6 semanas · Base" o "Bloque 2 · semana 3 de 4"
    val headline: String,  // tu cuerpo hoy en una frase, salida de la misma decisión que la sesión
    val headlineDetail: List<Reason>,
    val day: DayView,
    val weekLine: String,  // "Esta semana: 80 min de actividad y fuerza 2 días"
    val meetsWho: Boolean,
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
    val strength = week.strengthDays
    return TodayScreen(
        dateLabel = dateLabel(today.toString()),
        goalLine = goalLine, headline = headline, headlineDetail = detail, day = day,
        weekLine = "Esta semana: ${week.activeMinutes} min de actividad y fuerza $strength ${if (strength == 1) "día" else "días"}",
        meetsWho = week.activeMinutes >= 150 && strength >= 2,
        warnings = week.warnings, info = week.info,
        askReadiness = !planner.hasRecoverySignals && data.readiness[today.toString()] == null && day.items.isNotEmpty(),
        linked = data.cache != null, goalFinished = goalFinished,
    )
}

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
            else -> "Llevas mucha fatiga acumulada."
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

data class ProgressScreen(val linked: Boolean, val metrics: List<Metric>, val weeklyMinutes: List<Point>)

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
    // Minutos moderados equivalentes por semana (OMS): Z1 no cuenta, Z2–Z3 ×1, Z4 o más ×2 (zonas de intervals.icu)
    val weekly = (0 until days / 7).reversed().map { w ->
        val monday = mondayOf(today).plusDays(-7 * w)
        val end = monday.plusDays(7).toString()
        val minutes = cache.activities.filter { it.date() >= monday.toString() && it.date() < end }.sumOf { a ->
            val z = a["icu_hr_zone_times"].nums()
            if (z.size < 7) 0.0 else (z[1] + z[2] + 2 * (z[3] + z[4] + z[5] + z[6])) / 60
        }
        Point(monday.toString(), minutes)
    }
    return ProgressScreen(true, metrics, weekly)
}

package io.github.mrclusterman.trainsync.engine

// Motor de entrenamiento (memoria técnica §8), portado de prototype/backend/app/services/training_engine.py.
// Funciones puras y deterministas: sin I/O ni IA. Reglas y fuentes: docs/MOTOR_ENTRENAMIENTO.md.
// Lo marcado con [Inferencia] no tiene evidencia publicada y es un parámetro ajustable.

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

val K_CTL = 1 - exp(-1.0 / 42)
val K_ATL = 1 - exp(-1.0 / 7)
val SWIM = setOf("swimming_pool", "swimming_open")

// ---------------------------------------------------------------- 8.1 / 8.2 carga

fun hrTss(durationS: Double, avgHr: Double, restHr: Double, lthr: Double): Double {
    val ratio = (avgHr - restHr) / (lthr - restHr)
    return durationS * ratio.pow(2) * 100 / 3600
}

fun powerTss(durationS: Double, npWatts: Double, ftp: Double): Double =
    durationS * npWatts * (npWatts / ftp) / (ftp * 3600) * 100

fun swimTss(durationS: Double, pace100mS: Double, css100mS: Double): Double =
    durationS * (css100mS / pace100mS).pow(2) * 100 / 3600

fun strengthTss(rpe: Double, minutes: Double): Double = (rpe / 10).pow(2) * (minutes / 60) * 100

/** Un paso del EWA de CTL (k = K_CTL) o ATL (k = K_ATL). */
fun nextLoad(prev: Double, tss: Double, k: Double): Double = prev + (tss - prev) * k

/** Media móvil 7 d de ln(RMSSD) frente al SWC (0,5 DT de los 28 días previos). history: del más antiguo a hoy. */
fun hrvStatus(history: List<Double?>): String? {
    val ln = history.takeLast(35).map { lnOrNull(it) }
    val last7 = ln.takeLast(7).filterNotNull()
    val base = ln.dropLast(7).filterNotNull()
    if (last7.size < 3 || base.size < 14) return null
    val swc = 0.5 * stdev(base)
    return if (abs(mean(last7) - mean(base)) <= swc) "within_swc" else "outside_swc"
}

// ---------------------------------------------------------------- 8.3 zonas

/** % LTHR según Friel; running y ciclismo tienen límites distintos. null = abierta. */
val HR_ZONES: Map<String, Map<String, Pair<Double, Double?>>> = mapOf(
    "running" to mapOf("Z1" to (0.0 to 0.85), "Z2" to (0.85 to 0.90), "Z3" to (0.90 to 0.95),
        "Z4" to (0.95 to 1.00), "Z5" to (1.00 to 1.07), "Z6" to (1.07 to null)),
    "cycling" to mapOf("Z1" to (0.0 to 0.81), "Z2" to (0.81 to 0.90), "Z3" to (0.90 to 0.94),
        "Z4" to (0.94 to 1.00), "Z5" to (1.00 to 1.07), "Z6" to (1.07 to null)),
)

/** RPE (CR10) por zona. [Práctica] Guía complementaria a FC o ritmo, no sustituto. */
val RPE_BY_ZONE = mapOf("Z1" to "1-2", "Z2" to "3-4", "Z3" to "5", "Z4" to "6-7", "Z5" to "8-9", "Z6" to "10")
val SWIM_ZONES: Map<String, Pair<Double?, Double?>> = mapOf("Z1" to (1.20 to null), "Z2" to (1.10 to 1.20),
    "Z3" to (1.03 to 1.10), "Z4" to (0.97 to 1.03), "Z5" to (0.90 to 0.97), "Z6" to (null to 0.90))

private fun hrZone(sport: String, zone: String) = HR_ZONES[if (sport == "running") "running" else "cycling"]!![zone]!!

/**
 * Metros de un bloque de natación al ritmo medio de su zona (CSS × factor), en largos de 25 m: con tu CSS la sesión se
 * escribe en distancia, como se nada en la piscina, y no en minutos a un ritmo fijo. [Práctica; ritmo medio: Inferencia]
 */
fun swimMeters(minutes: Double, zone: String, css: Double): Int {
    val (fast, slow) = SWIM_ZONES[zone]!!
    val factor = when {
        fast == null -> slow!! - 0.03  // Z6 abierta por abajo
        slow == null -> fast + 0.05  // Z1 abierta por arriba
        else -> (fast + slow) / 2
    }
    return max(25, pyRound(minutes * 60 / (css * factor) * 100 / 25) * 25)
}

/** Rango legible de una zona: ppm en running y ciclismo, ritmo /100m en natación. */
fun zoneTarget(zone: String, sport: String, lthr: Double?, css: Double?): String? {
    if (sport in SWIM) {
        if (css == null || css == 0.0) return "RPE ${RPE_BY_ZONE[zone]}"  // sin CSS, por RPE hasta el test (MOTOR §4.3)
        val (lo, hi) = SWIM_ZONES[zone]!!
        val fmtPace = { f: Double -> "${floorDiv(css * f, 60.0).toInt()}:${twoDigits(pyRound((css * f) % 60))}" }
        if (lo == null) return "< ${fmtPace(hi!!)}/100m"
        if (hi == null) return "> ${fmtPace(lo)}/100m"
        return "${fmtPace(lo)}-${fmtPace(hi)}/100m"
    }
    if (lthr == null || lthr == 0.0) return null
    val (lo, hi) = hrZone(sport, zone)
    if (hi == null) return "> ${pyRound(lthr * lo)} ppm"
    return if (lo != 0.0) "${pyRound(lthr * lo)}-${pyRound(lthr * hi) - 1} ppm" else "< ${pyRound(lthr * hi)} ppm"
}

/** RPE alto con FC baja: señal de fatiga. [Inferencia] RPE >= 6 con menos del 10 % del tiempo en Z3 o más. */
fun effortMismatch(rpe: Double?, zoneSecs: List<Double>): Boolean {
    val total = zoneSecs.sum()
    return rpe != null && rpe >= 6 && total > 0 && zoneSecs.drop(2).sum() / total < 0.10
}

/** Foster 1998: carga semanal (sRPE), monotonía (media / DT diaria) y strain (carga × monotonía). */
fun fosterMonotony(dailyLoads: List<Double>): Triple<Double, Double?, Int> {
    val load = dailyLoads.sum()
    val sd = if (dailyLoads.size > 1) stdev(dailyLoads) else 0.0
    val mono = if (sd != 0.0) mean(dailyLoads) / sd else null
    return Triple(load, if (mono != null && mono != 0.0) pyRound(mono, 2) else null,
        if (mono != null && mono != 0.0) pyRound(load * mono) else 0)
}

/** Regla 2 de zona gris: Z3 > 15 % en una sesión fácil. [Inferencia] fácil = menos del 5 % en Z4 o más. */
fun grayZone(zoneSecs: List<Double>): Boolean {
    val total = zoneSecs.sum()
    if (total <= 0 || zoneSecs.size < 4) return false
    return zoneSecs[2] / total > 0.15 && zoneSecs.drop(3).sum() / total < 0.05
}

// ---------------------------------------------------------------- 8.5 periodización

data class Goal(
    val name: String,
    val sport: String,  // running / cycling / swimming_pool / swimming_open / triathlon / duathlon
    val date: LocalDate,
    val priority: String = "A",
    val distanceKm: Double? = null,
    val trail: Boolean = false,
)

fun runClass(goal: Goal?): String? {
    if (goal == null || goal.sport != "running" || goal.distanceKm == null || goal.distanceKm == 0.0) return null
    val d = goal.distanceKm
    return if (d <= 7) "5k" else if (d <= 15) "10k" else if (d <= 25) "half" else if (d <= 45) "marathon" else "ultra"
}

/** Días de taper (A) por distancia. [Práctica: Mayo Clinic; ultra: entrenadores] Resto: 14 [Verificado: Bosquet 2007]. */
val TAPER_DAYS = mapOf("5k" to 9, "10k" to 9, "half" to 12, "marathon" to 21, "ultra" to 21)

fun taperDays(goal: Goal): Int = when (goal.priority) {
    "B" -> 4  // [Práctica] MOTOR §8.2
    "C" -> 1
    else -> TAPER_DAYS[runClass(goal)] ?: 14
}

const val PREP_WEEKS = 20

/** (fase, semana de descarga). Fases: base_early, base, build, peak, taper. */
fun phaseFor(day: LocalDate, goal: Goal?, planStart: LocalDate?): Pair<String, Boolean> {
    if (goal == null || day > goal.date) return "base" to false  // sin objetivo: mantenimiento aeróbico
    val taper = taperDays(goal)
    if (day.daysUntil(goal.date) < taper) return "taper" to false
    // [Inferencia] sin inicio fijado, preparación de 20 semanas; antes, base de mantenimiento
    val start = planStart ?: goal.date.plusDays(-7 * PREP_WEEKS)
    if (day < start) return "base" to false
    val elapsed = start.daysUntil(day)
    // Proporciones de la Memoria 8.5 sin el taper: base ~52 %, build ~28 %, peak ~20 %
    val progress = elapsed.toDouble() / max(start.daysUntil(goal.date) - taper, 1)
    val week = elapsed / 7
    val recoveryWeek = week % 4 == 3  // patrón 3+1, salvo en peak y taper
    if (progress >= 0.80) return "peak" to false
    if (progress >= 0.52) return "build" to recoveryWeek
    return (if (week < 4) "base_early" else "base") to recoveryWeek
}

/** Plantilla semanal polarizada (8.4). Días no listados = descanso; fitDays la ajusta a los días del usuario. */
val TEMPLATES: Map<String, Map<String, String>> = mapOf(
    "base_early" to mapOf("TUE" to "vo2max", "WED" to "aerobic", "THU" to "vo2max", "FRI" to "recovery", "SAT" to "aerobic", "SUN" to "long"),
    "base" to mapOf("TUE" to "vo2max", "WED" to "aerobic", "THU" to "neuromuscular", "FRI" to "recovery", "SAT" to "aerobic", "SUN" to "long"),
    "build" to mapOf("TUE" to "threshold", "WED" to "aerobic", "THU" to "vo2max", "FRI" to "recovery", "SAT" to "aerobic", "SUN" to "long"),
    "peak" to mapOf("TUE" to "race_specific", "WED" to "aerobic", "THU" to "vo2max", "FRI" to "recovery", "SAT" to "aerobic", "SUN" to "long"),
    "taper" to mapOf("TUE" to "race_specific", "WED" to "aerobic", "THU" to "recovery", "SAT" to "aerobic", "SUN" to "aerobic"),
)

/** Fuerza: más en base y construcción, menos en peak, nada en taper (RF-08). */
val STRENGTH_DAYS = mapOf("base_early" to listOf("TUE", "FRI"), "base" to listOf("TUE", "FRI"),
    "build" to listOf("TUE", "FRI"), "peak" to listOf("TUE"), "taper" to emptyList())
val PHASE_VOLUME = mapOf("base_early" to 0.9, "base" to 1.0, "build" to 1.1, "peak" to 1.15, "taper" to 0.55)  // [Inferencia]
val QUALITY = setOf("vo2max", "threshold", "race_specific", "brick", "test", "tempo", "anaerobic")
val DOWNGRADE = mapOf("vo2max" to "aerobic", "threshold" to "aerobic", "race_specific" to "aerobic", "brick" to "aerobic",
    "test" to "aerobic", "neuromuscular" to "aerobic", "long" to "aerobic", "aerobic" to "recovery",
    "recovery" to "rest", "rest" to "rest", "tempo" to "aerobic", "anaerobic" to "aerobic")

/** Deporte principal por día en objetivos multideporte (8.4). */
val ROTATION = mapOf(
    "triathlon" to mapOf("TUE" to "cycling", "WED" to "swimming_pool", "THU" to "running", "FRI" to "swimming_pool", "SAT" to "running", "SUN" to "cycling"),
    "duathlon" to mapOf("TUE" to "running", "WED" to "cycling", "THU" to "cycling", "FRI" to "running", "SAT" to "running", "SUN" to "cycling"),
)

/** Plantilla de la fase adaptada al tipo de prueba (MOTOR §6). */
fun templateFor(phase: String, goal: Goal?): Map<String, String> {
    val t = TEMPLATES[phase]!!.toMutableMap()
    val rc = runClass(goal)
    if (phase == "build" && rc in setOf("half", "marathon", "ultra")) t["THU"] = "threshold"  // [Verificado parcial: Casado 2022]
    if (rc == "ultra" && phase in setOf("build", "peak")) t["SAT"] = "long"  // [Práctica] largas en días consecutivos
    if (goal != null && goal.sport in ROTATION && phase in setOf("build", "peak")) t["SAT"] = "brick"  // [Práctica]
    return t
}

/** Qué se conserva antes cuando no caben todas las sesiones en los días disponibles. [Inferencia] */
val KEEP_FIRST = listOf("long", "race_specific", "brick", "threshold", "vo2max", "test", "tempo", "anaerobic",
    "neuromuscular", "aerobic", "recovery")
val DAY_NAMES = WEEKDAYS.zip(listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")).toMap()

/**
 * Coloca la plantilla en los días que el usuario puede entrenar: lo que cae en un día sin entreno pasa al día libre más
 * cercano; si no queda ninguno, desplaza a la sesión menos importante. Devuelve la plantilla y de qué día viene lo movido.
 */
fun fitDays(template: Map<String, String>, restDays: List<String>): Pair<Map<String, String>, Map<String, String>> {
    val available = WEEKDAYS.filter { it !in restDays }
    val placed = template.filterKeys { it in available }.toMutableMap()
    val moved = mutableMapOf<String, String>()
    for (day in template.keys.filter { it !in available }.sortedBy { KEEP_FIRST.indexOf(template[it]) }) {
        val free = available.filter { it !in placed }
        val target: String
        if (free.isNotEmpty()) {
            target = free.minBy { abs(WEEKDAYS.indexOf(it) - WEEKDAYS.indexOf(day)) }
        } else {
            val worst = placed.keys.maxByOrNull { KEEP_FIRST.indexOf(placed[it]) }
            if (worst == null || KEEP_FIRST.indexOf(placed[worst]) <= KEEP_FIRST.indexOf(template[day])) continue
            target = worst
        }
        placed[target] = template[day]!!
        moved[target] = day
    }
    return placed to moved
}

/** Tope de la larga de running por distancia (min). [Práctica: Daniels <= 150; 5K-HM y ultra: Inferencia] */
val LONG_CAP = mapOf("5k" to 75, "10k" to 90, "half" to 120, "marathon" to 150, "ultra" to 240)

fun longCap(goal: Goal?, sport: String): Int? = if (sport == "running") LONG_CAP[runClass(goal)] ?: 150 else null

// ---------------------------------------------------------------- 8.4 decisión diaria

data class DayState(
    val ctl: Double,
    val atl: Double,
    val hrvHistory: List<Double?> = emptyList(),  // RMSSD diario hasta hoy
    val sleepScore: Double? = null,
    val sleepHours: Double? = null,  // anoche, si el reloj o el móvil da los minutos dormidos
    val usualSleepHours: Double? = null,  // tu mediana de los 28 días anteriores
    val feltStatus: String? = null,  // sin reloj, tus tres respuestas de hoy: "easy" (suave) o "rest" (recuperar)
    val consecutiveDays: Int = 0,
    val effortMismatch: Boolean = false,  // RPE alto con FC baja en los últimos 2 días
    val loadFocus: Map<String, Double>? = null,  // reparto de la carga de 28 días
    val daysSinceActivity: Int? = null,
) {
    val tsb: Double get() = ctl - atl
}

/** Algoritmo de decisión diaria: (tipo, factor de volumen, motivos). */
fun adjust(sessionType: String, state: DayState): Triple<String, Double, List<String>> {
    var type = sessionType
    val reasons = mutableListOf<String>()
    var volume = 1.0
    val tsb = state.tsb
    val status = hrvStatus(state.hrvHistory)
    // TSB entre -10 y -30 es carga productiva (Friel); solo < -30 obliga a recuperar
    if (tsb < -30) {
        return Triple(if (type != "rest") "recovery" else "rest", 1.0,
            listOf("Fatiga muy por encima de tu forma física (forma ${fmt(tsb, 1)}, por debajo de −30): hoy solo recuperación [Práctica]"))
    }
    // Sin reloj, tus respuestas (sueño, energía, agujetas) hacen lo mismo que las señales del reloj [Inferencia]
    if (state.feltStatus == "rest") {
        return Triple(if (type != "rest") "recovery" else "rest", 1.0,
            listOf("Por tus respuestas de hoy (sueño, energía y agujetas, todo mal): solo recuperación [Inferencia]"))
    }
    if (state.feltStatus == "easy") {
        if (type in QUALITY) type = DOWNGRADE[type]!!
        volume *= 0.7
        reasons += "Por tus respuestas de hoy (has dormido o te notas peor de lo normal): versión suave, −30 % y sin intensidad [Inferencia]"
    }
    if (status == "outside_swc" && type in QUALITY) {
        type = "aerobic"
        reasons += "Tu variabilidad cardíaca (VFC) de los últimos 7 días está fuera de tu rango normal: hoy sin intensidad [Verificado]"
    }
    if (state.effortMismatch && type in QUALITY) {
        type = "aerobic"
        reasons += "Esfuerzo percibido alto con FC baja en tu última sesión: posible fatiga, hoy sin intensidad [Verificado]"
    }
    val sleep = state.sleepScore
    // Sin puntuación de sueño (Health Connect, Apple Salud), las horas: menos de 6 y una o más por debajo de lo normal en
    // ti. Los adultos necesitan 7 h o más (Watson 2015, AASM/SRS) [Verificado]; los umbrales de 6 h y 1 h, [Inferencia]
    val hours = state.sleepHours
    val usual = state.usualSleepHours
    val shortNight = sleep == null && hours != null && usual != null && hours < 6 && hours <= usual - 1
    if ((sleep != null && sleep < 50) || shortNight) {
        // Mal sueño quita la calidad; sumado a HRV fuera de rango, el día pasa a recuperación
        if (type in QUALITY || status == "outside_swc") type = DOWNGRADE[type]!!
        volume = 0.7
        reasons += if (sleep != null) "Sueño ${fmt(sleep, 0)}/100: -30 % de carga y sin calidad [Inferencia]"
        else "Has dormido ${fmt(hours!!, 1)} h, menos de lo normal en ti (${fmt(usual!!, 1)} h): -30 % de carga y sin intensidad [Inferencia]"
    }
    // Tras una parada se reconstruye la base antes de la intensidad [Práctica: Garmin; 7 días: Inferencia]
    val idle = state.daysSinceActivity
    if (idle != null && idle >= 7 && type in QUALITY) {
        type = "aerobic"
        reasons += "$idle días sin entrenar: primero base, la intensidad vuelve después [Práctica]"
    }
    if (state.consecutiveDays >= 3 && type in QUALITY && tsb < -20) {  // [Inferencia]
        type = "aerobic"
        reasons += "${state.consecutiveDays} días seguidos entrenando y con la forma en ${fmt(tsb, 1)}: hoy sin intensidad [Inferencia]"
    }
    return Triple(type, volume, reasons)
}

// ---------------------------------------------------------------- 12 reparto de la carga (el "load focus" de Garmin)

/** Fracción objetivo de la carga de 28 días: aeróbico bajo, alto y anaeróbico. [Inferencia: modelo 80/20] */
val FOCUS_TARGET: Map<String, Map<String, Double>> = mapOf(
    "base_early" to mapOf("low" to 0.80, "high" to 0.15, "anaerobic" to 0.05),
    "base" to mapOf("low" to 0.80, "high" to 0.15, "anaerobic" to 0.05),
    "build" to mapOf("low" to 0.75, "high" to 0.20, "anaerobic" to 0.05),
    "peak" to mapOf("low" to 0.75, "high" to 0.20, "anaerobic" to 0.05),
)
const val FOCUS_MARGIN = 0.05  // déficit mínimo para cambiar la sesión de la plantilla [Inferencia]

/** Una actividad para el reparto de la carga: tiempo en las 7 zonas de FC de intervals.icu y su carga. */
data class ZoneLoad(val hrZoneTimes: List<Double>, val trainingLoad: Double)

fun loadFocus(activities: List<ZoneLoad>): Map<String, Double>? {
    var low = 0.0
    var high = 0.0
    var anaerobic = 0.0
    for (a in activities) {
        val time = a.hrZoneTimes.sum()
        if (time != 0.0 && a.trainingLoad != 0.0) {
            low += a.trainingLoad * a.hrZoneTimes.take(2).sum() / time
            high += a.trainingLoad * a.hrZoneTimes.drop(2).take(3).sum() / time
            anaerobic += a.trainingLoad * a.hrZoneTimes.drop(5).sum() / time
        }
    }
    val total = low + high + anaerobic
    return if (total != 0.0) mapOf("low" to low / total, "high" to high / total, "anaerobic" to anaerobic / total) else null
}

/** La sesión de calidad cubre el tipo de carga que más falta (como las sugerencias de Garmin). */
fun focusKind(kind: String, phase: String, focus: Map<String, Double>?, goal: Goal?): Pair<String, String?> {
    if (focus == null || phase !in FOCUS_TARGET || kind !in setOf("vo2max", "threshold", "neuromuscular")) return kind to null
    val target = FOCUS_TARGET[phase]!!
    val gaps = target.mapValues { (k, v) -> v - focus[k]!! }
    val worst = gaps.keys.maxBy { gaps[it]!! }
    if (gaps[worst]!! < FOCUS_MARGIN) return kind to null
    val share = "${pct(focus[worst]!!)} de tu carga frente al ${pct(target[worst]!!)} objetivo"
    return when {
        worst == "low" -> "aerobic" to "Falta carga aeróbica baja ($share): hoy base [Inferencia]"
        worst == "anaerobic" -> "anaerobic" to "Falta carga anaeróbica ($share) [Inferencia]"
        runClass(goal) in setOf("half", "marathon") && phase in setOf("build", "peak") ->
            "tempo" to "Falta carga aeróbica alta ($share): tempo a ritmo de maratón [Inferencia]"
        else -> (if (kind == "neuromuscular") "vo2max" else kind) to "Falta carga aeróbica alta ($share) [Inferencia]"
    }
}

// ---------------------------------------------------------------- 13 ritmos a partir de la FC (como Garmin)

/** Muestra segundo a segundo de una carrera: FC, velocidad (m/s) y pendiente (%). */
data class RunSample(val hr: Double?, val speed: Double?, val grade: Double?)

/**
 * Recta velocidad = a + b·FC. Solo llano (|pendiente| <= 2 %) y corriendo (> 1,5 m/s); mediana por tramos de 5 ppm
 * con >= 60 s. Hacen falta >= 4 tramos y 15 ppm de rango. [Inferencia]
 */
fun hrPaceModel(samples: List<RunSample>): Pair<Double, Double>? {
    val bins = linkedMapOf<Int, MutableList<Double>>()
    for (s in samples) {
        val hr = s.hr ?: continue
        val v = s.speed ?: continue
        val grade = s.grade ?: continue
        if (hr != 0.0 && v > 1.5 && abs(grade) <= 2) bins.getOrPut(hr.toInt() / 5 * 5) { mutableListOf() } += v
    }
    val points = bins.filter { it.value.size >= 60 }.map { (h, v) -> (h + 2.5) to median(v) }
    if (points.size < 4 || points.maxOf { it.first } - points.minOf { it.first } < 15) return null
    val (b, a) = linearRegression(points.map { it.first }, points.map { it.second })
    return if (b > 0) a to b else null
}

/** Ritmo (s/km) que tus carreras predicen para esa FC. */
fun paceAt(bpm: Double, model: Pair<Double, Double>): Double = 1000 / max(model.first + model.second * bpm, 1.5)

/** Límites de FC (ppm) de una zona; null en Z6 (abierta: manda el RPE). */
fun hrBounds(zone: String, sport: String, lthr: Double, maxHr: Double?, hrCap: Int?): Pair<Double, Double>? {
    val (lo, hi) = hrZone(sport, zone)
    if (hi == null) return null
    var loBpm = lthr * lo
    var hiBpm = lthr * hi
    if (zone == "Z1") {
        loBpm = if (maxHr != null && maxHr != 0.0) 0.5 * maxHr else 0.65 * lthr  // abierta por abajo [Inferencia]
        if (hrCap != null && hrCap != 0) hiBpm = min(hiBpm, hrCap.toDouble())
    }
    if (zone == "Z2" && hrCap != null && hrCap != 0) {
        // El techo LTHR − 30 manda en sesiones fáciles; 5 % de margen si queda por debajo de la Z2
        loBpm = min(loBpm, hrCap - 0.05 * lthr)
        hiBpm = hrCap.toDouble()
    }
    return loBpm to hiBpm
}

// ---------------------------------------------------------------- sesiones

data class Profile(
    val activeSports: List<String>,
    val strength: String = "gym",  // gym / bodyweight / none
    val restDays: List<String> = listOf("MON"),
    val lthr: Double? = null,  // por defecto; lthrBySport la sustituye por deporte
    val lthrBySport: Map<String, Double> = emptyMap(),
    val maxHr: Double? = null,
    val restHr: Double? = null,
    val css: Double? = null,  // s/100 m
    val runPace: Double? = null,  // ritmo umbral de carrera, s/km
    val ftp: Double? = null,  // vatios; sin FTP la bici va por FC
    val runModel: Pair<Double, Double>? = null,  // velocidad (m/s) = a + b·FC, de tus carreras (hrPaceModel)
    val dayMinutes: Map<String, Int>? = null,  // minutos disponibles por día; null = sin límite
    val planStart: LocalDate? = null,
    val warnings: MutableList<String> = mutableListOf(),
    val needsTest: Set<String> = emptySet(),  // deportes sin LTHR o CSS fiable (onboarding RF-03)
    val intensityCap: String = "vigorous",  // tope del cribado del ACSM (Screening.kt)
) {
    fun lthrFor(sport: String): Double? = lthrBySport[sport]?.takeIf { it != 0.0 } ?: lthr
}

/** Ratio de intensidad medio por zona para estimar el TSS planificado. [Inferencia] */
val ZONE_IF = mapOf("Z1" to 0.65, "Z2" to 0.75, "Z3" to 0.88, "Z4" to 0.97, "Z5" to 1.05, "Z6" to 1.15)
val NAMES = mapOf("recovery" to "Recuperación", "aerobic" to "Aeróbico base", "long" to "Tirada larga",
    "vo2max" to "Intervalos VO2max", "threshold" to "Umbral", "race_specific" to "Específico de carrera",
    "rest" to "Descanso", "strength" to "Fuerza", "neuromuscular" to "Neuromuscular",
    "brick" to "Transición bici-carrera (brick)", "test" to "Test de campo", "tempo" to "Tempo", "anaerobic" to "Anaeróbico")
val GOALS = mapOf("recovery" to "Recuperar sin generar fatiga",
    "aerobic" to "Base aeróbica y eficiencia (Z2 pura, sin zona gris)",
    "long" to "Resistencia aeróbica y economía", "vo2max" to "Elevar el techo aeróbico",
    "threshold" to "Subir el umbral de lactato", "race_specific" to "Ritmo y sensaciones de competición",
    "neuromuscular" to "Potencia y economía sin fatiga (esfuerzos muy cortos, recuperación completa)",
    "brick" to "Acostumbrar las piernas a correr tras la bici", "test" to "Medir LTHR o CSS para fijar tus zonas",
    "tempo" to "Ritmo de maratón sostenido (aeróbico alto)",
    "anaerobic" to "Capacidad anaeróbica: esfuerzos de 40–60″ por encima del VO2max")

private fun blocksTss(blocks: List<Block>): Double {
    var total = 0.0
    for (b in blocks) {
        val duty = b.duty ?: 1.0  // intermitentes: solo esa fracción va a la zona de trabajo
        total += b.reps * b.minutes * (duty * ZONE_IF[b.zone]!!.pow(2) + (1 - duty) * ZONE_IF["Z1"]!!.pow(2))
        total += b.reps * (b.restMinutes ?: 0.0) * ZONE_IF[b.restZone ?: "Z1"]!!.pow(2)
    }
    return total / 60 * 100
}

/** Sesión del catálogo para el hueco; rota cada semana entre las válidas. Las de trail solo salen en trail. */
fun pickSession(kind: String, sport: String, phase: String, week: Int, event: String? = null): CatalogEntry? {
    val options = CATALOG.filter {
        it.sport == sport && it.category == kind && phase in it.phases && (it.events.isEmpty() || event in it.events)
    }.sortedBy { it.events.isEmpty() }
    return if (options.isEmpty()) null else options[week % options.size]
}

/** Sesión del catálogo con la duración ajustada al volumen; null si el deporte no tiene sesión para ese hueco. */
fun buildSession(kind: String, sport: String, profile: Profile, volume: Double = 1.0, phase: String = "base",
                 week: Int = 0, event: String? = null, maxMinutes: Int? = null): Session? {
    val entry = pickSession(kind, sport, phase, week, event) ?: return null
    val (lo, hi) = entry.duration
    var total = max(pyRound((lo + hi) / 2.0 * volume), 20)
    if (maxMinutes != null && maxMinutes != 0 && kind == "long") total = min(total, maxMinutes)
    // minutes = null en los bloques que absorben la diferencia hasta `total`
    val draft = entry.blocks.map { c -> c to c.minutes }
    val reps = entry.blocks.map { it.reps }.toMutableList()
    fun span(i: Int) = reps[i] * (draft[i].second!! + (draft[i].first.rest ?: 0.0))
    for (i in draft.indices) {
        val minutes = draft[i].second
        if (reps[i] > 1 && minutes != null) {
            // Si el volumen no da para la estructura, caben menos series: mínimo 2 cortas, 1 si el bloque es >= 10 min
            val others = draft.indices.filter { it != i && draft[it].second != null }.sumOf { span(it) }
            val fits = floorDiv(total - 5 - others, minutes + (draft[i].first.rest ?: 0.0)).toInt()
            reps[i] = max(if (minutes >= 10) 1 else 2, min(reps[i], fits))
        }
    }
    val fixed = draft.indices.filter { draft[it].second != null }.sumOf { span(it) }
    // Con tope del cribado, ningún tramo por encima: Z3 como mucho (moderado) o Z2 (ligero-moderado) [Verificado: ACSM]
    val top = mapOf("moderate" to "Z3", "light_moderate" to "Z2")[profile.intensityCap]
    fun capped(zone: String) = if (top != null && zone > top) top else zone
    val blocks = draft.mapIndexed { i, (c, minutes) ->
        Block(block = c.block, reps = reps[i], minutes = minutes ?: max(total - fixed, 5.0), zone = capped(c.zone),
            restMinutes = c.rest, restZone = if (c.rest != null) "Z1" else null, detail = c.detail, duty = c.duty)
    }
    val css = profile.css?.takeIf { it != 0.0 }
    for (b in blocks) {
        if (sport in SWIM && css != null && kind != "test") b.meters = swimMeters(b.minutes, b.zone, css)
        b.rpe = RPE_BY_ZONE[b.zone]
        if (b.reps > 1 && b.minutes < 3 && b.zone in setOf("Z5", "Z6")) {
            b.guide = "Guíate por RPE: en series cortas la FC tarda en subir"  // [Práctica]
        }
    }
    val zones = (blocks.map { it.zone } + blocks.mapNotNull { it.restZone }).distinct().sorted()
    val lthr = profile.lthrFor(sport)
    // Un test se hace al máximo sostenible: un objetivo calculado con la LTHR que se quiere medir lo sesgaría
    var targets: MutableMap<String, String?> =
        if (kind == "test") mutableMapOf() else zones.associateWith { zoneTarget(it, sport, lthr, profile.css) }.toMutableMap()
    // Techo de sesión fácil = umbral aeróbico estimado (LTHR − 30, Friel) hasta que se mida
    val hrCap = if (lthr != null && lthr != 0.0 && kind !in QUALITY && sport !in SWIM) pyRound(lthr - 30) else null
    if (hrCap != null && hrCap != 0 && targets["Z2"] != null) {
        // El techo manda sobre la Z2 de Friel: en running el techo cae por debajo del inicio de Z2
        val z2Lo = pyRound(lthr!! * hrZone(sport, "Z2").first)
        targets["Z2"] = if (z2Lo < hrCap) "$z2Lo-$hrCap ppm" else "<= $hrCap ppm"
        if (targets["Z1"] != null && z2Lo > hrCap) targets["Z1"] = "< $hrCap ppm"
    }
    val model = profile.runModel
    if (sport == "running" && model != null && lthr != null && lthr != 0.0 && kind != "test" && !entry.id.startsWith("T-")) {
        // Como Garmin: ritmos desde tus zonas de FC con tu propia relación FC-velocidad (§13)
        val fmtPace = { sec: Double -> "${floorDiv(sec, 60.0).toInt()}:${twoDigits(pyRound(sec % 60))}" }
        targets = targets.mapValues { (z, t) ->
            val b = hrBounds(z, sport, lthr, profile.maxHr, hrCap)
            when {
                b == null -> t
                z == "Z1" -> "> ${fmtPace(paceAt(b.second, model))} /km"  // abierta: más lento que
                else -> "${fmtPace(paceAt(b.first, model))}-${fmtPace(paceAt(b.second, model))} /km"
            }
        }.toMutableMap()
    }
    return Session(
        id = entry.id, sport = sport, type = kind, name = NAMES[kind]!!, goal = GOALS[kind]!!,
        durationMin = pyRound(blocks.sumOf { it.reps * (it.minutes + (it.restMinutes ?: 0.0)) }),
        tss = pyRound(blocksTss(blocks)), blocks = blocks,
        targets = targets.filterValues { !it.isNullOrEmpty() }.mapValues { it.value!! }, hrCap = hrCap,
        ifBad = NOTES[kind]!!.first, ifGood = NOTES[kind]!!.second,
    )
}

/** Fuerza por fase (MOTOR §5). Carga = rTSS con el RPE planificado. */
fun buildStrength(goalSport: String, profile: Profile, phase: String): RaceStrength {
    val (dose, rpe, minutes) = STRENGTH_DOSE[phase]!!
    val kit = if (profile.strength == "gym") "gym" else "bodyweight"
    val upper = goalSport in SWIM || goalSport == "triathlon"
    val exercises = LOWER[kit]!! + (if (phase == "build") PLYO[kit]!! else emptyList()) +
        (if (upper) UPPER[kit]!! else emptyList()) + CORE
    // Eddens 2018: fuerza antes favorece la fuerza; en build y peak prima la calidad de la sesión de resistencia
    val order = if (phase in setOf("base_early", "base")) "antes" else "después"
    return RaceStrength(name = NAMES["strength"]!!, durationMin = minutes, dose = dose, rpePlanned = rpe,
        tss = pyRound(strengthTss(rpe.toDouble(), minutes.toDouble())), exercises = exercises,
        order = "$order de la sesión de resistencia si van seguidas")
}

fun mainSport(weekday: String, goal: Goal?, profile: Profile): String {
    var preferred = if (goal != null) ROTATION[goal.sport]?.get(weekday) else null
    if (preferred == null && goal != null && goal.sport in profile.activeSports) preferred = goal.sport
    if (preferred in profile.activeSports) return preferred!!
    // Sin carrera objetivo (o con un deporte que no practica), rotan los deportes elegidos día a día [Inferencia]
    return profile.activeSports[WEEKDAYS.indexOf(weekday) % profile.activeSports.size]
}

/** Lo más que sube la carga de una semana sobre la última de carga (regla del 10 %). [Práctica] */
const val MAX_WEEKLY_RISE = 1.10
/** Lo más que pesa una semana de descarga frente a la última de carga (el volumen ya baja un 30 %). [Práctica] */
const val MAX_DELOAD_SHARE = 0.80

private var rampCache: Pair<Pair<Profile, Goal>, List<Double>>? = null  // último plan calculado: el mismo se pide muchas veces

/**
 * Factor de volumen de cada semana de la preparación para que la carga (TSS) no suba más de un 10 % sobre la última
 * semana de carga: sin él, el paso de base a construcción sube hasta un 27 % de golpe (validación R2). Las semanas de
 * descarga y el afinado no se tocan.
 */
fun rampScales(profile: Profile, goal: Goal): List<Double> {
    val key = profile to goal
    rampCache?.takeIf { it.first == key }?.let { return it.second }
    val start = profile.planStart ?: goal.date.plusDays(-7 * PREP_WEEKS)
    fun load(w: Int, scale: Double) = (0 until 7).sumOf { dayTss(proposeDay(start.plusDays(7 * w + it), profile, goal, scale = scale, ramp = false)) }
    var last: Double? = null
    val scales = (0..start.daysUntil(goal.date) / 7).map { w ->
        val first = proposeDay(start.plusDays(7 * w), profile, goal, ramp = false)
        if (first.phase == "taper") return@map 1.0
        // La descarga también se limita: si coincide con el cambio de fase, sus sesiones nuevas podían superar a la anterior
        val recovery = first.recoveryWeek
        var scale = 1.0
        var actual = load(w, 1.0)
        val cap = last?.let { it * if (recovery) MAX_DELOAD_SHARE else MAX_WEEKLY_RISE }
        if (cap != null && actual > cap) {
            // El TSS no es lineal con el volumen (bloques fijos, topes de minutos, la fuerza que entra o sale): por
            // bisección, el mayor volumen entre 0,15 y 1 cuya carga no pasa del tope
            var lo = 0.15
            var hi = 1.0
            repeat(10) {
                val mid = (lo + hi) / 2
                if (load(w, mid) <= cap) lo = mid else hi = mid
            }
            scale = lo
            actual = load(w, scale)
        }
        if (!recovery) last = actual
        scale
    }
    rampCache = key to scales
    return scales
}

/** Propuesta diaria (RF-05): sesión principal, alternativas por deporte activo y fuerza. `ramp`: aplicar rampScales. */
fun proposeDay(day: LocalDate, profile: Profile, goal: Goal?, state: DayState? = null, scale: Double = 1.0,
               ramp: Boolean = true): Day {
    val weekday = day.weekday()
    val week = day.isoWeek()
    val (phase, recoveryWeek) = phaseFor(day, goal, profile.planStart)
    val (template, moved) = fitDays(templateFor(phase, goal), profile.restDays)
    var kind = template[weekday] ?: "rest"
    var volume = PHASE_VOLUME[phase]!! * scale
    val reasons = mutableListOf<String>()
    var why: List<String> = emptyList()
    moved[weekday]?.let { reasons += "Viene del ${DAY_NAMES[it]}: ese día no entrenas [Inferencia]" }
    val prepStart = goal?.let { profile.planStart ?: it.date.plusDays(-7 * PREP_WEEKS) }
    if (ramp && goal != null && prepStart != null && day >= prepStart && day <= goal.date) {
        val rise = rampScales(profile, goal)[prepStart.daysUntil(day) / 7]
        volume *= rise
        if (rise < 0.95 && kind != "rest") reasons += "Carga de la semana limitada a +10 % sobre la anterior: subir más de golpe aumenta el riesgo de lesión [Práctica]"
    }
    // Con tope del cribado, nada de series ni sesiones de calidad hasta que lo valore el médico [Verificado: ACSM 2015]
    if (profile.intensityCap != "vigorous" && (kind in QUALITY || kind == "neuromuscular")) {
        kind = DOWNGRADE[kind]!!
        reasons += "Sin intensidad alta: por tu cuestionario de salud, el plan se queda en esfuerzo moderado [Verificado]"
    }
    if (recoveryWeek) {
        volume *= 0.7
        if (kind in QUALITY && weekday == "THU") kind = "aerobic"
        reasons += "Semana de descarga (patrón 3+1): -30 % de volumen [Práctica]"
    }
    if (state != null) {
        val (focused, focusReason) = focusKind(kind, phase, state.loadFocus, goal)
        kind = focused
        focusReason?.let { reasons += it }
        val (adjusted, factor, adjustReasons) = adjust(kind, state)
        kind = adjusted
        volume *= factor
        why = adjustReasons
        reasons += why
    }

    val out = Day(date = day.toString(), weekday = weekday, phase = phase, recoveryWeek = recoveryWeek, reasons = reasons,
        adjustments = why)
    if (kind == "rest") return out
    val preferred = if (kind == "brick") "cycling" else mainSport(weekday, goal, profile)
    val event = if (goal != null && goal.trail) "trail" else null
    // Sin fuerza si el estado del día ha obligado a bajar la carga
    if (profile.strength != "none" && weekday in STRENGTH_DAYS[phase]!! && why.isEmpty()) {
        out.strength = buildStrength(goal?.sport ?: preferred, profile, phase)
        val dayCap = profile.dayMinutes?.get(weekday)
        // Hacia una carrera manda la resistencia: la fuerza solo si deja al menos la mitad del día (y 20′) a la principal
        if (dayCap != null && dayCap != 0 && out.strength!!.durationMin > min(dayCap - 20, dayCap / 2)) {
            out.strength = null
            reasons += "Sin fuerza hoy: no cabe en tus $dayCap min junto a la sesión principal [Inferencia]"
        }
    }
    // Minutos que le quedan a la sesión principal (descontada la fuerza): la sesión tiene que caber
    var cap = profile.dayMinutes?.get(weekday)
    if (cap != null && cap != 0) cap = max(cap - (out.strength?.durationMin ?: 0), 20)
    val limit = cap?.takeIf { it != 0 }

    fun build(sport: String, startVolume: Double): Session? {
        var vol = startVolume
        var session = buildSession(kind, sport, profile, vol, phase, week, event, longCap(goal, sport))
        repeat(3) {  // la duración no es lineal con el volumen (bloques fijos): se ajusta
            if (session == null || limit == null || session!!.durationMin <= limit) return session
            vol *= limit.toDouble() / session!!.durationMin
            session = buildSession(kind, sport, profile, vol, phase, week, event, longCap(goal, sport))
        }
        return session
    }

    val order = listOf(preferred) + profile.activeSports.filter { it != preferred }
    var sessions = order.mapNotNull { s -> build(s, volume)?.let { s to it } }
    // Las series tienen una parte fija: si ni así caben en tus minutos, hoy toca la versión continua
    val first = sessions.firstOrNull()?.second
    if (first != null && limit != null && first.durationMin > limit && kind in QUALITY + "neuromuscular") {
        kind = DOWNGRADE[kind]!!
        sessions = order.mapNotNull { s -> build(s, volume)?.let { s to it } }
        reasons += "Hoy sin series: la sesión de calidad no cabe en tus $limit min [Inferencia]"
    }
    // Lo que no cabe en el día no se propone: primero la versión corta (recuperación); si tampoco, hoy solo fuerza o descanso
    val dayCap = profile.dayMinutes?.get(weekday)?.takeIf { it != 0 }
    if (dayCap != null && sessions.isNotEmpty() && sessions.none { it.second.durationMin <= dayCap } && kind != "recovery") {
        kind = "recovery"
        sessions = order.mapNotNull { s -> build(s, volume)?.let { s to it } }
        reasons += "Hoy, una sesión corta de recuperación: es lo que cabe en tus $dayCap min [Inferencia]"
    }
    if (dayCap != null) sessions = sessions.filter { it.second.durationMin <= dayCap }
    if (sessions.isEmpty()) {
        if (dayCap != null) reasons += "Hoy no cabe una sesión de resistencia en tus $dayCap min [Inferencia]"
        return out
    }
    out.main = sessions.first().second
    // Algunas sesiones no bajan de su parte fija (calentamiento y series): si con la fuerza no cabe, fuera la fuerza
    if (dayCap != null && out.strength != null && out.main!!.durationMin + out.strength!!.durationMin > dayCap) {
        out.strength = null
        reasons += "Sin fuerza hoy: no cabe en tus $dayCap min junto a la sesión principal [Inferencia]"
    }
    val target = out.main!!.tss
    for ((other, first) in sessions.drop(1)) {
        // Alternativa de carga equivalente (±15 %): el TSS no es lineal con la duración, se itera
        var alt: Session? = first
        var altVolume = volume
        for (i in 0 until 3) {
            val a = alt!!
            if (a.tss == 0 || abs(a.tss - target) <= 0.15 * target || (limit != null && a.durationMin >= limit)) break
            altVolume *= target.toDouble() / a.tss
            alt = build(other, altVolume)
            if (alt == null) break
        }
        alt?.let { out.alternatives += it }
    }
    return out
}

/** Rampa de CTL objetivo por semana según el CTL actual (MOTOR §9.1). [Práctica: TrainingPeaks] */
private val RAMP = listOf(45.0 to 3.0, 70.0 to 4.0, Double.POSITIVE_INFINITY to 5.0)

/** TSS semanal para subir el CTL la rampa objetivo: 7 × (CTL + r / (1 − e^(−7/42))). */
fun weeklyTssTarget(ctl: Double, recoveryWeek: Boolean = false): Double {
    val ramp = RAMP.first { ctl <= it.first }.second
    val target = 7 * (ctl + ramp / (1 - exp(-7.0 / 42)))
    return if (recoveryWeek) target * 0.65 else target  // [Práctica] descarga = −35 %
}

fun dayTss(day: Day): Double = (day.main?.tss ?: 0).toDouble() + (day.strength?.tss ?: 0)

/** Peso de cada tipo de sesión en el TSS semanal (MOTOR §9.1). [Inferencia] */
val WEIGHTS = mapOf("long" to 25, "vo2max" to 15, "threshold" to 15, "race_specific" to 15, "brick" to 15, "test" to 15,
    "aerobic" to 11, "neuromuscular" to 11, "recovery" to 5, "tempo" to 15, "anaerobic" to 11)

/**
 * Plan de N días: TSS semanal según la rampa de CTL, repartido por tipo de sesión. Solo el día 1 usa el estado real; cada
 * bloque de 7 días parte del CTL proyectado con la carga planificada de los anteriores (antes, los días 8–14 repetían la
 * escala de hoy). [Inferencia: la proyección supone que se hace lo planificado]
 */
fun plan(start: LocalDate, days: Int, profile: Profile, goal: Goal?, todayState: DayState? = null): List<Day> {
    val scales = mutableMapOf<LocalDate, Double>()
    var ctl = todayState?.ctl
    for (chunk in 0 until (days + 6) / 7) {
        val from = start.plusDays(7 * chunk)
        val (phase, recoveryWeek) = phaseFor(from, goal, profile.planStart)
        val week = (0 until 7).map { proposeDay(from.plusDays(it), profile, goal) }
        if (ctl == null) break
        if (phase != "taper") {  // el taper ya fija su propio volumen
            val mains = week.filter { it.main != null && it.main!!.tss != 0 }
            val target = weeklyTssTarget(ctl, recoveryWeek) - week.sumOf { it.strength?.tss ?: 0 }
            val totalWeight = mains.sumOf { WEIGHTS[it.main!!.type]!! }
            for (d in mains) {
                val dayTarget = target * WEIGHTS[d.main!!.type]!! / totalWeight
                // [Inferencia] límites 0,5-1,5 para no deformar las sesiones del catálogo
                scales[LocalDate.parse(d.date)] = min(max(dayTarget / d.main!!.tss, 0.5), 1.5)
            }
        }
        for (d in week) {  // CTL al final de esta semana si se hace lo planificado (la escala, aproximada sobre la principal)
            val scale = scales[LocalDate.parse(d.date)] ?: 1.0
            ctl = nextLoad(ctl!!, (d.main?.tss ?: 0) * scale + (d.strength?.tss ?: 0), K_CTL)
        }
    }
    val out = (0 until days).map { d ->
        val day = start.plusDays(d)
        proposeDay(day, profile, goal, if (d == 0) todayState else null, scales[day] ?: 1.0)
    }
    scheduleTests(out, profile)
    return out
}

/** Onboarding (RF-03): la primera sesión de calidad pasa a ser el test del deporte sin LTHR o CSS fiable. */
fun scheduleTests(days: List<Day>, profile: Profile) {
    val used = mutableSetOf<String>()
    for (sport in profile.activeSports.filter { it in profile.needsTest }) {
        val probe = buildSession("test", sport, profile) ?: continue  // sin test en el catálogo (aguas abiertas)
        // Solo en un día de calidad donde el test quepa en los minutos disponibles (descontada la fuerza)
        val fits = { d: Day ->
            profile.dayMinutes.isNullOrEmpty() ||
                probe.durationMin + (d.strength?.durationMin ?: 0) <= (profile.dayMinutes[d.weekday] ?: 0)
        }
        val quality = days.filter { it.main != null && it.main!!.type in QUALITY && it.date !in used && fits(it) }
        val day = quality.firstOrNull { it.main!!.sport == sport } ?: quality.firstOrNull() ?: continue
        val test = buildSession("test", sport, profile, phase = day.phase) ?: continue
        day.alternatives.add(0, day.main!!)
        day.main = test
        day.reasons += "Test de $sport: faltan datos fisiológicos fiables para fijar tus zonas [Práctica]"
        used += day.date
    }
}

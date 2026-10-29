package io.github.mrclusterman.trainsync.intervals

// Datos de intervals.icu → entradas del motor. Portado de prototype/backend/app/routers/plan.py y me.py.
// Funciones puras sobre el JSON de la API: el cliente (IntervalsClient) solo descarga.

import io.github.mrclusterman.trainsync.engine.DayState
import io.github.mrclusterman.trainsync.engine.Done
import io.github.mrclusterman.trainsync.engine.Goal
import io.github.mrclusterman.trainsync.engine.Profile
import io.github.mrclusterman.trainsync.engine.ZoneLoad
import io.github.mrclusterman.trainsync.engine.effortMismatch
import io.github.mrclusterman.trainsync.engine.fmt
import io.github.mrclusterman.trainsync.engine.grayZone
import io.github.mrclusterman.trainsync.engine.loadFocus
import io.github.mrclusterman.trainsync.engine.median
import io.github.mrclusterman.trainsync.engine.paceAt
import io.github.mrclusterman.trainsync.engine.paceText
import io.github.mrclusterman.trainsync.engine.plusDays
import io.github.mrclusterman.trainsync.engine.pyRound
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs

internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
internal fun JsonElement?.nums(): List<Double> = (this as? JsonArray)?.map { it.num() ?: 0.0 } ?: emptyList()
internal fun JsonObject.date(): String = this["start_date_local"].str().orEmpty().take(10)

/** Tipos de actividad de intervals.icu → deportes del motor de carrera. */
val RACE_ACTIVITY_SPORTS = mapOf("Run" to "running", "TrailRun" to "running", "VirtualRun" to "running",
    "Ride" to "cycling", "VirtualRide" to "cycling", "GravelRide" to "cycling", "MountainBikeRide" to "cycling",
    "Swim" to "swimming_pool", "OpenWaterSwim" to "swimming_open", "WeightTraining" to "strength")

/** Objetivo prioritario: la carrera futura de mayor prioridad (A > B > C) y, a igual prioridad, la más cercana. */
fun pickGoal(goals: List<Goal>, today: LocalDate): Goal? =
    goals.filter { it.date >= today }.minWithOrNull(compareBy({ it.priority }, { it.date }))

/** Carrera del calendario de intervals.icu (RACE_A/B/C) como objetivo. */
fun raceToGoal(r: JsonObject): Goal {
    val name = r["name"].str()?.ifEmpty { null } ?: "Objetivo"
    val lowered = name.lowercase()
    // [Inferencia] intervals.icu no tiene tipo triatlón ni duatlón: se deduce del nombre
    val sport = when {
        "triat" in lowered -> "triathlon"
        "duat" in lowered -> "duathlon"
        else -> RACE_ACTIVITY_SPORTS[r["type"].str() ?: ""] ?: "running"
    }
    val distance = r["distance"].num()
    return Goal(name = name, sport = sport, date = LocalDate.parse(r.date()),
        priority = (r["category"].str() ?: "RACE_A").takeLast(1),
        distanceKm = if (distance != null && distance != 0.0) distance / 1000 else null,
        trail = r["type"].str() == "TrailRun" || "trail" in lowered)
}

private val SETTING_SPORTS = mapOf("Run" to "running", "Ride" to "cycling", "Swim" to "swimming_pool")

/** LTHR por deporte, FC máx. y de reposo, CSS, ritmo umbral y FTP desde la cuenta de intervals.icu. */
fun fillPhysiology(profile: Profile, athlete: JsonObject): Profile {
    val configLthr = profile.lthr
    var maxHr = profile.maxHr
    val lthrBySport = profile.lthrBySport.toMutableMap()
    val warnings = profile.warnings
    val settings = (athlete["sportSettings"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
    for (s in settings) {
        val types = (s["types"] as? JsonArray)?.map { it.str() } ?: emptyList()
        val sport = types.firstNotNullOfOrNull { SETTING_SPORTS[it] }
        val lthr = s["lthr"].num()
        val settingMax = s["max_hr"].num()
        if (sport == null || lthr == null || lthr == 0.0) continue
        if (maxHr == null || maxHr == 0.0) maxHr = settingMax
        if (configLthr != null && configLthr != 0.0) continue  // la LTHR configurada manda sobre intervals.icu
        // [Inferencia] una LTHR >= 97 % de la FC máx. no es plausible: suele ser un ajuste antiguo o mal configurado
        if (settingMax != null && settingMax != 0.0 && lthr >= 0.97 * settingMax) {
            warnings += "LTHR de $sport en intervals.icu (${num(lthr)}) no es plausible con FC máx ${num(settingMax)}: " +
                "se usa la de otro deporte. Haz el test de LTHR (R-TEST-LTHR) y corrígela en intervals.icu."
            continue
        }
        if (sport !in lthrBySport) lthrBySport[sport] = lthr
    }
    val lthr = configLthr?.takeIf { it != 0.0 } ?: lthrBySport["cycling"] ?: lthrBySport.values.firstOrNull()
    var css = profile.css
    var runPace = profile.runPace
    var ftp = profile.ftp
    // Umbrales de ritmo y potencia (threshold_pace en m/s) para los objetivos que llegan al reloj
    for (s in settings) {
        val types = (s["types"] as? JsonArray)?.map { it.str() } ?: emptyList()
        val pace = s["threshold_pace"].num()?.takeIf { it != 0.0 }
        if ("Swim" in types && pace != null && (css == null || css == 0.0)) css = 100 / pace
        if ("Run" in types && pace != null) runPace = 1000 / pace
        s["ftp"].num()?.takeIf { it != 0.0 && "Ride" in types }?.let { ftp = it }
    }
    // Onboarding (RF-03): test en los deportes sin LTHR propia o sin CSS
    val needsTest = profile.needsTest.toMutableSet()
    for (sport in listOf("running", "cycling")) {
        if (sport in profile.activeSports && sport !in lthrBySport && (configLthr == null || configLthr == 0.0)) needsTest += sport
    }
    if ("swimming_pool" in profile.activeSports && (css == null || css == 0.0)) needsTest += "swimming_pool"
    return profile.copy(lthr = lthr, lthrBySport = lthrBySport, maxHr = maxHr,
        restHr = profile.restHr?.takeIf { it != 0.0 } ?: athlete["icu_resting_hr"].num(),
        css = css, runPace = runPace, ftp = ftp, needsTest = needsTest)
}

/** Número como lo escribe Python: 170 si es entero, 170.5 si no. */
private fun num(x: Double): String = if (x == kotlin.math.floor(x)) x.toLong().toString() else x.toString()

/** Estado del día: carga (CTL/ATL), HRV, sueño, días seguidos, reparto de la carga y señales de fatiga. */
fun dayState(wellness: List<JsonObject>, activities: List<JsonObject>, today: LocalDate): DayState {
    val rows = wellness.filter { it["ctl"].num() != null }.sortedBy { it["id"].str() }
    check(rows.isNotEmpty()) { "intervals.icu no devolvió wellness con CTL/ATL" }
    val last = rows.last()
    val trained = activities.filter { !it["start_date_local"].str().isNullOrEmpty() }.map { it.date() }.toSet()
    var streak = 0
    while (today.plusDays(-(streak + 1)).toString() in trained) streak += 1
    val byDay = wellness.associate { it["id"].str() to it["hrv"].num() }
    val recent = activities.filter { it.date() >= today.plusDays(-2).toString() }
    val daysAgo = trained.filter { it <= today.toString() }.map { LocalDate.parse(it).daysUntil(today) }
    val month = activities.filter { it.date() > today.plusDays(-28).toString() }
    // Horas de sueño: la de hoy es la noche pasada (el sueño se apunta el día en que te despiertas)
    val sleepByDay = wellness.associate { it["id"].str() to it["sleepSecs"].num()?.takeIf { s -> s > 0 } }
    val usualSleep = (1..28).mapNotNull { sleepByDay[today.plusDays(-it).toString()] }.takeIf { it.size >= 7 }?.let { median(it) / 3600 }
    return DayState(
        ctl = last["ctl"].num()!!, atl = last["atl"].num()!!,
        loadFocus = loadFocus(month.map { ZoneLoad(it["icu_hr_zone_times"].nums(), it["icu_training_load"].num() ?: 0.0) }),
        daysSinceActivity = daysAgo.minOrNull(),
        effortMismatch = recent.any { effortMismatch(it["icu_rpe"].num(), it["icu_hr_zone_times"].nums()) },
        hrvHistory = (34 downTo 0).map { byDay[today.plusDays(-it).toString()] },
        sleepScore = if (last["id"].str() == today.toString()) last["sleepScore"].num() else null,
        sleepHours = sleepByDay[today.toString()]?.div(3600),
        usualSleepHours = usualSleep,
        consecutiveDays = streak,
    )
}

data class LastActivity(val name: String?, val date: String, val sport: String, val durationMin: Int, val tss: Double?,
                        val hrZoneSecs: List<Double>, val grayZone: Boolean)

fun lastActivity(activities: List<JsonObject>): LastActivity? {
    val a = activities.filter { !it["start_date_local"].str().isNullOrEmpty() }
        .maxByOrNull { it["start_date_local"].str()!! } ?: return null
    val zones = a["icu_hr_zone_times"].nums()
    return LastActivity(a["name"].str(), a.date(), RACE_ACTIVITY_SPORTS[a["type"].str()] ?: "other",
        pyRound((a["moving_time"].num() ?: 0.0) / 60), a["icu_training_load"].num(), zones, grayZone(zones))
}

/** Aviso si tu LTHR de carrera y tu ritmo umbral no encajan con lo que predicen tus carreras. [Inferencia: > 5 %] */
fun runModelWarning(profile: Profile): String? {
    val model = profile.runModel ?: return null
    val lthr = profile.lthrFor("running")?.takeIf { it != 0.0 } ?: return null
    val runPace = profile.runPace?.takeIf { it != 0.0 } ?: return null
    val predicted = paceAt(lthr, model)
    if (abs(predicted / runPace - 1) <= 0.05) return null
    return "Con tu LTHR de carrera (${fmt(lthr, 0)}) tus carreras predicen ${paceText(predicted)}/km en umbral, pero tu " +
        "ritmo umbral en intervals.icu es ${paceText(runPace)}/km. Una de las dos está desajustada: haz el test de LTHR " +
        "(R-TEST-LTHR)."
}

const val RUNS_FOR_MODEL = 12  // carreras recientes para la relación FC-velocidad [Inferencia]

/** Las carreras con FC de las que sale la relación FC-velocidad, de la más antigua a la más reciente. */
fun runsForModel(activities: List<JsonObject>): List<JsonObject> = activities
    .filter { it["type"].str() == "Run" && (it["average_heartrate"].num() ?: 0.0) != 0.0 }
    .sortedBy { it["start_date_local"].str() }.takeLast(RUNS_FOR_MODEL)

/** Tipos de actividad → deportes del plan de salud, para detectar sesiones hechas sin pulsar nada. */
val HEALTH_ACTIVITY_SPORTS = mapOf("Run" to "running", "TrailRun" to "running", "VirtualRun" to "running",
    "Ride" to "cycling", "VirtualRide" to "cycling", "GravelRide" to "cycling", "MountainBikeRide" to "cycling",
    "EBikeRide" to "cycling", "Swim" to "swimming_pool", "OpenWaterSwim" to "swimming_pool", "Walk" to "walking",
    "Hike" to "walking", "WeightTraining" to "strength")
const val MIN_ACTIVITY_S = 600  // menos de 10 min no cuenta como sesión [Inferencia]

/** Actividades del reloj por día, para matchDay. */
fun watchSessions(activities: List<JsonObject>): Map<String, List<Done>> {
    val done = linkedMapOf<String, MutableList<Done>>()
    for (a in activities) {
        val sport = HEALTH_ACTIVITY_SPORTS[a["type"].str()] ?: continue
        val start = a["start_date_local"].str().orEmpty()
        val moving = a["moving_time"].num() ?: 0.0
        if (moving >= MIN_ACTIVITY_S && start.isNotEmpty()) {
            done.getOrPut(start.take(10)) { mutableListOf() } +=
                Done(sport, "watch", (moving / 60).toInt(), start.drop(11).take(5).ifEmpty { null })
        }
    }
    return done
}

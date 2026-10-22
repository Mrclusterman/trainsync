package io.github.mrclusterman.trainsync.app

// Datos de salud del propio móvil (Health Connect en Android, Apple Salud en iOS): la app funciona sin intervals.icu
// (docs/VISION.md §7, fase 3). Cada plataforma lee sus registros y los entrega con esta forma común; aquí se convierten
// al formato que ya usa el motor, así el plan, el calendario y Progreso no distinguen de dónde vienen.

import io.github.mrclusterman.trainsync.engine.K_ATL
import io.github.mrclusterman.trainsync.engine.K_CTL
import io.github.mrclusterman.trainsync.engine.hrTss
import io.github.mrclusterman.trainsync.engine.nextLoad
import io.github.mrclusterman.trainsync.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Un día de datos del reloj o la báscula, tal como los guarda el sistema. */
@Serializable
data class DailyHealth(
    val date: String,  // AAAA-MM-DD
    val sleepMinutes: Double? = null,
    val hrv: Double? = null,  // ms (RMSSD en Health Connect; SDNN en Apple Salud)
    val restingHr: Double? = null,
    val weightKg: Double? = null,
    val steps: Double? = null,
    val vo2max: Double? = null,
)

/** Un entrenamiento registrado en el móvil. `zoneSeconds`: tiempo en las 7 zonas de FC, si se pudo calcular. */
@Serializable
data class Workout(
    val id: String,
    val sport: String,  // walking / running / cycling / swimming_pool / strength / other
    val start: String,  // AAAA-MM-DDTHH:MM:SS, hora local
    val minutes: Double,
    val avgHr: Double? = null,
    val maxHr: Double? = null,
    val zoneSeconds: List<Double>? = null,
)

@Serializable
data class HealthSnapshot(
    val source: String,  // "Health Connect" o "Apple Salud"
    val days: List<DailyHealth>,
    val workouts: List<Workout>,
    val syncedAt: String,
)

private val ACTIVITY_TYPE = mapOf("running" to "Run", "cycling" to "Ride", "swimming_pool" to "Swim",
    "walking" to "Walk", "strength" to "WeightTraining")

/** FC máxima estimada por la edad (Tanaka 2001: 208 − 0,7 × edad) cuando no hay una medida. [Verificado: Tanaka 2001] */
fun estimatedMaxHr(age: Int): Double = 208 - 0.7 * age

/**
 * Los datos del móvil en el formato de intervals.icu que usa el motor. La carga de cada entrenamiento se estima con la
 * FC media (hrTSS, umbral ≈ 90 % de la FC máx.) o, sin pulso, a ~50 por hora (esfuerzo moderado); con ella salen la
 * forma física (CTL) y la fatiga (ATL) de cada día. [Inferencia: el 90 % y los 50/h son aproximaciones]
 */
fun healthCache(snapshot: HealthSnapshot, profile: UserProfile?, today: LocalDate): IntervalsCache {
    val age = profile?.birthYear?.let { today.year - it } ?: 40
    val measuredMax = snapshot.workouts.mapNotNull { it.maxHr }.maxOrNull()
    val maxHr = maxOf(measuredMax ?: 0.0, estimatedMaxHr(age))
    val lthr = 0.9 * maxHr
    val rest = snapshot.days.mapNotNull { it.restingHr }.takeIf { it.isNotEmpty() }?.average() ?: 60.0

    fun load(w: Workout): Double = w.avgHr?.takeIf { it > rest }?.let { hrTss(w.minutes * 60, it, rest, lthr) }
        ?: (w.minutes / 60 * 50)

    val loadByDay = snapshot.workouts.groupBy { it.start.take(10) }.mapValues { (_, ws) -> ws.sumOf { load(it) } }
    val byDay = snapshot.days.associateBy { it.date }
    val first = (snapshot.days.map { it.date } + loadByDay.keys).minOrNull()?.let { LocalDate.parse(it) } ?: today
    var ctl = 0.0
    var atl = 0.0
    val wellness = mutableListOf<JsonObject>()
    var d = first
    while (d <= today) {
        val key = d.toString()
        val tss = loadByDay[key] ?: 0.0
        ctl = nextLoad(ctl, tss, K_CTL)
        atl = nextLoad(atl, tss, K_ATL)
        val h = byDay[key]
        wellness += buildJsonObject {
            put("id", key)
            put("ctl", ctl)
            put("atl", atl)
            h?.sleepMinutes?.let { put("sleepSecs", it * 60) }
            h?.hrv?.let { put("hrv", it) }
            h?.restingHr?.let { put("restingHR", it) }
            h?.weightKg?.let { put("weight", it) }
            h?.steps?.let { put("steps", it) }
            h?.vo2max?.let { put("vo2max", it) }
        }
        d = d.plusDays(1)
    }
    val activities = snapshot.workouts.mapNotNull { w ->
        val type = ACTIVITY_TYPE[w.sport] ?: return@mapNotNull null
        buildJsonObject {
            put("id", w.id)
            put("type", type)
            put("start_date_local", w.start)
            put("moving_time", w.minutes * 60)
            put("icu_training_load", load(w))
            w.avgHr?.let { put("average_heartrate", it) }
            w.zoneSeconds?.let { z -> put("icu_hr_zone_times", JsonArray(z.map { JsonPrimitive(it) })) }
        }
    }
    val athlete = buildJsonObject { put("icu_resting_hr", rest) }
    return IntervalsCache(athlete, wellness, activities, snapshot.syncedAt)
}

/** Los límites de las 7 zonas de FC (ppm) con los que cada plataforma reparte los pulsos de un entrenamiento. */
fun heartRateZoneBounds(profile: UserProfile?, today: LocalDate, measuredMaxHr: Double?): List<Double> {
    val age = profile?.birthYear?.let { today.year - it } ?: 40
    val lthr = 0.9 * maxOf(measuredMaxHr ?: 0.0, estimatedMaxHr(age))
    // Límites superiores de Z1, Z2, Z3, Z4, Z5a y Z5b en % de la LTHR (como intervals.icu con zonas de Friel)
    return listOf(0.81, 0.89, 0.93, 0.99, 1.02, 1.06).map { it * lthr }
}

/** Los datos del reloj o del móvil, sea cual sea la fuente: intervals.icu primero (es más completo), si no el móvil. */
fun AppData.deviceData(today: LocalDate): IntervalsCache? = cache ?: health?.let { healthCache(it, profile, today) }

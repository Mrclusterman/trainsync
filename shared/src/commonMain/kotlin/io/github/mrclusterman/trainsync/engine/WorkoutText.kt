package io.github.mrclusterman.trainsync.engine

// Sesión de Brivel → entrenamiento estructurado de intervals.icu, que lo pasa al reloj o al ciclocomputador.
// Portado de prototype/backend/app/services/workout_text.py.
//
// Cada paso lleva un objetivo que intervals.icu convierte en límites (el reloj avisa al salirse del rango). Sintaxis
// (forum.intervals.icu/t/workout-builder/1163): pasos con "- ", duración "10m"/"30s"/"1m30", "72.5-80.2% HR" (de la FC
// máx.), "56-75%" (del FTP), repeticiones con una línea "6x" antes de los pasos; ritmo absoluto "4:50-4:40/km Pace" o
// "/100m Pace". El texto anterior a la duración es el aviso del paso. Formatos comprobados contra la API el 2026-10-21.
// Una sola métrica por sesión: al reloj solo llega la principal y mezclar ritmo y FC pierde la segunda.

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val SPORT_TYPES = mapOf("running" to "Run", "cycling" to "Ride", "swimming_pool" to "Swim", "swimming_open" to "Swim",
    "walking" to "Walk")

/** Running por ritmo sin modelo FC-velocidad: % del ritmo umbral (más % = más lento). [Práctica: Friel] */
private val RUN_PACE_ZONES = mapOf("Z1" to (1.29 to 1.60), "Z2" to (1.14 to 1.29), "Z3" to (1.06 to 1.13),
    "Z4" to (0.99 to 1.05), "Z5" to (0.90 to 1.00))

/** Potencia en % del FTP (Coggan); el límite bajo de Z1 es [Inferencia]. */
private val POWER_ZONES = mapOf("Z1" to (40 to 55), "Z2" to (56 to 75), "Z3" to (76 to 90), "Z4" to (91 to 105),
    "Z5" to (106 to 120), "Z6" to (121 to 150))
private val PACE_FIRST = setOf("vo2max", "threshold", "race_specific", "tempo")  // sin modelo: solo la calidad [Práctica]

fun stepDuration(minutes: Double): String {
    var s = pyRound(minutes * 60)
    if (s >= 120) s = pyRound(s / 5.0) * 5  // el bloque de relleno sale con segundos sueltos: a 5 s se lee mejor
    if (s < 60) return "${s}s"
    return "${s / 60}m" + if (s % 60 != 0) "${s % 60}" else ""
}

fun paceText(seconds: Double): String = "${floorDiv(seconds, 60.0).toInt()}:${twoDigits(pyRound(seconds % 60))}"

/** La métrica de toda la sesión: pace, power, hr o rpe (sin objetivo en el reloj). */
fun metric(session: Session, profile: Profile): String {
    val sport = session.sport
    val kind = session.type
    if (kind == "test") return "rpe"  // un objetivo con el umbral que se mide sesgaría el test
    if (sport in SWIM) return if (profile.css != null && profile.css != 0.0) "pace" else "rpe"
    if (sport == "cycling" && profile.ftp != null && profile.ftp != 0.0) return "power"
    val lthr = profile.lthrFor(sport)
    if (sport == "running" && !session.id.startsWith("T-")) {  // en trail el ritmo no sirve: por FC
        // Como Garmin: con tu relación FC-velocidad, toda la carrera va por ritmo; sin ella, solo la calidad
        if ((profile.runModel != null && lthr != null && lthr != 0.0) ||
            (profile.runPace != null && profile.runPace != 0.0 && kind in PACE_FIRST)) return "pace"
    }
    return if (lthr != null && lthr != 0.0 && profile.maxHr != null && profile.maxHr != 0.0) "hr" else "rpe"
}

/** Objetivo del paso en sintaxis de intervals.icu; null si se hace por sensación (Z6 o sin umbral). */
fun stepTarget(zone: String, session: Session, profile: Profile, mode: String): String? {
    val sport = session.sport
    if (mode == "rpe" || (zone == "Z6" && mode != "power")) return null  // en esfuerzos máximos cortos manda el RPE
    if (mode == "power") {
        val (lo, hi) = POWER_ZONES[zone]!!
        return "$lo-$hi%"
    }
    if (mode == "pace" && sport in SWIM) {
        val (fast, slowOrNull) = SWIM_ZONES[zone]!!  // factores del CSS: (más rápido, más lento)
        val slow = slowOrNull ?: (fast!! + 0.15)  // Z1 abierta: 15 % más lenta como límite [Inferencia]
        return "${paceText(profile.css!! * slow)}-${paceText(profile.css * fast!!)}/100m Pace"  // más lento primero
    }
    val lthr = profile.lthrFor(sport)
    val bounds = if (lthr != null && lthr != 0.0) hrBounds(zone, sport, lthr, profile.maxHr, session.hrCap) else null
    if (mode == "pace" && profile.runModel != null && bounds != null) {
        // Ritmos desde tus zonas de FC con tu relación FC-velocidad (motor §13)
        return "${paceText(paceAt(bounds.first, profile.runModel))}-${paceText(paceAt(bounds.second, profile.runModel))}/km Pace"
    }
    if (mode == "pace") {
        val (fast, slow) = RUN_PACE_ZONES[zone]!!
        return "${paceText(profile.runPace!! * slow)}-${paceText(profile.runPace * fast)}/km Pace"
    }
    // FC: las zonas de Brivel en % de la FC máx., porque intervals.icu no acepta ppm y "% LTHR" usaría la LTHR
    // guardada en intervals.icu, que puede no ser la que usa Brivel
    val (lo, hi) = bounds!!
    val pct = { bpm: Double -> fmt(100 * bpm / profile.maxHr!!, 1).trimEnd('0').trimEnd('.') }
    return "${pct(lo)}-${pct(hi)}% HR"
}

/** Texto de la sesión: objetivo, notas de cada bloque y los pasos. */
fun describe(session: Session, profile: Profile): String {
    val mode = metric(session, profile)
    val lines = mutableListOf(session.goal)
    // Las notas van sin guion: así su texto ("6×20″", "15 %") no se interpreta como duración u objetivo
    lines += session.blocks.filter { !it.detail.isNullOrEmpty() }.map { "${capitalize(it.block)}: ${it.detail}" }

    // Natación con CSS: en metros ("mtr" en intervals.icu, porque "m" son minutos)
    fun step(name: String, minutes: Double, zone: String, meters: Int? = null): String {
        val length = meters?.let { "${it}mtr" } ?: stepDuration(minutes)
        val goal = stepTarget(zone, session, profile, mode)
            // intervals.icu descarta "RPE x" detrás de la duración: delante pasa al aviso del paso
            ?: return "- $name RPE ${RPE_BY_ZONE[zone]} $length"
        return "- $name $length $goal"
    }

    for (b in session.blocks) {
        if (b.reps > 1) {
            lines += listOf("", "${b.reps}x", step(b.block, b.minutes, b.zone, b.meters))
            val rest = b.restMinutes
            if (rest != null && rest != 0.0 && session.sport in SWIM) {
                lines += "- descanso ${stepDuration(rest)}"  // en la piscina se descansa parado: sin objetivo
            } else if (rest != null && rest != 0.0) {
                lines += step("recuperación", rest, b.restZone ?: "Z1")
            }
            lines += ""
        } else {
            lines += step(b.block, b.minutes, b.zone, b.meters)
        }
    }
    return lines.joinToString("\n").replace("\n\n\n", "\n\n").trim() + "\n"
}

/** `str.capitalize()` de Python: primera letra en mayúscula y el resto en minúscula. */
private fun capitalize(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }

@Serializable
data class WorkoutEvent(
    val category: String = "WORKOUT",
    @SerialName("start_date_local") val startDateLocal: String,
    val type: String,
    val name: String,
    val description: String,
    @SerialName("moving_time") val movingTime: Int,
    @SerialName("external_id") val externalId: String,
)

/** Evento para POST /events/bulk?upsert=true: un external_id por día (y por clave), así reenviar lo actualiza. */
fun workoutEvent(session: Session, day: String, profile: Profile, key: String? = null) = WorkoutEvent(
    startDateLocal = "${day}T00:00:00", type = SPORT_TYPES[session.sport]!!, name = "Brivel · ${session.name}",
    description = describe(session, profile), movingTime = session.durationMin * 60,
    externalId = "trainsync-$day" + (key?.let { "-$it" } ?: ""),
)

/** Fuerza como entrenamiento de pesas: lista de ejercicios sin guiones, para que no se lea como pasos con objetivo. */
fun strengthEvent(session: HealthStrength, day: String) = WorkoutEvent(
    startDateLocal = "${day}T00:00:00", type = "WeightTraining", name = "Brivel · ${session.name}",
    description = (session.exercises.map { "• $it" } + "" + session.notes).joinToString("\n"),
    movingTime = session.durationMin * 60, externalId = "trainsync-$day-strength",
)

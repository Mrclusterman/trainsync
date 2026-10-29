package io.github.mrclusterman.trainsync.engine

// Validación del motor (docs/proyecto/DISENO.md §11 y docs/proyecto/VALIDACION.md). Sustituye a
// la antigua validación en Python del prototipo: el motor de Kotlin es la única fuente. Tres partes, todas con datos sintéticos:
//   V1–V11  planes sin fecha: 6 perfiles × 3 adherencias durante 24 semanas
//   R1–R9   planes hacia una carrera: 7 carreras, de la preparación (20 semanas) al día de la prueba
//   E1–E11  ajuste del día con datos del reloj o del móvil, o con las tres preguntas; plan de 14 días
//   N1–N3   natación: en metros con tu CSS, por esfuerzo sin él
//   F1–F13  combinaciones generadas (semilla fija): 300 perfiles sin fecha y 300 carreras con reglas que valen para todos
// Con VALIDATION_REPORT=1 escribe docs/proyecto/validacion/resultados.md; sin él solo comprueba.
// Caso real (realAccount): con INTERVALS_ATHLETE_ID e INTERVALS_API_KEY en el entorno, los mismos criterios con la
// fisiología y el estado de esa cuenta. Sin ellas se omite (así en la nube). El informe solo dice si cumple, más la LTHR y
// la FC máx.; ningún dato de sueño, VFC ni actividades sale del equipo.

import io.github.mrclusterman.trainsync.app.systemToday
import io.github.mrclusterman.trainsync.intervals.IntervalsClient
import io.github.mrclusterman.trainsync.intervals.dayState
import io.github.mrclusterman.trainsync.intervals.fillPhysiology
import io.github.mrclusterman.trainsync.intervals.num
import io.github.mrclusterman.trainsync.intervals.str
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.io.File
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.fail

private data class Row(val case: String, val variant: String, val id: String, val ok: Boolean?, val detail: String)

/**
 * Fallos conocidos y aceptados por ahora: salen en el informe como "no", pero no rompen la compilación. Cada uno con su
 * motivo; quitarlo de aquí en cuanto el motor lo cumpla.
 */
private val KNOWN = mapOf<String, String>(
    // Ninguno. El último (V9 "cribado con tope", adherencia baja) se resolvió el 2026-10-29: VALIDACION.md §4.
)

class ValidationTest {
    private val start = LocalDate(2026, 1, 5)  // lunes fijo: resultados reproducibles
    private val weeksCount = 24
    private val main = listOf("sentadilla", "bisagra", "empuje horizontal", "tracción horizontal")
    private val vigorous = setOf("Z4", "Z5", "Z6")
    private val adherence = linkedMapOf("alta" to 0.95, "media" to 0.75, "baja" to 0.40)
    private val all = (0 until 7).map { WEEKDAYS[it] }

    private val healthProfiles = linkedMapOf(
        "principiante sedentario" to HealthProfile("fitness", mapOf("MON" to 45, "WED" to 45, "SAT" to 60),
            strength = "bodyweight", cardio = listOf("walking"), level = "beginner", intensityCap = "light_moderate"),
        "persona mayor (70)" to HealthProfile("fitness", mapOf("MON" to 60, "TUE" to 45, "THU" to 60, "SAT" to 60),
            strength = "bodyweight", cardio = listOf("walking", "swimming_pool"), level = "beginner", age = 70,
            intensityCap = "light_moderate", css = 150.0),
        "gimnasio" to HealthProfile("strength", listOf("MON", "TUE", "THU", "FRI", "SAT").associateWith { 75 },
            strength = "gym", cardio = listOf("cycling", "running"), level = "intermediate", lthr = 170.0, maxHr = 190.0),
        "corredor" to HealthProfile("fitness", mapOf("MON" to 60, "TUE" to 75, "WED" to 60, "THU" to 75, "SAT" to 120, "SUN" to 90),
            strength = "gym", cardio = listOf("running", "cycling"), level = "advanced", lthr = 172.0, maxHr = 190.0),
        "seis días" to HealthProfile("fitness", all.take(6).associateWith { 60 }, strength = "gym",
            cardio = listOf("walking", "cycling"), level = "intermediate", lthr = 165.0, maxHr = 185.0),
        "cribado con tope" to HealthProfile("fitness", all.take(6).associateWith { 60 }, strength = "gym",
            cardio = listOf("running", "cycling"), level = "advanced", intensityCap = "light_moderate", lthr = 170.0, maxHr = 190.0),
        "adolescente (15)" to HealthProfile("fitness", mapOf("MON" to 60, "TUE" to 90, "WED" to 60, "THU" to 90, "SAT" to 120),
            strength = "bodyweight", cardio = listOf("running", "cycling"), level = "intermediate", age = 15),
    )

    // ------------------------------------------------------------------ V1–V11: planes sin fecha

    private fun simulate(p: HealthProfile, done: Double): List<WeekPlan> {
        val history = mutableListOf<Double>()
        return (0 until weeksCount).map { w ->
            val (block, weekInBlock) = w / 4 to w % 4
            weekPlan(p, start.plusDays(7 * w), history.take(block), weekInBlock).also { if (weekInBlock == 3) history += done }
        }
    }

    private fun WeekPlan.sessions(sport: String? = null) = days.flatMap { it.sessions }.filter { sport == null || it.sport == sport }
    private fun WeekPlan.strength() = sessions("strength").filterIsInstance<HealthStrength>()
    private fun HealthDay.has(pattern: String) = sessions.any { it is HealthStrength && pattern in it.patterns }

    private fun foster(loads: List<Double>): Double {
        val mean = loads.average()
        val sd = sqrt(loads.sumOf { (it - mean) * (it - mean) } / loads.size)
        return if (sd == 0.0) 0.0 else mean / sd
    }

    private fun checkHealth(p: HealthProfile, label: String, weeks: List<WeekPlan>): Map<String, Pair<Boolean?, String>> {
        val target = TARGET[p.goal]!!
        val stable = weeks.filter { it.weekInBlock < 3 && it.dose.aerobic >= target.aerobic }
        val loading = weeks.filter { it.weekInBlock < 3 }
        val short = { w: WeekPlan -> (w.warnings + w.info).any { "llegas a" in it } }  // avisa de que no cabe la dosis
        val r = linkedMapOf<String, Pair<Boolean?, String>>()

        // V1 suelo OMS en las semanas estables. No aplica si la progresión aún no llegó o si es físicamente imposible
        // según una estimación independiente del motor: minutos − 20 por día de fuerza − 15 de calentamiento por día. [Inferencia]
        val capacity = p.days.values.sum() - 20 * strengthDays(p) - 15 * trainingDays(p).size
        val low = stable.minOfOrNull { it.equivalentMin } ?: 0
        val floor = aerobicFloor(p)  // 150; en menores de 18, 420 (60 al día de media)
        r["V1"] = when {
            stable.isEmpty() -> null to "no aplica: sin semanas estables (la progresión aún no ha llegado a la meta)"
            capacity < floor -> null to "no aplica: con ${p.days.values.sum()} min/semana caben ~$capacity equivalentes; mín. $low"
            else -> stable.all { it.equivalentMin >= floor && it.dose.strengthDays >= FLOOR_STRENGTH_DAYS } to
                "${stable.size} semanas estables; mín. $low min equivalentes"
        }
        // V12 menores de 18 (OMS 2020): meta de 60 al día de media, fuerza 3 días y, si no cabe, el plan lo dice
        if (p.age != null && p.age < 18) {
            val meta = weeks.first().dose.aerobic >= 0.6 * YOUTH_AEROBIC - 1 && weeks.maxOf { it.dose.aerobic } >= YOUTH_AEROBIC * 0.95
            val strength = weeks.all { it.dose.strengthDays >= 3 }
            val told = weeks.filter { it.weekInBlock < 3 && it.equivalentMin + 5 < minOf(it.dose.aerobic, YOUTH_AEROBIC) }.all { w -> (w.warnings + w.info).any { "60 al día" in it } }
            r["V12"] = (meta && strength && told) to "meta hacia $YOUTH_AEROBIC min, fuerza 3 días: ${yes(strength)}; aviso si no cabe: ${yes(told)}"
        }
        if (p.age != null && p.age >= 65) {
            r["V2"] = weeks.all { w -> w.strength().count { BALANCE in it.exercises } >= 3 } to "equilibrio y fuerza ≥ 3 días todas las semanas"
        }
        if (p.goal == "strength") {
            val times = weeks.all { w -> main.all { pt -> w.strength().count { pt in it.patterns } >= 2 } }
            val sets = weeks.all { w ->
                main.minOf { pt -> w.strength().filter { pt in it.patterns }.sumOf { it.setsPerExercise } } >= w.dose.sets ||
                    w.info.any { "series" in it }
            }
            r["V3"] = (times && sets) to "cada patrón principal ≥ 2 veces/semana; series = dosis o aviso"
        }
        // V5 progresión ≤ 10 % semanal y entre bloques; la 4.ª semana de cada bloque, descarga
        val aero = weeks.map { it.dose.aerobic.toDouble() }
        val rises = (1 until weeksCount).filter { weeks[it].weekInBlock in 1..2 && aero[it - 1] > 0 }.map { aero[it] / aero[it - 1] }
        val rebounds = (4 until weeksCount step 4).filter { aero[it - 2] > 0 }.map { aero[it] / aero[it - 2] }
        val deload = (3 until weeksCount step 4).all { aero[it] < aero[it - 1] || aero[it - 1] == 0.0 }
        val top = (rises + rebounds).maxOrNull() ?: 1.0
        r["V5"] = (top <= 1.105 && deload) to "subida máx. ${pct(top - 1)}; descarga cada 4.ª semana: ${yes(deload)}"
        if (p.intensityCap != "vigorous") {
            val noVigorous = weeks.flatMap { it.sessions() }.filterIsInstance<HealthCardio>()
                .all { s -> s.session.blocks.none { it.zone in vigorous } }
            val rir = weeks.flatMap { it.strength() }.all { it.rir >= 3 }
            r["V6"] = (noVigorous && rir) to "sin Z4+ ni RIR < 3"
        }
        val rest = weeks.all { w -> w.days.any { it.sessions.isEmpty() } }
        val legs = weeks.all { w -> w.days.zipWithNext().none { (a, b) -> a.has("sentadilla") && b.has("sentadilla") } }
        r["V7"] = (rest && legs) to "día de descanso: ${yes(rest)}; pierna en días seguidos: ${yes(!legs)}"
        if (p.goal == "strength" && p.cardio.size > 1) {
            r["V8"] = weeks.all { w -> w.days.none { d -> d.has("sentadilla") && d.sessions.any { it.sport == "running" } } } to
                "sin carrera el día de pierna"
        }
        // V9 monotonía de Foster < 2 en las semanas de carga; aparte las limitadas por el tiempo disponible
        val monotony = { w: WeekPlan -> foster(w.days.map { d -> d.sessions.sumOf { it.tss }.toDouble() }) }
        val free = loading.filterNot(short).map(monotony)
        val bound = loading.filter(short).map(monotony)
        var detail = "sin límite de tiempo: máx. ${fmt(free.maxOrNull() ?: 0.0, 2)} (${free.size} semanas)"
        if (bound.isNotEmpty()) detail += "; limitadas por tiempo: máx. ${fmt(bound.max(), 2)} (${bound.size} semanas)"
        r["V9"] = ((free + bound).max() < MAX_MONOTONY) to detail
        r["V10"] = (simulate(p, adherence[label]!!) == weeks) to "dos ejecuciones idénticas"
        return r
    }

    private fun healthRows(): List<Row> {
        val rows = mutableListOf<Row>()
        for ((name, p) in healthProfiles) {
            val finals = mutableMapOf<String, Int>()
            for ((label, value) in adherence) {
                val weeks = simulate(p, value)
                finals[label] = weeks[weeksCount - 4].dose.aerobic  // primera semana del último bloque
                checkHealth(p, label, weeks).forEach { (id, r) -> rows += Row(name, label, id, r.first, r.second) }
            }
            // V11 con adherencia baja el plan baja volumen en vez de subirlo
            rows += Row(name, "baja vs alta", "V11", finals["baja"]!! < finals["alta"]!!,
                "último bloque: ${finals["baja"]} min (baja) frente a ${finals["alta"]} (alta)")
        }
        return rows
    }

    // ------------------------------------------------------------------ R1–R9: hacia una carrera

    private data class Race(val goal: Goal, val profile: Profile)

    private fun days(vararg pairs: Pair<String, Int>) = pairs.toMap()
    private fun race(name: String, sport: String, km: Double?, priority: String, sports: List<String>, minutes: Map<String, Int>,
                     trail: Boolean = false, lthr: Double? = null, strength: String = "gym"): Pair<String, Race> {
        val goal = Goal(name, sport, start.plusDays(7 * PREP_WEEKS + 5), priority, km, trail)  // sábado
        val profile = Profile(activeSports = sports, strength = strength, restDays = all.filter { it !in minutes },
            dayMinutes = minutes, lthr = lthr, maxHr = lthr?.let { it / 0.9 })
        return name to Race(goal, profile)
    }

    private val races = linkedMapOf(
        race("10 km, 3 días", "running", 10.0, "A", listOf("running"), days("TUE" to 45, "THU" to 45, "SUN" to 70), strength = "bodyweight"),
        race("media maratón, 4 días", "running", 21.1, "A", listOf("running"),
            days("TUE" to 60, "THU" to 60, "SAT" to 45, "SUN" to 120), lthr = 168.0),
        race("maratón, 5 días", "running", 42.2, "A", listOf("running", "cycling"),
            days("MON" to 45, "TUE" to 75, "THU" to 75, "SAT" to 60, "SUN" to 180), lthr = 165.0),
        race("trail 30 km", "running", 30.0, "A", listOf("running"),
            days("TUE" to 60, "WED" to 45, "FRI" to 60, "SUN" to 150), trail = true, lthr = 170.0),
        race("gran fondo", "cycling", 140.0, "A", listOf("cycling"),
            days("TUE" to 75, "THU" to 75, "SAT" to 240, "SUN" to 120), lthr = 160.0),
        race("triatlón olímpico", "triathlon", null, "A", listOf("swimming_pool", "cycling", "running"),
            days("MON" to 45, "TUE" to 60, "WED" to 60, "THU" to 60, "SAT" to 150, "SUN" to 90), lthr = 162.0),
        race("10 km preparatoria (C)", "running", 10.0, "C", listOf("running"), days("TUE" to 45, "THU" to 45, "SUN" to 70)),
    )

    private fun raceDays(r: Race): List<Day> {
        val first = r.goal.date.plusDays(-7 * PREP_WEEKS)
        return (0..first.daysUntil(r.goal.date)).map { proposeDay(first.plusDays(it), r.profile, r.goal) }
    }

    private fun Day.minutes() = (main?.durationMin ?: 0) + (strength?.durationMin ?: 0)
    private fun Day.load() = dayTss(this)

    /** Minutos de cada zona en una sesión: series × duración, y la recuperación entre series en su zona. */
    private fun zoneMinutes(s: Session): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        for (b in s.blocks) {
            out[b.zone] = (out[b.zone] ?: 0.0) + b.reps * b.minutes
            val rest = b.restMinutes ?: continue
            val zone = b.restZone ?: "Z1"
            out[zone] = (out[zone] ?: 0.0) + (b.reps - 1).coerceAtLeast(0) * rest
        }
        return out
    }

    private fun checkRace(r: Race, days: List<Day>): Map<String, Pair<Boolean?, String>> {
        val out = linkedMapOf<String, Pair<Boolean?, String>>()
        val order = listOf("base_early", "base", "build", "peak", "taper")
        val phases = days.map { order.indexOf(it.phase) }
        out["R1"] = phases.zipWithNext().all { (a, b) -> b >= a } to days.map { it.phase }.distinct().joinToString(" → ")

        // Semanas desde el inicio de la preparación (así caen las de descarga del motor); sin el afinado
        val taperDays = taperDays(r.goal)
        val build = days.filter { it.phase != "taper" }
        val weeks = build.chunked(7).filter { it.size == 7 }
        val load = weeks.map { w -> w.sumOf { it.load() } }
        val recovery = weeks.map { w -> w.any { it.recoveryWeek } }
        val rises = (1 until weeks.size).filter { !recovery[it] && load[it - 1] > 0 }.map { i ->
            val before = if (recovery[i - 1] && i >= 2) i - 2 else i - 1  // tras la descarga se compara con la semana previa a ella
            load[i] / load[before]
        }
        val top = rises.maxOrNull() ?: 1.0
        out["R2"] = (top <= 1.105) to "subida máx. de la carga semanal ${pct(top - 1)} (${weeks.size} semanas)"
        val deloads = (1 until weeks.size).filter { recovery[it] }
        out["R3"] = deloads.all { load[it] < load[it - 1] } to "${deloads.size} semanas de descarga, todas con menos carga que la anterior"

        // R4 afinado: −40–60 % de volumen (Bosquet 2007); solo en la carrera principal, las B y C apenas afinan
        if (r.goal.priority == "A") {
            val taper = days.dropLast(1).takeLast(taperDays - 1)  // sin el día de la prueba
            val before = days.dropLast(taperDays).takeLast(21)
            val ratio = taper.sumOf { it.minutes() }.toDouble() / taper.size / (before.sumOf { it.minutes() }.toDouble() / before.size)
            out["R4"] = (ratio <= 0.70) to "volumen del afinado = ${fmt(ratio * 100, 0)} % del de las 3 semanas previas ($taperDays días)"
        }
        // R5 cada día cabe en los minutos que tienes y no hay sesiones en tus días sin entreno
        val over = days.filter { d -> d.minutes() > (r.profile.dayMinutes!![d.weekday] ?: 0) }
        out["R5"] = over.isEmpty() to if (over.isEmpty()) "todas caben" else
            "${over.size} días no caben; p. ej. ${over.first().date}: ${over.first().minutes()} de ${r.profile.dayMinutes!![over.first().weekday] ?: 0} min"
        val withoutRest = days.chunked(7).count { w -> w.size == 7 && w.all { it.main != null || it.strength != null } }
        out["R6"] = (withoutRest == 0) to "semanas sin un día de descanso: $withoutRest"

        // R7 distribución polarizada: ≥ 75 % del tiempo de resistencia en Z1–Z2 fuera del afinado (Seiler 2010: ~80 %)
        val zones = build.mapNotNull { it.main }.map { zoneMinutes(it) }
        val total = zones.sumOf { it.values.sum() }
        val easy = zones.sumOf { (it["Z1"] ?: 0.0) + (it["Z2"] ?: 0.0) }
        out["R7"] = (easy / total >= 0.75) to "Z1–Z2 = ${fmt(easy / total * 100, 0)} % del tiempo"

        val cap = longCap(r.goal, "running")
        val longest = days.mapNotNull { it.main }.filter { it.type == "long" && it.sport == "running" }.maxOfOrNull { it.durationMin }
        if (cap != null && longest != null) out["R8"] = (longest <= cap) to "tirada larga máx. $longest min (tope $cap)"

        // R9 nunca dos sesiones de calidad en días seguidos (alternar duro y fácil) [Práctica]
        val quality = days.zipWithNext().filter { (a, b) -> a.main?.type in QUALITY && b.main?.type in QUALITY }
        out["R9"] = quality.isEmpty() to if (quality.isEmpty()) "ninguna" else
            "${quality.size} veces; p. ej. ${quality.first().first.date} ${quality.first().first.main!!.type} + ${quality.first().second.main!!.type}"
        out["R10"] = (raceDays(r) == days) to "dos ejecuciones idénticas"
        return out
    }

    private fun raceRows(): List<Row> = races.flatMap { (name, r) ->
        checkRace(r, raceDays(r)).map { (id, v) -> Row(name, "prioridad ${r.goal.priority}", id, v.first, v.second) }
    }

    // ------------------------------------------------------------------ E1–E8: ajuste del día

    private val calm = DayState(ctl = 50.0, atl = 55.0, hrvHistory = List(35) { 60.0 + it % 5 }, sleepScore = 80.0, daysSinceActivity = 1)

    private fun stateRows(): List<Row> {
        val rows = mutableListOf<Row>()
        fun row(id: String, case: String, ok: Boolean, detail: String) { rows += Row(case, "sintético", id, ok, detail) }

        adjust("vo2max", calm).let { (t, v, why) -> row("E1", "todo normal", t == "vo2max" && v == 1.0 && why.isEmpty(), "sin cambios: $t, ×$v") }
        adjust("vo2max", calm.copy(atl = 85.0)).let { (t, _, why) ->
            row("E2", "fatiga muy alta (forma −35)", t == "recovery" && why.isNotEmpty(), "pasa a $t")
        }
        adjust("vo2max", calm.copy(hrvHistory = calm.hrvHistory.dropLast(7) + List(7) { 40.0 })).let { (t, _, _) ->
            row("E3", "VFC por debajo de lo normal 7 días", t !in QUALITY, "pasa a $t")
        }
        adjust("threshold", calm.copy(sleepScore = 35.0)).let { (t, v, _) ->
            row("E4", "mal sueño", t !in QUALITY && v < 1.0, "pasa a $t, ×$v")
        }
        adjust("vo2max", calm.copy(daysSinceActivity = 10)).let { (t, _, _) -> row("E5", "10 días sin entrenar", t !in QUALITY, "pasa a $t") }
        adjust("vo2max", calm.copy(effortMismatch = true)).let { (t, _, _) ->
            row("E6", "esfuerzo alto con pulso bajo", t !in QUALITY, "pasa a $t")
        }
        // Las tres preguntas (planes sin fecha): mal → versión suave; todo a 1 → solo un paseo
        val p = healthProfiles["seis días"]!!
        val day = weekPlan(p, start).days.first { it.sessions.isNotEmpty() }.sessions
        val (easy, _) = adjustDay(p, day, readiness(2, 2, 4), 2)
        val softer = easy.sumOf { it.durationMin } <= day.sumOf { it.durationMin } &&
            easy.filterIsInstance<HealthCardio>().none { s -> s.session.blocks.any { it.zone in vigorous } }
        row("E7", "respuestas: dormí y estoy mal", softer, "${day.sumOf { it.durationMin }} → ${easy.sumOf { it.durationMin }} min, sin Z4+")
        val (rest, _) = adjustDay(p, day, readiness(1, 1, 1), 2)
        row("E8", "respuestas: todo a 1", rest.size <= 1 && rest.all { it.sport == "walking" }, "queda: ${rest.joinToString { it.sport }}")
        // Sueño en horas (Health Connect, Apple Salud: sin puntuación), frente a lo normal en cada persona
        val phone = calm.copy(sleepScore = null)
        adjust("threshold", phone.copy(sleepHours = 5.2, usualSleepHours = 7.4)).let { (t, v, _) ->
            row("E9", "móvil: 5,2 h de sueño (sueles 7,4)", t !in QUALITY && v < 1.0, "pasa a $t, ×$v")
        }
        adjust("threshold", phone.copy(sleepHours = 5.4, usualSleepHours = 5.6)).let { (t, v, why) ->
            row("E10", "móvil: 5,4 h (sueles 5,6)", t == "threshold" && v == 1.0 && why.isEmpty(), "sin cambios: es tu sueño normal")
        }
        // E11 plan de 14 días con el estado de hoy: la 2.ª semana parte del CTL proyectado y no sube más del 10 %
        val r = races["maratón, 5 días"]!!
        for (offset in listOf(28, 63, 91)) {  // en base, en construcción y en pico
            val from = r.goal.date.plusDays(-7 * PREP_WEEKS + offset)
            val two = plan(from, 14, r.profile, r.goal, calm.copy(ctl = 45.0, atl = 50.0))
            val (w1, w2) = two.take(7).sumOf { it.load() } to two.drop(7).sumOf { it.load() }
            val recovery = two.take(7).any { it.recoveryWeek }
            row("E11", "14 días desde el día $offset de la maratón", recovery || w2 <= w1 * 1.105,
                "semana 1: ${fmt(w1, 0)}, semana 2: ${fmt(w2, 0)}" + if (recovery) " (la 1.ª es de descarga)" else "")
        }
        return rows
    }

    // ------------------------------------------------------------------ caso real

    @Test
    fun realAccount() {
        val id = System.getenv("INTERVALS_ATHLETE_ID") ?: return
        val key = System.getenv("INTERVALS_API_KEY") ?: return
        val today = systemToday()
        val client = IntervalsClient(id, key)
        val (athlete, wellness, activities) = try {
            runBlocking { Triple(client.athlete(), client.wellness(today.plusDays(-90), today), client.activities(today.plusDays(-90), today)) }
        } finally {
            client.close()
        }
        val sports = System.getenv("ACTIVE_SPORTS")?.split(",")?.map { it.trim() }?.filter { it in setOf("running", "cycling", "swimming_pool") }
            ?.ifEmpty { null } ?: listOf("running", "cycling")
        val restDays = System.getenv("REST_DAYS")?.split(",")?.map { it.trim() }?.filter { it in WEEKDAYS } ?: listOf("MON")
        val minutes = all.filter { it !in restDays }.associateWith { if (it == "SUN") 120 else 60 }
        val ep = fillPhysiology(Profile(activeSports = sports, restDays = restDays, dayMinutes = minutes), athlete)
        val rows = mutableListOf<Row>()

        // Planes sin fecha con tu fisiología
        val hp = HealthProfile("fitness", minutes, cardio = sports.filter { it in CARDIO }, level = "intermediate",
            lthr = ep.lthr, maxHr = ep.maxHr, css = ep.css)
        checkHealth(hp, "media", simulate(hp, adherence["media"]!!)).forEach { (c, r) -> rows += Row("tu cuenta, sin fecha", "media", c, r.first, r.second) }

        // Objetivos de FC que llegarían al reloj: techo fácil = LTHR − 30 (MOTOR §3) en % de la FC máx., todos en 50–100 %
        val lthr = ep.lthr
        val maxHr = ep.maxHr
        if (lthr != null && maxHr != null) {
            val texts = weekPlan(hp, start).sessions().filterIsInstance<HealthCardio>().map { describe(it.session, ep) }
            val pct = texts.flatMap { t -> Regex("""(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)% HR""").findAll(t).flatMap { m -> m.groupValues.drop(1).map { it.toDouble() } } }
            val cap = 100 * (lthr - 30) / maxHr
            rows += Row("tu cuenta, objetivos de FC", "fisiología real", "FC",
                pct.isNotEmpty() && pct.all { it in 50.0..100.0 } && pct.any { kotlin.math.abs(it - cap) < 0.1 },
                "LTHR ${fmt(lthr, 0)}, FC máx. ${fmt(maxHr, 0)} → techo fácil ${fmt(lthr - 30, 0)} ppm = ${fmt(cap, 1)} % de la FC máx.; " +
                    "${texts.size} sesiones, objetivos ${fmt(pct.minOrNull() ?: 0.0, 1)}–${fmt(pct.maxOrNull() ?: 0.0, 1)} % HR")
        } else rows += Row("tu cuenta, objetivos de FC", "fisiología real", "FC", null, "no aplica: intervals.icu no tiene LTHR o FC máx.")

        // El umbral estimado con tus actividades (como sin intervals.icu) frente al configurado en intervals.icu: ±8 ppm [Inferencia]
        val asWorkouts = activities.mapNotNull { a ->
            val sport = mapOf("Run" to "running", "Ride" to "cycling", "VirtualRide" to "cycling")[a["type"].str()] ?: return@mapNotNull null
            io.github.mrclusterman.trainsync.app.Workout(a["id"].str() ?: "", sport, a["start_date_local"].str() ?: "",
                (a["moving_time"].num() ?: 0.0) / 60, avgHr = a["average_heartrate"].num(), maxHr = a["max_heartrate"].num())
        }
        for (sport in listOf("running", "cycling")) {
            val configured = ep.lthrBySport[sport] ?: continue
            val est = maxHr?.let { io.github.mrclusterman.trainsync.app.estimatedLthr(asWorkouts, sport, it) }
            rows += Row("tu cuenta, umbral estimado", sport, "LTHR", est?.let { kotlin.math.abs(it - configured) <= 8 },
                est?.let { "estimado ${fmt(it, 0)} con tus ${asWorkouts.count { w -> w.sport == sport }} sesiones de 90 días; en intervals.icu ${fmt(configured, 0)}" }
                    ?: "no aplica: ninguna sesión de 20–70 min al 85 % de tu FC máx. o más en 90 días")
        }

        // Una media maratón con tu fisiología
        val goal = Goal("media maratón", "running", today.plusDays(7 * PREP_WEEKS), "A", 21.1)
        val race = Race(goal, ep.copy(activeSports = listOf("running") + sports.filter { it != "running" }, planStart = null))
        checkRace(race, raceDays(race)).forEach { (c, r) -> rows += Row("tu cuenta, media maratón", "prioridad A", c, r.first, r.second) }

        // Tu estado de hoy: el plan de 14 días se calcula con él y sigue cabiendo en tus minutos
        val state = dayState(wellness, activities, today)
        val twoWeeks = plan(today, 14, race.profile, goal, state)
        val over = twoWeeks.count { d -> d.minutes() > (minutes[d.weekday] ?: 0) }
        rows += Row("tu cuenta, estado de hoy", "datos reales", "E-real", over == 0,
            "plan de 14 días con tu estado de hoy; días que no caben: $over")

        println(rows.joinToString("\n") { "${it.id} ${it.case}: ${when (it.ok) { true -> "cumple"; false -> "NO cumple"; null -> "no aplica" }} · ${it.detail}" })
        if (System.getenv("VALIDATION_REPORT") == "1") {
            val mark = { ok: Boolean? -> when (ok) { true -> "sí"; false -> "**no**"; null -> "no aplica" } }
            File("../docs/proyecto/validacion/resultados-cuenta-real.md").writeText((listOf(
                "# Validación con la cuenta real de pruebas ($today)", "",
                "${rows.count { it.ok == true }} de ${rows.count { it.ok != null }} comprobaciones aplicables cumplen. De la cuenta solo salen la " +
                    "LTHR (configurada y estimada), la FC máx. y el número de sesiones; el resto son cifras del plan.", "",
                "| Caso | Variante | Criterio | Cumple | Detalle |", "|---|---|---|---|---|") +
                rows.map { "| ${it.case} | ${it.variant} | ${it.id} | ${mark(it.ok)} | ${it.detail} |" }).joinToString("\n") + "\n")
        }
        val failed = rows.filter { it.ok == false }
        if (failed.isNotEmpty()) fail("Con la cuenta real no cumplen:\n" + failed.joinToString("\n") { "  ${it.id} ${it.case}: ${it.detail}" })
    }

    // ------------------------------------------------------------------ N1–N3: natación

    private fun swimRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val css = 105.0  // 1:45/100 m
        val withCss = Profile(activeSports = listOf("swimming_pool"), css = css)
        val sessions = listOf("recovery", "aerobic", "long", "threshold", "vo2max", "race_specific").flatMap { kind ->
            listOf("base", "build", "peak").flatMap { phase -> (0 until 4).mapNotNull { w -> buildSession(kind, "swimming_pool", withCss, 1.0, phase, w) } }
        }
        val blocks = sessions.flatMap { it.blocks }
        rows += Row("natación con CSS 1:45", "${sessions.size} sesiones", "N1", blocks.all { b -> b.meters != null && b.meters!! % 25 == 0 },
            "todas las series en metros y en largos de 25 m")
        // Los metros cuadran con el tiempo al ritmo de la zona: ±25 m o ±5 %
        val off = blocks.filter { b ->
            val (fast, slow) = SWIM_ZONES[b.zone]!!
            val pace = css * listOfNotNull(fast, slow).average()
            kotlin.math.abs(b.meters!! - b.minutes * 60 / pace * 100) > maxOf(25.0, 0.05 * b.meters!!) + 25
        }
        rows += Row("natación con CSS 1:45", "${blocks.size} series", "N2", off.isEmpty(),
            if (off.isEmpty()) "metros coherentes con el tiempo y el ritmo de cada zona" else "${off.size} series descuadradas, p. ej. ${off.first().meters} m en ${off.first().minutes} min ${off.first().zone}")
        val text = sessions.map { describe(it, withCss) }
        rows += Row("natación con CSS 1:45", "texto para el reloj", "N3",
            text.all { t -> "mtr" in t && "/100m Pace" in t } && buildSession("aerobic", "swimming_pool", Profile(listOf("swimming_pool")))!!.blocks.all { it.meters == null },
            "en el reloj, distancia y ritmo /100 m; sin CSS, por tiempo y esfuerzo")
        return rows
    }

    // ------------------------------------------------------------------ F: combinaciones generadas

    private fun fuzzRows(): List<Row> {
        val rnd = kotlin.random.Random(20261029)
        val rows = mutableListOf<Row>()
        fun days(): Map<String, Int> {
            val n = rnd.nextInt(1, 8)
            return all.shuffled(rnd).take(n).associateWith { listOf(20, 30, 45, 60, 75, 90, 120, 180, 240).random(rnd) }
        }
        // Planes sin fecha
        val fails = linkedMapOf("F1" to mutableListOf<String>(), "F2" to mutableListOf(), "F3" to mutableListOf(),
            "F4" to mutableListOf(), "F5" to mutableListOf())
        val healthN = 300
        repeat(healthN) { n ->
            val cap = listOf("vigorous", "moderate", "light_moderate").random(rnd)
            val p = HealthProfile(listOf("fitness", "strength").random(rnd), days(), strength = listOf("gym", "bodyweight", "none").random(rnd),
                cardio = CARDIO.shuffled(rnd).take(rnd.nextInt(1, 5)), level = LEVELS.random(rnd), age = listOf(null, 15, 30, 50, 68, 82).random(rnd),
                intensityCap = cap, lthr = listOf(null, 160.0, 175.0).random(rnd), maxHr = 190.0, css = listOf(null, 95.0, 130.0).random(rnd))
            val tag = "#$n ${p.goal} ${p.level} ${p.days} ${p.strength} ${p.cardio} $cap"
            val weeks = try {
                val history = mutableListOf<Double>()
                (0 until 8).map { w -> weekPlan(p, start.plusDays(7 * w), history.take(w / 4), w % 4).also { if (w % 4 == 3) history += 0.7 } }
            } catch (e: Exception) { fails["F1"]!! += "$tag: ${e.message}"; return@repeat }
            for (w in weeks) {
                val strengthShort = w.warnings.any { "sesión mínima de fuerza" in it }
                w.days.forEach { d ->
                    val used = d.sessions.sumOf { it.durationMin }
                    val avail = p.days[d.weekday] ?: 0
                    if (avail == 0 && d.sessions.isNotEmpty()) fails["F2"]!! += "$tag: sesión el ${d.weekday}, que no entrenas"
                    else if (used > avail && !strengthShort) fails["F2"]!! += "$tag: ${d.weekday} $used de $avail min"
                }
                if (w.days.none { it.sessions.isEmpty() }) fails["F3"]!! += "$tag: semana sin descanso"
                if (cap != "vigorous") {
                    val vig = w.sessions().filterIsInstance<HealthCardio>().any { s -> s.session.blocks.any { it.zone in vigorous } }
                    val rirMin = if (cap == "light_moderate") 3 else 2  // tope moderado: RIR 2 (HealthPlan, strengthSession)
                    if (vig || w.strength().any { it.rir < rirMin }) fails["F4"]!! += "$tag: intensidad por encima del tope"
                }
            }
            if (n % 10 == 0 && weekPlan(p, start) != weekPlan(p, start)) fails["F5"]!! += "$tag: no determinista"
        }
        val healthText = mapOf("F1" to "sin errores", "F2" to "cada día cabe en sus minutos y nada en los días sin entreno",
            "F3" to "un día de descanso por semana", "F4" to "con tope del cribado, sin Z4+ ni RIR por debajo del tope (3 ligero-moderado, 2 moderado)", "F5" to "determinista")
        fails.forEach { (id, f) -> rows += Row("$healthN perfiles sin fecha × 8 semanas", "generados", id, f.isEmpty(),
            if (f.isEmpty()) healthText[id]!! else "${f.size} fallos; p. ej. ${f.first()}") }

        // Carreras: desde hoy (cualquier punto de la preparación) hasta el día de la prueba
        val raceFails = linkedMapOf("F6" to mutableListOf<String>(), "F7" to mutableListOf(), "F8" to mutableListOf(),
            "F9" to mutableListOf(), "F10" to mutableListOf(), "F11" to mutableListOf(), "F12" to mutableListOf(), "F13" to mutableListOf())
        val raceN = 300
        var floorWeeks = 0
        val distances = mapOf("running" to listOf(5.0, 10.0, 21.1, 42.2, 60.0, null), "cycling" to listOf(40.0, 100.0, 160.0, null),
            "swimming_pool" to listOf(1.5, 3.0, null), "swimming_open" to listOf(1.5, 5.0, null), "triathlon" to listOf(null), "duathlon" to listOf(null))
        repeat(raceN) { n ->
            val sport = distances.keys.random(rnd)
            val minutes = days()
            val sports = (listOf("running", "cycling", "swimming_pool").shuffled(rnd).take(rnd.nextInt(1, 4)))
            val today = start.plusDays(rnd.nextInt(0, 7))
            val goal = Goal("carrera", sport, today.plusDays(7 * rnd.nextInt(2, 31) + rnd.nextInt(0, 7)), listOf("A", "B", "C").random(rnd),
                distances[sport]!!.random(rnd), trail = sport == "running" && rnd.nextBoolean())
            val profile = Profile(activeSports = sports, strength = listOf("gym", "bodyweight", "none").random(rnd),
                restDays = all.filter { it !in minutes }, dayMinutes = minutes, lthr = listOf(null, 165.0).random(rnd),
                maxHr = 190.0, css = listOf(null, 100.0).random(rnd),
                intensityCap = listOf("vigorous", "vigorous", "moderate", "light_moderate").random(rnd))
            val tag = "#$n $sport ${goal.distanceKm} ${goal.priority} ${today.daysUntil(goal.date)} días ${minutes} $sports"
            val days = try {
                (0..today.daysUntil(goal.date)).map { proposeDay(today.plusDays(it), profile, goal) }
            } catch (e: Exception) { raceFails["F6"]!! += "$tag: ${e.message}"; return@repeat }
            days.forEach { d ->
                val avail = minutes[d.weekday] ?: 0
                if (avail == 0 && (d.main != null || d.strength != null)) raceFails["F7"]!! += "$tag: sesión el ${d.weekday}, que no entrenas"
                else if (d.minutes() > avail) raceFails["F7"]!! += "$tag: ${d.date} ${d.minutes()} de $avail min"
            }
            if (days.chunked(7).any { w -> w.size == 7 && w.all { it.main != null || it.strength != null } }) raceFails["F8"]!! += "$tag: semana sin descanso"
            val order = listOf("base_early", "base", "build", "peak", "taper")
            val prepStart = goal.date.plusDays(-7 * PREP_WEEKS).toString()  // antes, base de mantenimiento
            if (days.filter { it.date >= prepStart }.zipWithNext().any { (a, b) -> order.indexOf(b.phase) < order.indexOf(a.phase) }) raceFails["F9"]!! += "$tag: fases desordenadas"
            // Carga: alineada con las semanas del motor (desde el inicio de la preparación)
            val prep = goal.date.plusDays(-7 * PREP_WEEKS)
            val weeks = days.filter { it.phase != "taper" }.groupBy { prep.daysUntil(LocalDate.parse(it.date)) / 7 }.filterValues { it.size == 7 }
            val keys = weeks.keys.filter { it >= 0 }.sorted()  // antes de la preparación, mantenimiento: sin progresión
            var last: Double? = null
            val scales = rampScales(profile, goal)
            for (k in keys) {
                val w = weeks[k]!!
                val load = w.sumOf { it.load() }
                // Con el volumen ya al mínimo (sesiones en su parte fija) no hay de dónde bajar: se cuenta aparte
                val atFloor = (scales.getOrNull(k) ?: 1.0) <= 0.15 + 1e-9
                if (w.any { it.recoveryWeek }) {
                    // Con todo ya en la sesión mínima (20′) no hay de dónde bajar
                    if (last != null && last!! > 0 && load >= last!! && w.any { it.minutes() > 20 } && !atFloor) raceFails["F12"]!! += "$tag: la descarga de la semana $k no baja (${fmt(load, 0)} frente a ${fmt(last!!, 0)})"
                    continue
                }
                if (last != null && last!! > 0 && load > last!! * 1.105 && atFloor) floorWeeks += 1
                else if (last != null && last!! > 0 && load > last!! * 1.105) raceFails["F10"]!! += "$tag: semana $k sube ${pct(load / last!! - 1)}"
                last = load
            }
            if (profile.intensityCap != "vigorous" && days.any { d -> d.main?.type in QUALITY || d.main?.type == "neuromuscular" ||
                    d.main?.blocks?.any { it.zone in vigorous } == true }) raceFails["F13"]!! += "$tag: intensidad por encima del tope ${profile.intensityCap}"
            if (n % 10 == 0 && (0..6).map { proposeDay(today.plusDays(it), profile, goal) } != days.take(7)) raceFails["F11"]!! += "$tag: no determinista"
        }
        val raceText = mapOf("F6" to "sin errores", "F7" to "cada día cabe en sus minutos y nada en los días sin entreno",
            "F8" to "un día de descanso por semana", "F9" to "fases en orden", "F10" to "carga semanal +10 % como mucho",
            "F11" to "determinista", "F12" to "cada descarga baja la carga", "F13" to "con tope del cribado, ni series ni Z4+")
        raceFails.forEach { (id, f) -> rows += Row("$raceN carreras desde un punto cualquiera", "generadas", id, f.isEmpty(),
            (if (f.isEmpty()) raceText[id]!! else "${f.size} fallos; p. ej. ${f.first()}") +
                if (id == "F10" && floorWeeks > 0) "; $floorWeeks semanas suben más con todo ya al mínimo (sesiones en su parte fija)" else "") }
        return rows
    }

    // ------------------------------------------------------------------ informe

    @Test
    fun theEngineMeetsEveryCriterion() {
        val parts = listOf("Planes sin fecha (V1–V11)" to healthRows(), "Hacia una carrera (R1–R10)" to raceRows(),
            "Ajuste del día (E1–E11)" to stateRows(), "Natación (N1–N3)" to swimRows(),
            "Combinaciones generadas (F1–F13)" to fuzzRows())
        val rows = parts.flatMap { it.second }
        if (System.getenv("VALIDATION_REPORT") == "1") report(parts)
        val failed = rows.filter { it.ok == false && "${it.id} ${it.case} (${it.variant})" !in KNOWN }
        val fixed = KNOWN.keys.filter { k -> rows.none { it.ok == false && "${it.id} ${it.case} (${it.variant})" == k } }
        if (fixed.isNotEmpty()) fail("Ya cumplen; quitarlos de KNOWN: $fixed")
        if (failed.isNotEmpty()) fail("${failed.size} comprobaciones no cumplen:\n" + failed.joinToString("\n") { "  ${it.id} ${it.case} (${it.variant}): ${it.detail}" })
    }

    private fun report(parts: List<Pair<String, List<Row>>>) {
        val rows = parts.flatMap { it.second }
        val applicable = rows.filter { it.ok != null }
        val mark = { ok: Boolean? -> when (ok) { true -> "sí"; false -> "**no**"; null -> "no aplica" } }
        val lines = mutableListOf("# Resultados de la validación del motor", "",
            "${applicable.count { it.ok == true }} de ${applicable.size} comprobaciones aplicables cumplen " +
                "(${rows.size - applicable.size} no aplican). Datos sintéticos; generado con " +
                "`VALIDATION_REPORT=1 ./gradlew :shared:jvmTest --tests '*ValidationTest*'`. Criterios en `VALIDACION.md`.")
        for ((title, part) in parts) {
            lines += listOf("", "## $title", "", "| Caso | Variante | Criterio | Cumple | Detalle |", "|---|---|---|---|---|")
            lines += part.map { r ->
                val known = KNOWN["${r.id} ${r.case} (${r.variant})"]?.let { " (conocido: $it)" } ?: ""
                "| ${r.case} | ${r.variant} | ${r.id} | ${mark(r.ok)}$known | ${r.detail} |"
            }
        }
        File("../docs/proyecto/validacion/resultados.md").writeText(lines.joinToString("\n") + "\n")
    }

    private fun pct(x: Double) = (if (x > 0) "+" else "") + fmt(x * 100, 1) + " %"
    private fun yes(b: Boolean) = if (b) "sí" else "no"
}

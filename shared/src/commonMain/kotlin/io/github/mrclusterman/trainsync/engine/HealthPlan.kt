package io.github.mrclusterman.trainsync.engine

// Planes de salud sin fecha: ponerse en forma y fuerza y músculo (docs/proyecto/DISENO.md), portado de
// prototype/backend/app/services/health_plan.py. Determinista: mismas entradas, mismo plan. Cada regla cita su
// apartado del diseño (§n), que cita su fuente en docs/proyecto/ESTADO_DEL_ARTE.md.

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// "perder grasa" se retiró el 2026-10-21: con usuarios desde 14 años, mal cruce con los TCA (DISENO §2)
val HEALTH_GOALS = listOf("fitness", "strength")
val LEVELS = listOf("beginner", "intermediate", "advanced")
val CARDIO = listOf("walking", "running", "cycling", "swimming_pool")

data class HealthProfile(
    val goal: String,  // fitness / strength
    val days: Map<String, Int>,  // día disponible (MON..SUN) → minutos
    val strength: String = "gym",  // gym / bodyweight / none
    val cardio: List<String> = listOf("walking"),
    val level: String = "beginner",
    val age: Int? = null,
    val intensityCap: String = "vigorous",  // tope del cribado del ACSM: light_moderate / moderate / vigorous
    val lthr: Double? = null,
    val maxHr: Double? = null,
    val css: Double? = null,
    val keep: Set<String> = emptySet(),  // días que el usuario pidió entrenar: nunca pasan a descanso extra (§5.6)
) {
    init {
        require(goal in HEALTH_GOALS && level in LEVELS && strength in setOf("gym", "bodyweight", "none"))
        require(cardio.all { it in CARDIO } && days.keys.all { it in WEEKDAYS })
        require(intensityCap in setOf("light_moderate", "moderate", "vigorous"))
    }
}

/** Una sesión del plan de salud: fuerza o resistencia. */
sealed interface Planned {
    val sport: String
    val durationMin: Int
    val tss: Int
}

@Serializable
data class HealthStrength(
    override val sport: String = "strength",
    val type: String = "strength",
    val name: String = "Fuerza",
    val patterns: List<String>,
    @SerialName("sets_per_exercise") val setsPerExercise: Int,
    val exercises: List<String>,
    val rir: Int,
    @SerialName("duration_min") override val durationMin: Int,
    override val tss: Int,
    val notes: String,
) : Planned

/** Fuerza del modo carrera como sesión planificada (para emparejar lo hecho). */
data class RaceStrengthItem(val strength: RaceStrength) : Planned {
    override val sport get() = "strength"
    override val durationMin get() = strength.durationMin
    override val tss get() = strength.tss
}

/** Envoltorio de una sesión de resistencia del catálogo en el plan de salud. */
data class HealthCardio(val session: Session) : Planned {
    override val sport get() = session.sport
    override val durationMin get() = session.durationMin
    override val tss get() = session.tss
    val equivalentMin: Int get() = session.equivalentMin!!
}

@Serializable
data class Dose(val aerobic: Int, @SerialName("strength_days") val strengthDays: Int, val sets: Int)

data class HealthDay(
    val date: String,
    val weekday: String,
    val sessions: List<Planned>,
    val note: String? = null,
    val availableMin: Int? = null,
)

data class WeekPlan(
    val goal: String,
    val weekInBlock: Int,
    val dose: Dose,
    val days: List<HealthDay>,
    val warnings: List<String>,
    val info: List<String>,
    val equivalentMin: Int,
    val changed: Boolean? = null,
)

// ---------------------------------------------------------------- §3 dosis semanal

/** Meta del bloque estable: minutos moderados equivalentes (1 vigoroso = 2 moderados, OMS) y series por patrón. */
internal data class Target(val aerobic: Int, val strengthDays: Pair<Int, Int>, val sets: Int)

internal val TARGET = mapOf(
    "fitness" to Target(300, 2 to 3, 10),  // OMS 150–300; ACSM 2026 ~10 series
    "strength" to Target(150, 3 to 4, 20),  // Schoenfeld 2017: más series, más músculo
)
const val FLOOR_AEROBIC = 150  // suelo de la OMS para todos (§1)
const val FLOOR_STRENGTH_DAYS = 2
private val START = mapOf("beginner" to 0.6, "intermediate" to 0.8, "advanced" to 1.0)  // [Inferencia] §3
private val START_SETS_PER_SESSION = mapOf("beginner" to 1, "intermediate" to 2, "advanced" to 3)  // [Inferencia] §3
private const val AEROBIC_STEP = 1.10  // +10 % por semana de carga [Inferencia] §4
private const val DELOAD = 0.7  // semana 4 del bloque: −30 % [Práctica: MOTOR §8]

fun strengthDays(p: HealthProfile): Int {
    if (p.strength == "none") return 0
    val (lo, hi) = TARGET[p.goal]!!.strengthDays
    var n = if (p.level == "beginner") lo else hi
    if (p.age != null && p.age >= 65) n = max(n, 3)  // OMS: equilibrio y fuerza 3 días o más en mayores de 65
    return min(n, trainingDays(p).size)
}

/** Revisión del bloque por adherencia (§4): ≥ 85 % sube, 60–85 % repite, < 60 % baja. [Inferencia] */
fun nextBase(base: Double, peak: Double, adherence: Double): Double = when {
    adherence >= 0.85 -> peak * 1.05
    adherence >= 0.60 -> base
    else -> base * 0.8
}

/** Dosis de una semana. adherence: sesiones hechas / planificadas de cada bloque ya terminado. */
fun weekDose(p: HealthProfile, adherence: List<Double>, weekInBlock: Int): Dose {
    val target = TARGET[p.goal]!!
    val nStrength = strengthDays(p)
    var aero = max(60.0, target.aerobic * START[p.level]!!)
    var sets = (nStrength * START_SETS_PER_SESSION[p.level]!!).toDouble()
    for (done in adherence) {
        // Nunca por debajo de 60 min: algo de actividad es mejor que nada (OMS 2020) [Inferencia en la cifra]
        aero = max(min(nextBase(aero, min(aero * AEROBIC_STEP.pow(2), target.aerobic.toDouble()), done),
            target.aerobic.toDouble()), 60.0)
        sets = min(nextBase(sets, min(sets + 2, target.sets.toDouble()), done), target.sets.toDouble())
    }
    val week = min(weekInBlock, 2)
    var minutes = pyRound(aero)
    repeat(week) {  // sobre el valor redondeado y hacia abajo: la subida que se ve nunca pasa del 10 %
        minutes = min(floor(minutes * AEROBIC_STEP).toInt(), target.aerobic)
    }
    sets = min(sets + week, target.sets.toDouble())  // +1 serie por patrón y semana de carga
    if (weekInBlock == 3) {
        minutes = pyRound(minutes * DELOAD)
        sets *= DELOAD
    }
    return Dose(minutes, nStrength, max(pyRound(sets), nStrength))
}

// ---------------------------------------------------------------- §5 reparto semanal

/** Días disponibles en orden de la semana, dejando al menos uno de descanso (§5.1). */
fun trainingDays(p: HealthProfile): List<String> {
    val days = WEEKDAYS.filter { (p.days[it] ?: 0) != 0 }.toMutableList()
    if (days.size == 7) days.remove(days.reversed().minBy { p.days[it]!! })  // el de menos minutos (el último si empatan)
    return days
}

private fun <T> combinations(items: List<T>, n: Int): List<List<T>> {
    if (n == 0) return listOf(emptyList())
    return items.indices.flatMap { i -> combinations(items.drop(i + 1), n - 1).map { listOf(items[i]) + it } }
}

/** Los n días más separados entre sí (máxima separación mínima, también entre semanas) (§5.2). */
fun spread(days: List<String>, n: Int): List<String> {
    if (n == 0) return emptyList()
    fun minGap(combo: List<String>): Int {
        val ix = combo.map { WEEKDAYS.indexOf(it) }.sorted()
        return (ix.zipWithNext { a, b -> b - a } + (ix.first() + 7 - ix.last())).min()
    }
    // Clave de Python: (separación mínima, [-índice de cada día]); max se queda con el primero de los empates
    val better = Comparator<List<String>> { a, b ->
        val g = minGap(a).compareTo(minGap(b))
        if (g != 0) g else a.map { -WEEKDAYS.indexOf(it) }.zip(b.map { -WEEKDAYS.indexOf(it) })
            .map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0
    }
    var best: List<String>? = null
    for (c in combinations(days, n)) if (best == null || better.compare(c, best) > 0) best = c
    return best!!
}

// ---------------------------------------------------------------- §6 sesión de fuerza

/** Patrón → (gimnasio, peso corporal). */
val PATTERNS: Map<String, Pair<String, String>> = linkedMapOf(
    "sentadilla" to ("Sentadilla copa (goblet) o con barra" to "Sentadilla (a una pierna al progresar)"),
    "bisagra" to ("Peso muerto rumano" to "Puente de glúteo (a una pierna al progresar)"),
    "zancada" to ("Zancada con mancuernas" to "Zancada atrás"),
    "empuje horizontal" to ("Press de banca con mancuernas" to "Flexiones (inclinadas al empezar)"),
    "empuje vertical" to ("Press militar con mancuernas" to "Flexiones en pica"),
    "tracción horizontal" to ("Remo con mancuerna" to "Remo invertido bajo una mesa firme"),
    "tracción vertical" to ("Jalón al pecho" to "Dominadas asistidas con goma"),
    "core" to ("Plancha frontal y lateral" to "Plancha frontal y lateral"),
)
val FULL_BODY = listOf("sentadilla", "bisagra", "empuje horizontal", "tracción horizontal", "core")
val UPPER_BODY = listOf("empuje horizontal", "tracción horizontal", "empuje vertical", "tracción vertical", "core")
val LOWER_BODY = listOf("sentadilla", "bisagra", "zancada", "core")
val ACCESSORY = listOf("core", "zancada", "empuje vertical", "tracción vertical")  // lo primero que sale si falta tiempo
const val BALANCE = "Equilibrio: apoyo a una pierna 3 × 30″ por lado y marcha en tándem 3 × 10 pasos"

/** Repeticiones y RIR por objetivo y nivel (§6). [ACSM 2009/2026; rangos: Inferencia] */
private val REPS = mapOf("beginner" to ("10–15" to 3), "fitness" to ("8–12" to 2), "strength" to ("6–12" to 1))
private const val MIN_PER_SET = 2.5  // serie + descanso [Inferencia]

fun strengthSession(p: HealthProfile, patterns: List<String>, setsPerExercise: Int, extraRir: Int = 0): HealthStrength {
    val (reps, baseRir) = REPS[if (p.level == "beginner") "beginner" else p.goal]!!
    // Tope del cribado (§9): lejos del fallo cuando no se permite intensidad vigorosa [Inferencia: RIR ≈ intensidad]
    val rir = max(baseRir + extraRir, mapOf("light_moderate" to 3, "moderate" to 2, "vigorous" to 0)[p.intensityCap]!!)
    val bodyweight = p.strength == "bodyweight"
    // El core es isométrico: por tiempo, no por repeticiones ni RIR [Práctica]
    val exercises = patterns.map { pt ->
        val name = PATTERNS[pt]!!.let { if (bodyweight) it.second else it.first }
        if (pt == "core") "$name: $setsPerExercise × 20–45″ por lado" else "$name: $setsPerExercise × $reps repeticiones"
    }.toMutableList()
    if (p.age != null && p.age >= 65) exercises += BALANCE
    val minutes = pyRound(10 + patterns.size * setsPerExercise * MIN_PER_SET)  // 10′ de calentamiento
    return HealthStrength(patterns = patterns, setsPerExercise = setsPerExercise, exercises = exercises, rir = rir,
        durationMin = minutes, tss = pyRound(strengthTss((10 - rir).toDouble(), minutes.toDouble())),
        notes = effortNote(rir, p.strength))
}

/** El RIR (repeticiones en reserva) y la progresión doble, sin jerga. */
fun effortNote(rir: Int, kit: String): String {
    val spare = if (rir == 1) "1 repetición" else "$rir repeticiones"
    val effort = if (rir != 0) "Termina cada serie cuando aún podrías hacer unas $spare más." else "Haz cada serie hasta no poder más."
    val harder = if (kit == "bodyweight") "pasa a una versión más difícil del ejercicio" else "sube un poco el peso (2–10 %)"
    return "$effort Cuando llegues al máximo de repeticiones en todas las series, $harder y vuelve al mínimo de repeticiones."
}

/** Cuerpo completo con ≤ 3 días; torso y pierna alternos con 4 (§5.2). budget: minutos máximos por día. */
fun strengthWeek(p: HealthProfile, days: List<String>, sets: Int, budget: Map<String, Int>? = null): Map<String, HealthStrength> {
    if (days.isEmpty()) return emptyMap()
    val plan = if (days.size <= 3) List(days.size) { FULL_BODY } else List(days.size / 2) { listOf(UPPER_BODY, LOWER_BODY) }.flatten()
    val times = PATTERNS.keys.associateWith { pt -> plan.count { pt in it } }
    val out = linkedMapOf<String, HealthStrength>()
    // Ondulación diaria: con 3 días de cuerpo completo, pesado, ligero y medio con las mismas series en total, para variar
    // la carga diaria (monotonía de Foster). [Práctica; ACSM 2026: la periodización no cambia el resultado medio]
    val wave = if (days.size == 3) listOf(1, -1, 0) else List(days.size) { 0 }
    for (i in 0 until min(days.size, plan.size)) {
        val day = days[i]
        var patterns = plan[i]
        var perEx = max(ceil(sets.toDouble() / patterns.filter { it != "core" }.maxOf { times[it]!! }).toInt() + wave[i], 1)
        var session = strengthSession(p, patterns, perEx)
        // Tiene que caber en los minutos del día (§5.5): primero menos series, luego fuera los accesorios
        val limit = budget?.get(day) ?: p.days[day]!!
        while (session.durationMin > limit && (perEx > 1 || ACCESSORY.any { it in patterns })) {
            if (perEx > 1) perEx -= 1
            else {
                val drop = ACCESSORY.first { it in patterns }
                patterns = patterns.filter { it != drop }
            }
            session = strengthSession(p, patterns, perEx)
        }
        out[day] = session
    }
    return out
}

// ---------------------------------------------------------------- §7 sesión de resistencia

/** Minutos moderados equivalentes por zona: Z1 es ligera y no cuenta para la OMS; Z4 o más es vigorosa (×2). */
private val EQUIV = mapOf("Z1" to 0, "Z2" to 1, "Z3" to 1, "Z4" to 2, "Z5" to 2, "Z6" to 2)

fun equivalentMinutes(session: Session): Double = session.blocks.sumOf { b ->
    b.reps * b.minutes * EQUIV[b.zone]!! + b.reps * (b.restMinutes ?: 0.0) * EQUIV[b.restZone ?: "Z1"]!!
}

/** Sesión del catálogo cuyo tiempo moderado equivalente se acerca a `equivalent`, sin pasar de `cap` minutos. */
fun cardioSession(p: HealthProfile, sport: String, kind: String, equivalent: Double, week: Int, cap: Int): Session? {
    val ep = Profile(activeSports = listOf(sport), lthr = p.lthr, maxHr = p.maxHr, css = p.css)
    val entry = pickSession(kind, sport, "base", week) ?: return null
    val mid = (entry.duration.first + entry.duration.second) / 2.0
    var total = min(max(equivalent, 20.0), cap.toDouble())
    var session: Session? = null
    for (attempt in 0 until 6) {  // equivalentes ≈ duración − parte fija en Z1 (calentamientos): corrección aditiva
        val candidate = buildSession(kind, sport, ep, total / mid, "base", week)!!
        if (candidate.durationMin > cap) break
        session = candidate
        val got = equivalentMinutes(candidate)
        if (got <= 0 || abs(got - equivalent) <= 0.1 * equivalent || total >= cap) break
        total = min(max(total + equivalent - got, 20.0), cap.toDouble())
    }
    return session?.also { it.equivalentMin = pyRound(equivalentMinutes(it)) }
}

/** La misma sesión en los otros deportes elegidos: mismo trabajo aeróbico y sin pasar de su duración. */
fun alternatives(p: HealthProfile, session: Session, week: Int): List<Session> = p.cardio
    .filter { it != session.sport }
    .mapNotNull { cardioSession(p, it, session.type, session.equivalentMin!!.toDouble(), week, session.durationMin) }

/** Moderada para principiantes y sin permiso de vigorosa; si no, polarizada (una de calidad cada 3, como mucho dos). */
fun cardioKinds(p: HealthProfile, n: Int): MutableList<String> {
    if (p.level == "beginner" || p.intensityCap != "vigorous" || n < 3) return MutableList(n) { "aerobic" }
    val hard = min(n / 3, 2)
    return MutableList(n) { if (it < hard) "threshold" else "aerobic" }
}

// ---------------------------------------------------------------- semana completa

/** Resistencia de la semana (§5.3–§5.5): primero los días sin fuerza; si no llegan, también los de fuerza. */
private fun weekCardio(p: HealthProfile, start: LocalDate, dose: Dose, days: List<String>,
                       strength: Map<String, HealthStrength>, strengthSlots: Int = 7): Pair<Map<String, Session>, Double> {
    val free = days.filter { it !in strength }
    // Días de fuerza que también llevan aeróbico: los de más tiempo libre primero, como mucho `strengthSlots`
    val extra = strength.keys.filter { p.days[it]!! - strength[it]!!.durationMin >= 20 }
        .sortedByDescending { p.days[it]!! - strength[it]!!.durationMin }.take(strengthSlots).toSet()
    val slots = free + strength.keys.filter { it in extra }
    val onStrengthDays = extra.isNotEmpty()
    val sports = p.cardio.ifEmpty { listOf("walking") }
    var pending = dose.aerobic.toDouble()
    val planDays = linkedMapOf<String, Session>()
    var doneEq = 0.0
    val caps = slots.associateWith { p.days[it]!! - (strength[it]?.durationMin ?: 0) }
    // Días duros y suaves alternos (monotonía de Foster < 2, MOTOR §3): entre los libres, de más a menos tiempo, 1,3 y 0,7
    // alternos; los días que ya tienen fuerza llevan menos aeróbico (0,5). [Inferencia en los factores]
    val byCap = free.sortedWith(compareBy({ -caps[it]!! }, { WEEKDAYS.indexOf(it) }))
    // Sin aeróbico los días de fuerza, la alternancia ya la dan esos días: los libres se reparten por igual
    val weight = free.associateWith { caps[it]!! * (if (!onStrengthDays) 1.0 else if (byCap.indexOf(it) % 2 == 0) 1.3 else 0.7) } +
        slots.filter { it in strength }.associateWith { caps[it]!! * 0.5 }
    val kinds = cardioKinds(p, slots.size)
    for ((i, day) in slots.withIndex()) {
        if (pending <= 5) break
        val cap = caps[day]!!
        // Reparto proporcional al peso de cada día que queda (§5.5); lo que no cabe pasa a los siguientes
        val share = min(pending * weight[day]!! / slots.drop(i).sumOf { weight[it]!! }, cap.toDouble())
        var sport = sports[i % sports.size]
        // En fuerza y músculo, nada de carrera el día de pierna si hay otra opción (Schumann y Lundberg 2022, §5.4)
        if (day in strength && "sentadilla" in strength[day]!!.patterns && sport == "running") {
            sport = sports.firstOrNull { it != "running" } ?: sport
            if (sport == "running" && p.goal == "strength") kinds[i] = "aerobic"
        }
        val weekNo = WEEKDAYS.indexOf(day) + start.isoWeek()
        // Si el deporte no tiene ese tipo de calidad o no cabe, se prueba el otro antes de quedarse en aeróbico
        val options = listOf(kinds[i]) +
            listOf("vo2max", "threshold").filter { kinds[i] != "aerobic" && it != kinds[i] } + "aerobic"
        val session = options.firstNotNullOfOrNull { cardioSession(p, sport, it, share, weekNo, cap) }
        if (session != null) {
            planDays[day] = session
            pending -= session.equivalentMin!!
            doneEq += session.equivalentMin!!
        }
    }
    return planDays to doneEq
}

val STRENGTH_SHARES = listOf(1.0, 0.85, 0.7, 0.55, 0.4, 0.3)  // parte del día que puede ocupar la fuerza [Inferencia]
const val MAX_MONOTONY = 2.0  // Foster 1998: por encima, la carga diaria es demasiado uniforme (MOTOR §3)
const val REST_NOTE = "Hoy descansas aunque lo marcaste como disponible: entrenar todos los días con el mismo esfuerzo " +
    "cansa más y sube el riesgo de acabar agotado o enfermo. Con este día libre llegas igual a tu dosis de la semana. " +
    "Si prefieres entrenar, puedes recuperarlo."

/** Monotonía de Foster: media / desviación típica de la carga diaria de la semana (0 si no hay variación). */
fun monotony(strength: Map<String, HealthStrength>, cardio: Map<String, Session>): Double {
    val loads = WEEKDAYS.map { d -> ((strength[d]?.tss ?: 0) + (cardio[d]?.tss ?: 0)).toDouble() }
    val mean = loads.sum() / 7
    val sd = sqrt(loads.sumOf { (it - mean) * (it - mean) } / 7)
    return if (sd != 0.0) mean / sd else 0.0
}

private data class Candidate(val strength: Map<String, HealthStrength>, val cardio: Map<String, Session>,
                             val doneEq: Double, val rest: String?)

/** Plan de 7 días a partir de `start` (lunes): dosis, días y avisos. */
fun weekPlan(p: HealthProfile, start: LocalDate, adherence: List<Double> = emptyList(), weekInBlock: Int = 0): WeekPlan {
    val dose = weekDose(p, adherence, weekInBlock)
    val days = trainingDays(p)
    val sDays = spread(days, dose.strengthDays)
    // Semanas candidatas en orden de preferencia: la primera que cumple el suelo aeróbico de la OMS (§1.2) con monotonía
    // < 2 (§5.6). La fuerza ocupa primero todo el día y después menos; para cada parte, la semana tal cual y luego con un
    // día libre más de descanso. Si ninguna baja de 2, la primera que cumple el suelo; si ninguna, la de más minutos.
    val floorEq = min(dose.aerobic, FLOOR_AEROBIC)
    val free = days.filter { it !in sDays && it !in p.keep }
    var chosen: Candidate? = null
    var floorOk: Candidate? = null
    var most: Candidate? = null
    for (share in STRENGTH_SHARES) {
        val budget = sDays.associateWith { max(pyRound(p.days[it]!! * share), 20) }
        val strength = strengthWeek(p, sDays, dose.sets, budget)
        // Con poca dosis repartida en muchos días, los días quedan iguales: también se prueba con aeróbico solo en
        // algunos días de fuerza (o en ninguno), que alterna días solo de fuerza y días de resistencia (validación V9)
        val slotOptions = listOf(7) + (0 until sDays.size)
        for ((rest, slotsOnStrength) in (listOf<String?>(null) + free).flatMap { r -> slotOptions.map { r to it } }) {
            val (planDays, doneEq) = weekCardio(p, start, dose, days.filter { it != rest }, strength, slotsOnStrength)
            val candidate = Candidate(strength, planDays, doneEq, rest)
            if (rest == null && (most == null || doneEq > most.doneEq)) most = candidate  // sin el suelo, no se quita un día
            if (doneEq < floorEq) continue
            if (floorOk == null) floorOk = candidate
            if (monotony(strength, planDays) < MAX_MONOTONY) {
                chosen = candidate
                break
            }
        }
        if (chosen != null) break
    }
    val (strength, planDays, doneEq, extraRest) = chosen ?: floorOk ?: most!!

    val warnings = mutableListOf<String>()  // algo que conviene cambiar
    val info = mutableListOf<String>()  // lo demás, en tono neutro
    if (p.strength == "none") warnings += "Sin fuerza no se cumple la recomendación de la OMS (2 días o más por semana)."
    if (strength.any { (d, s) -> s.durationMin > p.days[d]!! }) {
        warnings += "Hay días con menos tiempo del que pide la sesión mínima de fuerza (3 ejercicios, 1 serie)."
    }
    val plannedSets = listOf("sentadilla", "bisagra", "empuje horizontal", "tracción horizontal")
        .map { pt -> strength.values.filter { pt in it.patterns }.sumOf { it.setsPerExercise } }
    if (strength.isNotEmpty() && plannedSets.min() < dose.sets) {
        info += "Con tu tiempo haces ${plannedSets.min()} series por grupo muscular a la semana; " +
            "si algún día tienes más, el plan añade más."
    }
    val minutes = fmt(doneEq, 0)
    if (doneEq + 5 < dose.aerobic && doneEq < FLOOR_AEROBIC && p.level == "beginner") {
        info += "Empiezas con $minutes min de actividad a la semana. Para llegar a los $FLOOR_AEROBIC que recomienda la " +
            "OMS, más adelante te vendrá bien sumar un día o unos minutos."
    } else if (doneEq + 5 < dose.aerobic && doneEq < FLOOR_AEROBIC) {
        warnings += "Con tus días y minutos llegas a $minutes min de actividad a la semana. La OMS recomienda al menos " +
            "$FLOOR_AEROBIC: si puedes, añade un día o unos minutos."
    } else if (doneEq + 5 < dose.aerobic) {
        info += "Con tu tiempo llegas a $minutes min de actividad a la semana: ya cumples la recomendación de la OMS " +
            "($FLOOR_AEROBIC min). Si algún día tienes más tiempo, el plan lo aprovecha."
    }

    val out = (0 until 7).map { d ->
        val day = start.plusDays(d)
        val wd = day.weekday()
        var sessions = listOfNotNull<Planned>(strength[wd], planDays[wd]?.let { HealthCardio(it) })
        // Fuerza y músculo: fuerza primero; ponerse en forma: la sesión aeróbica primero (§5.4)
        if (p.goal != "strength" && sessions.size == 2) sessions = sessions.reversed()
        HealthDay(day.toString(), wd, sessions, if (wd == extraRest) REST_NOTE else null,
            if (wd == extraRest) p.days[wd] else null)
    }
    return WeekPlan(p.goal, weekInBlock, dose, out, warnings, info, pyRound(doneEq))
}

// ---------------------------------------------------------------- §8 ajuste diario sin reloj

/** Tres preguntas de 1 (mal) a 5 (bien). Dos o más en 1–2 → suave; todas en 1 → descanso activo. [Inferencia] */
fun readiness(sleep: Int, energy: Int, soreness: Int): String {
    val answers = listOf(sleep, energy, soreness)
    require(answers.all { it in 1..5 })
    if (answers.all { it == 1 }) return "rest"
    return if (answers.count { it <= 2 } >= 2) "easy" else "normal"
}

/**
 * Con datos del reloj o del móvil, el mismo ajuste que las tres preguntas: las reglas del modo carrera (`adjust`: fatiga,
 * variabilidad cardíaca, sueño, días sin entrenar, esfuerzo raro) deciden si hoy toca la versión suave, y por qué.
 */
fun watchReadiness(state: DayState): Pair<String, List<String>> {
    val (kind, volume, why) = adjust("vo2max", state)  // se prueba con una sesión de calidad: si la quitaría, hoy suave
    return (if (kind != "vo2max" || volume < 1.0) "easy" else "normal") to why
}

/** Estado del día: suave = resistencia moderada y más corta, fuerza con una serie menos y RIR + 1. */
fun adjustDay(p: HealthProfile, sessions: List<Planned>, status: String, week: Int): Pair<List<Planned>, String?> {
    if (status == "normal" || sessions.isEmpty()) return sessions to null
    if (status == "rest") {
        val walk = cardioSession(p, "walking", "recovery", 0.0, week, 30)
        return listOfNotNull<Planned>(walk?.let { HealthCardio(it) }) to "Hoy descanso activo: un paseo suave y nada más."
    }
    val out = sessions.map { s ->
        when (s) {
            is HealthStrength -> {
                var patterns = s.patterns
                if (s.setsPerExercise == 1) {  // ya en 1 serie: fuera un accesorio
                    val drop = ACCESSORY.firstOrNull { it in patterns }
                    patterns = patterns.filter { it != drop }
                }
                strengthSession(p, patterns, max(s.setsPerExercise - 1, 1), extraRir = 1)
            }
            is HealthCardio -> cardioSession(p, s.sport, "aerobic", s.equivalentMin * 0.7, week, s.durationMin)
                ?.let { HealthCardio(it) } ?: s
            is RaceStrengthItem -> s
        }
    }
    return out to "Hoy en versión suave: has dormido o te notas peor de lo normal."
}

// ---------------------------------------------------------------- §4 adherencia desde el historial

/** (bloque, semana dentro del bloque) de la semana que empieza en `monday`. */
fun position(blockStart: LocalDate, monday: LocalDate): Pair<Int, Int> {
    val weeks = max(floor(blockStart.daysUntil(monday) / 7.0).toInt(), 0)
    return weeks / 4 to weeks % 4
}

/**
 * Semana con cambios de disponibilidad: `changes` = minutos por día solo para esta semana (0 = ese día no puede). Se
 * recalcula con las mismas reglas; los días anteriores a `since` (cuando se hizo el cambio) quedan como estaban.
 * shortcut: los días pasados siguen contando como disponibles al recalcular, y con varios cambios en una semana los días
 * entre el primero y el último usan el plan original; guardar el historial de cambios si hace falta más precisión.
 */
fun weekWithChanges(p: HealthProfile, start: LocalDate, adherence: List<Double>, weekInBlock: Int,
                    changes: Map<String, Int>? = null, since: LocalDate? = null): WeekPlan {
    val base = weekPlan(p, start, adherence, weekInBlock)
    if (changes.isNullOrEmpty()) return base
    val days = (p.days + changes).filterValues { it != 0 }
    val keep = p.keep + changes.filterValues { it != 0 }.keys  // "quiero entrenar este día": no se vuelve a quitar
    val changed = weekPlan(p.copy(days = days, keep = keep), start, adherence, weekInBlock)
    val cutoff = (since ?: start).toString()
    val out = base.days.zip(changed.days).map { (b, c) -> if (b.date < cutoff) b else c }
    val eq = out.sumOf { d -> d.sessions.sumOf { (it as? HealthCardio)?.equivalentMin ?: 0 } }
    return changed.copy(days = out, equivalentMin = eq, changed = true)
}

val SPORT_NAMES = mapOf("walking" to "Caminata", "running" to "Carrera", "cycling" to "Bici",
    "swimming_pool" to "Natación", "strength" to "Fuerza")
private const val MIN_SHARE = 0.5  // una actividad del reloj con menos de la mitad de lo planificado no cuenta [Inferencia]

/** Algo hecho un día: del reloj (con minutos y hora) o marcado a mano. */
data class Done(val sport: String, val source: String, val minutes: Int? = null, val start: String? = null)

data class Match(val done: Done, val matched: String?, val why: String)

/**
 * Empareja lo hecho un día con lo planificado y explica por qué cuenta o no. Reglas: mismo deporte; si no, otro cardio
 * por una sesión de cardio (mismo trabajo aeróbico, §3); si no, extra. Lo del reloj se empareja antes; lo marcado a mano
 * en una sesión que el reloj ya dio por hecha no cuenta dos veces.
 */
fun matchDay(sessions: List<Planned>, done: List<Done>): List<Match> {
    val free = sessions.toMutableList()
    val out = mutableListOf<Match>()
    fun nameOf(s: Planned) = when (s) {
        is HealthStrength -> s.name
        is HealthCardio -> s.session.name
        is RaceStrengthItem -> s.strength.name
    }
    fun describe(item: Done): String {
        val sport = SPORT_NAMES[item.sport] ?: item.sport
        return if (item.source == "watch") {
            "$sport de ${item.minutes} min" + (item.start?.let { " a las $it" } ?: "") + " en tu reloj"
        } else "$sport marcada a mano"
    }
    fun take(item: Done, s: Planned) {
        val planned = s.durationMin
        val what = describe(item)
        if (item.minutes != null && item.minutes < MIN_SHARE * planned) {
            out += Match(item, null, "$what: no cuenta, ${item.minutes} de $planned min planificados")
            return
        }
        free.remove(s)
        val swap = if (s.sport == item.sport) "" else " (otro deporte, mismo trabajo aeróbico)"
        out += Match(item, s.sport, "$what: cuenta por «${nameOf(s)}»$swap")
    }
    for (source in listOf("watch", "manual")) {
        val pending = mutableListOf<Done>()
        for (item in done.filter { it.source == source }) {
            val same = free.firstOrNull { it.sport == item.sport }
            when {
                same != null -> take(item, same)
                source == "manual" && out.any { it.matched == item.sport } -> Unit  // ya la había detectado el reloj
                else -> pending += item
            }
        }
        for (item in pending) {
            val cardio = if (item.sport != "strength") free.firstOrNull { it.sport != "strength" } else null
            if (cardio != null) take(item, cardio)
            else out += Match(item, null, "${describe(item)}: extra, hoy no tocaba otra sesión de este tipo")
        }
    }
    return out
}

/**
 * Adherencia de cada bloque terminado: sesiones emparejadas con lo planificado (matchDay) / planificadas. Se reconstruye
 * cada bloque con el plan que tocaba entonces, con sus cambios (`changes`: lunes en ISO → (cambios, fecha del cambio)).
 */
fun adherenceHistory(p: HealthProfile, blockStart: LocalDate, done: Map<String, List<Done>>, blocks: Int,
                     changes: Map<String, Pair<Map<String, Int>, LocalDate>> = emptyMap()): List<Double> {
    val history = mutableListOf<Double>()
    for (b in 0 until blocks) {
        var planned = 0
        var completed = 0
        for (w in 0 until 4) {
            val monday = blockStart.plusDays(7 * (4 * b + w))
            val change = changes[monday.toString()]
            val week = weekWithChanges(p, monday, history, w, change?.first, change?.second)
            for (day in week.days) {
                planned += day.sessions.size
                completed += matchDay(day.sessions, done[day.date] ?: emptyList()).count { it.matched != null }
            }
        }
        history += if (planned != 0) pyRound(completed.toDouble() / planned, 3) else 1.0
    }
    return history
}

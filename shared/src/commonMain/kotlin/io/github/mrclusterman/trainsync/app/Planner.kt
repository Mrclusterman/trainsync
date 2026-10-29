package io.github.mrclusterman.trainsync.app

// Un solo motor para todos los objetivos (docs/VISION.md §6): el objetivo decide la periodización (sin fecha o hacia una
// carrera) y la app recibe siempre la misma forma de plan. Reutiliza el motor portado sin reescribirlo.

import io.github.mrclusterman.trainsync.engine.CARDIO
import io.github.mrclusterman.trainsync.engine.Day
import io.github.mrclusterman.trainsync.engine.DayState
import io.github.mrclusterman.trainsync.engine.Done
import io.github.mrclusterman.trainsync.engine.Goal
import io.github.mrclusterman.trainsync.engine.HealthCardio
import io.github.mrclusterman.trainsync.engine.HealthProfile
import io.github.mrclusterman.trainsync.engine.HealthStrength
import io.github.mrclusterman.trainsync.engine.Planned
import io.github.mrclusterman.trainsync.engine.Profile
import io.github.mrclusterman.trainsync.engine.RaceStrength
import io.github.mrclusterman.trainsync.engine.RaceStrengthItem
import io.github.mrclusterman.trainsync.engine.Session
import io.github.mrclusterman.trainsync.engine.WEEKDAYS
import io.github.mrclusterman.trainsync.engine.ScreeningResult
import io.github.mrclusterman.trainsync.engine.screen
import io.github.mrclusterman.trainsync.engine.adherenceHistory
import io.github.mrclusterman.trainsync.engine.adjustDay
import io.github.mrclusterman.trainsync.engine.alternatives
import io.github.mrclusterman.trainsync.engine.equivalentMinutes
import io.github.mrclusterman.trainsync.engine.isoWeek
import io.github.mrclusterman.trainsync.engine.matchDay
import io.github.mrclusterman.trainsync.engine.phaseFor
import io.github.mrclusterman.trainsync.engine.plan
import io.github.mrclusterman.trainsync.engine.plusDays
import io.github.mrclusterman.trainsync.engine.position
import io.github.mrclusterman.trainsync.engine.proposeDay
import io.github.mrclusterman.trainsync.engine.watchReadiness
import io.github.mrclusterman.trainsync.engine.pyRound
import io.github.mrclusterman.trainsync.engine.readiness
import io.github.mrclusterman.trainsync.engine.hrvStatus
import io.github.mrclusterman.trainsync.engine.weekWithHistory
import io.github.mrclusterman.trainsync.engine.weekday
import io.github.mrclusterman.trainsync.intervals.dayState
import io.github.mrclusterman.trainsync.intervals.fillPhysiology
import io.github.mrclusterman.trainsync.intervals.watchSessions
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** Un paso de una sesión, ya listo para mostrar. */
data class Step(val name: String, val reps: Int, val minutes: Double, val intensityZone: String, val restMinutes: Double?,
                val detail: String?, val target: String?, val meters: Int? = null)  // meters: natación con CSS

/** Una sesión del plan, de fuerza o de resistencia, con nombre llano y su nombre técnico. */
data class PlanItem(
    val sport: String,
    val kind: String,
    val title: String,
    val hint: String,
    val technicalName: String,
    val durationMin: Int,
    val steps: List<Step>,
    val exercises: List<String>,
    val notes: String?,
    val alternatives: List<PlanItem>,
    val chosen: Boolean,  // la eligió el usuario en lugar de la planificada
    val slot: String,  // deporte de la sesión planificada que ocupa (para el calendario del reloj)
    internal val session: Session?,
    internal val healthStrength: HealthStrength?,
    internal val raceStrength: RaceStrength?,
) {
    val isStrength: Boolean get() = sport == "strength"
}

/** Un motivo con su etiqueta de evidencia separada. */
data class Reason(val text: String, val evidence: String?)

data class MatchView(val text: String, val counts: Boolean)

data class DayView(
    val date: String,
    val weekday: String,
    val isToday: Boolean,
    val isPast: Boolean,
    val items: List<PlanItem>,
    val note: String?,
    val availableMin: Int?,  // día disponible que el motor deja de descanso extra: se puede recuperar
    val reasons: List<Reason>,
    val adjustments: List<Reason>,  // ajustes por cómo estás hoy (fatiga, sueño, HRV o tus respuestas)
    val matches: List<MatchView>,
    val doneSports: Set<String>,
    val phase: String?,
)

data class WeekView(
    val monday: String,
    val days: List<DayView>,
    val activeMinutes: Int,  // minutos moderados equivalentes (OMS)
    val strengthDays: Int,
    val warnings: List<String>,
    val info: List<String>,
    val block: Int?,  // sin fecha: bloque y semana dentro del bloque
    val weekInBlock: Int?,
    val changed: Boolean,
)

private val RACE_SPORTS = setOf("running", "cycling", "swimming_pool")

fun mondayOf(date: LocalDate): LocalDate = date.plusDays(-(date.dayOfWeek.isoDayNumber - 1))

/** Calcula el plan de cualquier semana con los datos guardados en el móvil. */
class Planner(private val data: AppData, private val today: LocalDate) {
    private val profile = data.profile ?: UserProfile()
    private val goal = data.goal

    /**
     * Cuestionario de salud del ACSM, si existe. Decisión 2026-10-21 (docs/CUMPLIMIENTO.md): las apps no lo preguntan,
     * el aviso de salud lo sustituye; el motor aplica el tope si algún día hay respuestas.
     */
    val screening: ScreeningResult? = profile.screening?.let { screen(it) }
    private val intensityCap = screening?.intensityCap ?: "vigorous"
    private val cache = data.deviceData(today)

    /** Fisiología de intervals.icu (LTHR, FC máx., CSS…) si está vinculado. */
    val physiology: Profile? = cache?.let {
        fillPhysiology(Profile(activeSports = listOf("running", "cycling")), it.athlete)
    }

    /** Estado de hoy (carga, HRV, sueño) si hay datos de intervals.icu. */
    val state: DayState? = cache?.let { runCatching { dayState(it.wellness, it.activities, today) }.getOrNull() }

    /** Hay señales de recuperación de hoy (VFC o sueño): si no, se preguntan las tres cosas aunque haya datos del móvil. */
    val hasRecoverySignals: Boolean = state?.let { s ->
        hrvStatus(s.hrvHistory) != null || s.sleepScore != null || (s.sleepHours != null && s.usualSleepHours != null)
    } == true

    private val feltToday: String? = data.readiness[today.toString()]?.let { readiness(it.sleep, it.energy, it.soreness) }
        ?.takeIf { it != "normal" }

    private val done: Map<String, List<Done>> = run {
        val all = (cache?.let { watchSessions(it.activities) } ?: emptyMap()).mapValues { it.value.toMutableList() }
            .toMutableMap()
        for (m in data.manualDone) all.getOrPut(m.date) { mutableListOf() } += Done(m.sport, "manual")
        // Apuntar lo levantado también cuenta como fuerza hecha ese día, aunque no se pulse "hecha"
        for (date in data.lifts.map { it.date }.toSet()) {
            val day = all.getOrPut(date) { mutableListOf() }
            if (day.none { it.sport == "strength" }) day += Done("strength", "manual")
        }
        all
    }

    fun raceGoal(): Goal? = goal?.race?.takeIf { goal.kind == GoalKind.RACE }?.let {
        Goal(it.name, it.sport, LocalDate.parse(it.date), it.priority, it.distanceKm, it.trail)
    }

    // ------------------------------------------------------------ sin fecha (ponerse en forma, fuerza, salud)

    private val healthProfile: HealthProfile by lazy {
        val ph = physiology
        HealthProfile(
            goal = if (goal?.kind == GoalKind.STRENGTH) "strength" else "fitness",
            days = profile.days.filterValues { it > 0 },
            strength = profile.strength,
            cardio = profile.cardio.filter { it in CARDIO }.ifEmpty { listOf("walking") },
            level = profile.level,
            age = profile.birthYear?.let { today.year - it },
            intensityCap = intensityCap,
            lthr = ph?.lthr, maxHr = ph?.maxHr, css = ph?.css,
        )
    }

    private val blockStart: LocalDate = data.blockStart?.let { LocalDate.parse(it) } ?: mondayOf(today)

    private val changes = data.weekChanges.mapValues { (_, c) -> c.steps() }

    // La adherencia de los bloques terminados se calcula una vez: las semanas futuras usan la de hoy
    private val history: List<Double> by lazy {
        val (block, _) = position(blockStart, mondayOf(today))
        adherenceHistory(healthProfile, blockStart, done, block, changes)
    }

    private fun healthWeek(monday: LocalDate): WeekView {
        val p = healthProfile
        val (block, week) = position(blockStart, monday)
        val adherence = history.take(block)
        val change = data.weekChanges[monday.toString()]
        val plan = weekWithHistory(p, monday, adherence, week, change?.steps() ?: emptyList())
        val days = plan.days.map { d ->
            var sessions: List<Planned> = d.sessions
            var note = d.note
            val adjustments = mutableListOf<Reason>()
            val answers = data.readiness[d.date]
            if (d.date == today.toString()) {
                // Tus respuestas mandan; sin ellas, los datos del reloj o del móvil (sin reloj se te preguntan)
                val watch = if (answers == null) state?.let { watchReadiness(it) } else null
                val status = answers?.let { readiness(it.sleep, it.energy, it.soreness) } ?: watch?.first ?: "normal"
                val (adjusted, adjustNote) = adjustDay(p, sessions, status, monday.isoWeek())
                sessions = adjusted
                if (adjustNote != null) {
                    adjustments += watch?.second?.map { splitEvidence(it).let { (t, e) -> Reason(t, e) } }
                        ?: listOf(Reason(adjustNote, "Inferencia"))
                    note = null
                }
            }
            val items = sessions.map { s ->
                when (s) {
                    is HealthStrength -> strengthItem(s)
                    is HealthCardio -> cardioItem(s.session, alternatives(p, s.session, monday.isoWeek()))
                    is RaceStrengthItem -> raceStrengthItem(s.strength)
                }
            }
            dayView(d.date, d.weekday, applyChoice(d.date, items), note, d.availableMin, emptyList(), adjustments, null)
        }
        return WeekView(monday.toString(), days, activeMinutes(days), days.count { d -> d.items.any { it.isStrength } },
            plan.warnings, plan.info, block, week, plan.changed == true)
    }

    // ------------------------------------------------------------ hacia una carrera

    private fun raceProfile(): Profile {
        val sports = profile.cardio.filter { it in RACE_SPORTS }.ifEmpty {
            listOf(goal?.race?.sport?.takeIf { it in RACE_SPORTS } ?: "running")
        }
        val base = Profile(activeSports = sports, strength = profile.strength,
            restDays = WEEKDAYS.filter { (profile.days[it] ?: 0) == 0 }, dayMinutes = profile.days.filterValues { it > 0 },
            intensityCap = intensityCap)
        return cache?.let { fillPhysiology(base, it.athlete) } ?: base
    }

    private fun raceWeek(monday: LocalDate, raceGoal: Goal): WeekView {
        val rp = raceProfile()
        val sunday = monday.plusDays(6)
        val fromToday = if (sunday >= today) {
            val start = if (today > monday) today else monday
            plan(start, (sunday.toEpochDays() - start.toEpochDays()).toInt() + 1, rp, raceGoal,
                if (start == today) state else null)
        } else emptyList()
        val engineDays: List<Day> = (0 until 7).map { d ->
            val date = monday.plusDays(d)
            when {
                // Tus respuestas de hoy ajustan el día igual que el reloj (sin reloj, con un estado neutro)
                date == today && feltToday != null ->
                    proposeDay(date, rp, raceGoal, (state ?: DayState(ctl = 0.0, atl = 0.0)).copy(feltStatus = feltToday))
                else -> fromToday.firstOrNull { it.date == date.toString() } ?: proposeDay(date, rp, raceGoal)
            }
        }
        val days = engineDays.map { d ->
            val items = listOfNotNull(d.main?.let { cardioItem(it, d.alternatives) }, d.strength?.let { raceStrengthItem(it) })
            val reasons = d.reasons.filter { it !in d.adjustments }.map { splitEvidence(it).let { (t, e) -> Reason(t, e) } }
            val adjustments = d.adjustments.map { splitEvidence(it).let { (t, e) -> Reason(t, e) } }
            dayView(d.date, d.weekday, applyChoice(d.date, items), null, null, reasons, adjustments, d.phase)
        }
        return WeekView(monday.toString(), days, activeMinutes(days), days.count { d -> d.items.any { it.isStrength } },
            rp.warnings, emptyList(), null, null, data.weekChanges.containsKey(monday.toString()))
    }

    // ------------------------------------------------------------ común

    fun week(monday: LocalDate): WeekView {
        val race = raceGoal()
        val week = if (race != null && race.date >= today) raceWeek(monday, race) else healthWeek(monday)
        val s = screening ?: return week
        return when (s.clearance) {
            // Síntomas con actividad: nada de ejercicio hasta que lo valore un médico
            "stop" -> week.copy(days = week.days.map { it.copy(items = emptyList(), matches = emptyList(), doneSports = emptySet()) },
                activeMinutes = 0, strengthDays = 0, warnings = listOf(s.message) + week.warnings)
            "none" -> week
            else -> week.copy(warnings = listOf(s.message) + week.warnings)
        }
    }

    fun day(date: LocalDate): DayView = week(mondayOf(date)).days.first { it.date == date.toString() }

    private fun activeMinutes(days: List<DayView>): Int = pyRound(days.sumOf { d ->
        d.items.mapNotNull { it.session }.sumOf { equivalentMinutes(it) }
    })

    private fun dayView(date: String, weekday: String, items: List<PlanItem>, note: String?, availableMin: Int?,
                        reasons: List<Reason>, adjustments: List<Reason>, phase: String?): DayView {
        val planned: List<Planned> = items.map { it.planned() }
        val matches = matchDay(planned, done[date] ?: emptyList())
        return DayView(date, weekday, date == today.toString(), date < today.toString(), items, note, availableMin,
            reasons, adjustments, matches.map { MatchView(it.why, it.matched != null) },
            matches.mapNotNull { it.matched }.toSet(), phase)
    }

    /** La alternativa elegida pasa a ser la sesión del día; la planificada queda entre las alternativas. */
    private fun applyChoice(date: String, items: List<PlanItem>): List<PlanItem> {
        val picks = data.choices[date] ?: return items
        return items.map { item ->
            val chosenSport = picks[item.sport] ?: return@map item
            val alt = item.alternatives.firstOrNull { it.sport == chosenSport } ?: return@map item
            alt.copy(chosen = true, slot = item.slot,
                alternatives = listOf(item.copy(alternatives = emptyList())) + item.alternatives.filter { it !== alt })
        }
    }

    private fun PlanItem.planned(): Planned = when {
        session != null -> HealthCardio(session.also { if (it.equivalentMin == null) it.equivalentMin = pyRound(equivalentMinutes(it)) })
        healthStrength != null -> healthStrength
        else -> RaceStrengthItem(raceStrength!!)
    }

    private fun cardioItem(s: Session, alts: List<Session>): PlanItem {
        val (title, hint) = PLAIN_SESSION[s.type] ?: (s.name to s.goal)
        return PlanItem(
            sport = s.sport, kind = s.type, title = title, hint = hint, technicalName = s.name, durationMin = s.durationMin,
            steps = s.blocks.map { Step(it.block, it.reps, it.minutes, it.zone, it.restMinutes, it.detail, s.targets[it.zone], it.meters) },
            exercises = emptyList(), notes = "${s.ifBad} ${s.ifGood}",
            alternatives = alts.map { cardioItem(it, emptyList()) }, chosen = false, slot = s.sport,
            session = s, healthStrength = null, raceStrength = null,
        )
    }

    private fun strengthItem(s: HealthStrength) = PlanItem(
        sport = "strength", kind = "strength", title = "Fuerza", hint = PLAIN_SESSION["strength"]!!.second,
        technicalName = "Fuerza", durationMin = s.durationMin, steps = emptyList(), exercises = s.exercises, notes = s.notes,
        alternatives = emptyList(), chosen = false, slot = "strength", session = null, healthStrength = s, raceStrength = null,
    )

    private fun raceStrengthItem(s: RaceStrength) = PlanItem(
        sport = "strength", kind = "strength", title = "Fuerza", hint = s.dose, technicalName = "Fuerza",
        durationMin = s.durationMin, steps = emptyList(), exercises = s.exercises,
        notes = "Hazla ${s.order}.", alternatives = emptyList(), chosen = false, slot = "strength",
        session = null, healthStrength = null, raceStrength = s,
    )

    /** Fase del plan de carrera hoy, o null sin carrera. */
    fun racePhase(): String? = raceGoal()?.let { phaseFor(today, it, null).first }

    companion object {
        val PHASE_LABEL = mapOf("base_early" to "Base inicial", "base" to "Base", "build" to "Construcción",
            "peak" to "Afinando", "taper" to "Puesta a punto")
        fun weekdayOf(date: LocalDate): String = date.weekday()
    }
}

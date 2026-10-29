package io.github.mrclusterman.trainsync.app

import io.github.mrclusterman.trainsync.engine.WEEKDAYS
import io.github.mrclusterman.trainsync.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * La app entera como la usaría alguien durante 5 semanas, con acciones al azar (semilla fija): marcar hecho, cambiar días,
 * responder las tres preguntas, elegir alternativa, apuntar fuerza, cambiar de objetivo. Cada día, todas las pantallas se
 * calculan sin error y lo de hoy cabe en los minutos de ese día.
 */
class AppFuzzTest {
    @Test
    fun randomUsersNeverBreakTheApp() {
        val rnd = Random(29102026)
        val problems = mutableListOf<String>()
        repeat(100) { n ->
            var now = LocalDate(2026, 10, 19).plusDays(rnd.nextInt(0, 7))
            val app = TrainSyncApp(MemoryStore(), MemoryStore(), { now })
            val days = WEEKDAYS.shuffled(rnd).take(rnd.nextInt(1, 8)).associateWith { listOf(20, 30, 45, 60, 90, 120).random(rnd) }
            val profile = UserProfile(birthYear = listOf(1950, 1975, 1995, 2010).random(rnd), level = listOf("beginner", "intermediate", "advanced").random(rnd),
                strength = listOf("gym", "bodyweight", "none").random(rnd), cardio = listOf("walking", "running", "cycling", "swimming_pool").shuffled(rnd).take(rnd.nextInt(1, 5)),
                days = days)
            fun newGoal() {
                val kind = GoalKind.entries.random(rnd)
                val race = if (kind == GoalKind.RACE) RaceInfo("prueba", listOf("running", "cycling", "swimming_pool", "triathlon", "duathlon").random(rnd),
                    now.plusDays(rnd.nextInt(3, 200)).toString(), listOf("A", "B", "C").random(rnd), listOf(null, 10.0, 21.1, 42.2).random(rnd)) else null
                app.setGoal(kind, race)
            }
            app.saveProfile(profile)
            newGoal()
            // La mitad, con datos del móvil al azar (algunos sin sueño o sin VFC, con picos y huecos)
            if (rnd.nextBoolean()) {
                val noSleep = rnd.nextInt(4) == 0
                val noHrv = rnd.nextInt(4) == 0
                app.importHealth(HealthSnapshot(listOf("Health Connect", "Apple Salud").random(rnd),
                    days = (0 until 60).filter { rnd.nextInt(10) != 0 }.map { d ->
                        DailyHealth(now.plusDays(-d).toString(), sleepMinutes = if (noSleep) null else rnd.nextDouble(240.0, 560.0),
                            hrv = if (noHrv) null else rnd.nextDouble(20.0, 110.0), restingHr = rnd.nextDouble(42.0, 75.0),
                            weightKg = rnd.nextDouble(50.0, 110.0), steps = rnd.nextDouble(500.0, 25000.0))
                    },
                    workouts = (1 until 60).filter { rnd.nextInt(3) == 0 }.map { d ->
                        Workout("w$d", listOf("walking", "running", "cycling", "swimming_pool", "strength", "other").random(rnd),
                            "${now.plusDays(-d)}T07:00:00", rnd.nextDouble(10.0, 200.0),
                            avgHr = if (rnd.nextBoolean()) rnd.nextDouble(90.0, 185.0) else null, maxHr = rnd.nextDouble(120.0, 230.0))
                    }, syncedAt = now.toString()))
            }
            val tag = "#$n ${app.data.goal?.kind} ${app.data.goal?.race?.sport} $days"
            val log = mutableListOf<String>()
            for (d in 0 until 35) {
                try {
                    val screen = app.todayScreen()
                    val avail = app.data.weekChanges[mondayOf(now).toString()]?.changes?.get(Planner.weekdayOf(now)) ?: days[Planner.weekdayOf(now)] ?: 0
                    val used = screen.day.items.sumOf { it.durationMin }
                    val strengthShort = screen.warnings.any { "sesión mínima de fuerza" in it }
                    if (used > avail && !strengthShort && screen.day.items.isNotEmpty()) problems += "$tag $now: $used de $avail min · ${log.takeLast(6)} · ${app.data.weekChanges[mondayOf(now).toString()]}"
                    if (screen.day.adjustments.isNotEmpty() && screen.day.items.isNotEmpty() && !screen.headline.startsWith("Hoy toca"))
                        problems += "$tag $now: hay ajuste pero la frase no lo dice: ${screen.headline}"
                    app.month(now.year, now.month.ordinal + 1)
                    app.progress()
                    when (rnd.nextInt(8)) {
                        0 -> screen.day.items.firstOrNull()?.let { app.markDone(now.toString(), it.sport) }
                        1 -> if (screen.askReadiness) app.answerReadiness(rnd.nextInt(1, 6), rnd.nextInt(1, 6), rnd.nextInt(1, 6))
                        2 -> { val t = now.plusDays(rnd.nextInt(0, 7 - (now.dayOfWeek.ordinal))); val m = listOf(0, 30, 60).random(rnd); log += "$now cambia $t a $m"; app.changeDay(t.toString(), m) }
                        3 -> screen.day.items.firstOrNull { it.alternatives.isNotEmpty() }?.let { app.chooseAlternative(now.toString(), it.sport, it.alternatives.first().sport) }
                        4 -> screen.day.items.firstOrNull { it.isStrength }?.exercises?.firstOrNull()?.let { app.logLift(now.toString(), it, listOf(null, 20.0, 60.0).random(rnd), rnd.nextInt(3, 15), 3) }
                        5 -> if (rnd.nextInt(10) == 0) { newGoal(); log += "$now objetivo ${app.data.goal?.kind}" }
                        6 -> if (screen.goalFinished) newGoal()
                        else -> {}
                    }
                } catch (e: Exception) {
                    problems += "$tag $now: ${e::class.simpleName}: ${e.message}"
                }
                now = now.plusDays(1)
            }
        }
        if (problems.isNotEmpty()) fail("${problems.size} problemas. Primeros:\n" + problems.take(5).joinToString("\n"))
    }
}

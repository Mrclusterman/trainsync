package io.github.mrclusterman.trainsync.app

import io.github.mrclusterman.trainsync.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthDataTest {
    private val today = LocalDate(2026, 10, 22)
    private val profile = UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
        cardio = listOf("running", "walking"), days = mapOf("MON" to 60, "WED" to 60, "FRI" to 60, "SAT" to 90))

    private fun snapshot() = HealthSnapshot("Health Connect",
        days = (0 until 60).map { d ->
            DailyHealth(today.plusDays(-d).toString(), sleepMinutes = 420.0 + d % 30, hrv = 50.0 + d % 9,
                restingHr = 52.0 + d % 4, weightKg = 72.0, steps = 8000.0 + 100 * (d % 10))
        },
        workouts = (1 until 40 step 2).map { d ->
            Workout("w$d", if (d % 4 == 1) "running" else "walking", "${today.plusDays(-d)}T07:30:00", 45.0,
                avgHr = if (d % 4 == 1) 150.0 else null, maxHr = 178.0,
                zoneSeconds = listOf(300.0, 1500.0, 600.0, 300.0, 0.0, 0.0, 0.0))
        },
        syncedAt = today.toString())

    @Test
    fun theAppWorksWithPhoneDataOnly() {
        val store = MemoryStore()
        val app = TrainSyncApp(store, MemoryStore(), { today })
        app.saveProfile(profile)
        app.setGoal(GoalKind.FITNESS)
        assertTrue(app.todayScreen().askReadiness || app.todayScreen().day.items.isEmpty())
        app.importHealth(snapshot())
        val progress = app.progress()
        assertTrue(progress.linked)
        assertTrue(listOf("sleep", "hrv", "restingHR", "weight", "steps", "ctl", "tsb").all { k -> progress.metrics.any { it.key == k } })
        assertTrue(progress.weeklyMinutes.any { it.value > 0 })
        assertFalse(app.todayScreen().askReadiness)  // con datos del reloj no hacen falta las preguntas
        assertFalse(app.todayScreen().linked)  // pero enviar al reloj sigue necesitando intervals.icu
        val month = app.month(2026, 10)
        assertTrue(month.weeks.flatten().any { d -> d.done.any { it.title.startsWith("Carrera ·") } })
        // Sobrevive a un reinicio y la versión del formato queda guardada
        assertEquals("Health Connect", TrainSyncApp(store, MemoryStore(), { today }).data.health?.source)
        assertEquals(SCHEMA, TrainSyncApp(store, MemoryStore(), { today }).data.schema)
    }

    @Test
    fun loadComesFromHeartRateOrDuration() {
        val cache = healthCache(snapshot(), profile, today)
        val withHr = cache.activities.first { it["id"].toString() == "\"w1\"" }
        val noHr = cache.activities.first { it["id"].toString() == "\"w3\"" }
        assertEquals("18.75", noHr["icu_training_load"].toString())  // 45 min caminando sin pulso → 25/h
        assertTrue(withHr["icu_training_load"].toString().toDouble() > 18.75)
        assertTrue(cache.wellness.last()["ctl"].toString().toDouble() > 0)
        assertEquals(today.toString(), cache.wellness.last()["id"].toString().trim('"'))
    }

    @Test
    fun watchDataAdjustsTodayWithoutAskingForHealthGoals() {
        val thursday = profile.copy(days = profile.days + ("THU" to 60))  // hoy (jueves) toca entrenar
        fun todayWith(hrv: (Int) -> Double): TodayScreen {
            val app = TrainSyncApp(MemoryStore(), MemoryStore(), { today })
            app.saveProfile(thursday)
            app.setGoal(GoalKind.FITNESS)
            val s = snapshot()
            app.importHealth(s.copy(days = s.days.mapIndexed { d, day -> day.copy(hrv = hrv(d)) }))
            return app.todayScreen()
        }
        val calm = todayWith { d -> 60.0 + d % 5 }
        assertTrue(calm.day.items.isNotEmpty())
        assertTrue(calm.day.adjustments.isEmpty())  // sin falsas alarmas
        val tired = todayWith { d -> if (d < 7) 35.0 else 60.0 + d % 5 }  // VFC hundida la última semana
        assertTrue(tired.day.adjustments.any { "variabilidad cardíaca" in it.text }, tired.day.adjustments.toString())
        assertFalse(tired.askReadiness)
        assertTrue(tired.day.items.sumOf { it.durationMin } <= calm.day.items.sumOf { it.durationMin })
    }

    @Test
    fun shortNightAgainstYourUsualSleepAdjustsToday() {
        val app = TrainSyncApp(MemoryStore(), MemoryStore(), { today })
        app.saveProfile(profile.copy(days = profile.days + ("THU" to 60)))
        app.setGoal(GoalKind.FITNESS)
        val s = snapshot()
        app.importHealth(s.copy(days = s.days.map { if (it.date == today.toString()) it.copy(sleepMinutes = 290.0) else it }))
        assertTrue(app.todayScreen().day.adjustments.any { "Has dormido 4.8 h" in it.text }, app.todayScreen().day.adjustments.toString())
    }

    @Test
    fun thresholdComesFromYourHardestSessions() {
        val s = snapshot()
        assertEquals(null, healthCache(s, profile, today).athlete["sportSettings"]?.let { (it as kotlinx.serialization.json.JsonArray).firstOrNull() })
        // Un 10 km de 42 min a 168 de media con FC máx. medida de 186: esa media es tu umbral
        val hard = s.copy(workouts = s.workouts + Workout("race", "running", "${today.plusDays(-5)}T09:00:00", 42.0, avgHr = 168.0, maxHr = 186.0))
        assertEquals(168.0, estimatedLthr(hard.workouts, "running", 186.0))
        val app = TrainSyncApp(MemoryStore(), MemoryStore(), { today })
        app.saveProfile(profile)
        app.setGoal(GoalKind.FITNESS)
        app.importHealth(hard)
        assertEquals(168.0, Planner(app.data, today).physiology?.lthrFor("running"))
        assertEquals(186.0, Planner(app.data, today).physiology?.maxHr)
        // Un rodaje suave no sirve: queda por debajo del 85 % de la FC máx.
        assertEquals(null, estimatedLthr(listOf(Workout("easy", "running", "${today}T07:00:00", 45.0, avgHr = 140.0)), "running", 186.0))
        assertEquals(0.95 * 186, estimatedLthr(listOf(Workout("x", "running", "${today}T07:00:00", 30.0, avgHr = 184.0)), "running", 186.0))
    }

    @Test
    fun withoutSleepOrHrvTheQuestionsStay() {
        val app = TrainSyncApp(MemoryStore(), MemoryStore(), { today })
        app.saveProfile(profile.copy(days = profile.days + ("THU" to 60)))
        app.setGoal(GoalKind.FITNESS)
        val s = snapshot()
        app.importHealth(s.copy(days = s.days.map { it.copy(sleepMinutes = null, hrv = null) }))  // solo pasos y peso
        assertTrue(app.todayScreen().askReadiness)
        app.answerReadiness(2, 2, 3)
        assertFalse(app.todayScreen().askReadiness)
        assertTrue(app.todayScreen().day.adjustments.isNotEmpty())
    }

    @Test
    fun progressShowsStatusPredictionsAndRecentWorkouts() {
        val app = TrainSyncApp(MemoryStore(), MemoryStore(), { today })
        app.saveProfile(profile)
        app.setGoal(GoalKind.FITNESS)
        val s = snapshot()
        app.importHealth(s.copy(days = s.days.map { it.copy(vo2max = 50.0) }))
        val p = app.progress()
        kotlin.test.assertNotNull(p.status)
        assertEquals(listOf("5 km", "10 km", "Media maratón", "Maratón"), p.predictions.map { it.distance })
        assertTrue(p.predictions.first().time.startsWith("19:5"), p.predictions.first().time)  // VDOT 50: 19:57 (Daniels)
        assertTrue(p.predictionSource!!.startsWith("tu VO2max (50)"))
        assertEquals(10, p.recent.size)
        assertTrue(p.recent.zipWithNext().all { (a, b) -> a.date >= b.date })
        assertTrue(p.weeklyMinutes.takeLast(4).all { it.value > 0 })  // los paseos sin pulso también cuentan
        // Sin VO2max ni carreras con distancia, no se inventa una previsión
        app.importHealth(s)
        assertTrue(app.progress().predictions.isEmpty())
    }

    @Test
    fun oldDataIsMigrated() {
        val old = AppData(schema = 1, profile = profile)
        assertEquals(SCHEMA, migrate(old).schema)
        assertEquals(profile, migrate(old).profile)
    }
}

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
        assertEquals("37.5", noHr["icu_training_load"].toString())  // 45 min sin pulso → 50/h
        assertTrue(withHr["icu_training_load"].toString().toDouble() > 37.5)
        assertTrue(cache.wellness.last()["ctl"].toString().toDouble() > 0)
        assertEquals(today.toString(), cache.wellness.last()["id"].toString().trim('"'))
    }

    @Test
    fun oldDataIsMigrated() {
        val old = AppData(schema = 1, profile = profile)
        assertEquals(SCHEMA, migrate(old).schema)
        assertEquals(profile, migrate(old).profile)
    }
}

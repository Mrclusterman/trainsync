package io.github.mrclusterman.trainsync.app

import io.github.mrclusterman.trainsync.engine.plusDays
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryStore : KeyValueStore {
    val map = mutableMapOf<String, String>()
    override fun read(key: String) = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

class TrainSyncAppTest {
    private val today = LocalDate(2026, 10, 22)  // jueves
    private val profile = UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
        cardio = listOf("running", "cycling"), days = mapOf("MON" to 60, "TUE" to 60, "THU" to 75, "SAT" to 90, "SUN" to 60))

    private fun app(store: KeyValueStore = MemoryStore(), secrets: KeyValueStore = MemoryStore(), engine: MockEngine? = null) =
        TrainSyncApp(store, secrets, { today }, engine)

    @Test
    fun onboardingAsksForProfileAndGoalAndGoalCanBeAbandoned() {
        val a = app()
        assertTrue(a.needsOnboarding)
        a.saveProfile(profile)
        assertTrue(a.needsOnboarding)
        a.setGoal(GoalKind.FITNESS)
        assertFalse(a.needsOnboarding)
        assertEquals("2026-10-19", a.data.blockStart)  // el bloque empieza el lunes de esta semana
        a.abandonGoal()
        assertTrue(a.needsOnboarding)
        assertFailsWith<IllegalArgumentException> { a.setGoal(GoalKind.RACE) }  // una carrera necesita sus datos
    }

    @Test
    fun dataSurvivesARestart() {
        val store = MemoryStore()
        app(store).apply { saveProfile(profile); setGoal(GoalKind.STRENGTH) }
        assertEquals(GoalKind.STRENGTH, app(store).data.goal?.kind)
    }

    @Test
    fun headlineAlwaysMatchesTheSessionOfTheDay() {
        // Punto 1 de la revisión: nunca "recuperación" arriba y otra sesión abajo
        for (kind in listOf(GoalKind.FITNESS, GoalKind.STRENGTH, GoalKind.RACE)) {
            for (offset in 0 until 21) {
                val date = today.plusDays(offset)
                val a = TrainSyncApp(MemoryStore(), MemoryStore(), { date })
                a.saveProfile(profile)
                a.setGoal(kind, if (kind == GoalKind.RACE) RaceInfo("Maratón", "running", "2027-02-14", distanceKm = 42.2) else null)
                val screen = a.todayScreen()
                val main = screen.day.items.firstOrNull { !it.isStrength }
                when {
                    screen.day.items.isEmpty() -> assertTrue(screen.headline.startsWith("Hoy descansas"), screen.headline)
                    main != null -> assertTrue(main.title.lowercase() in screen.headline, "${screen.headline} / ${main.title}")
                }
            }
        }
    }

    @Test
    fun fatigueAdjustmentDrivesTheHeadline() {
        val store = MemoryStore()
        val a = app(store)
        a.saveProfile(profile)
        a.setGoal(GoalKind.RACE, RaceInfo("Maratón", "running", "2027-02-14", distanceKm = 42.2))
        // Muy cargado (TSB < −30): el motor pasa a recuperación y la frase lo dice
        val data = a.data.copy(cache = cache(ctl = 50.0, atl = 95.0), intervalsAthlete = "i1")
        store.write("trainsync.data", Json.encodeToString(AppData.serializer(), data))
        val screen = app(store).todayScreen()
        if (screen.day.items.isNotEmpty()) {
            assertTrue(screen.headline.startsWith("Hoy toca bajar el ritmo"), screen.headline)
            assertEquals("recovery", screen.day.items.first { !it.isStrength }.kind)
            assertTrue(screen.headlineDetail.any { "TSB" in it.text })
        }
    }

    @Test
    fun chosenAlternativeBecomesTheMainSession() {
        val a = app()
        a.saveProfile(profile)
        a.setGoal(GoalKind.FITNESS)
        val dayWithAlt = (0 until 7).map { today.plusDays(-3 + it) }
            .map { a.day(it.toString()) }.first { d -> d.items.any { it.alternatives.isNotEmpty() } }
        val planned = dayWithAlt.items.first { it.alternatives.isNotEmpty() }
        val alt = planned.alternatives.first()
        a.chooseAlternative(dayWithAlt.date, planned.sport, alt.sport)
        val after = a.day(dayWithAlt.date).items.first { it.chosen }
        assertEquals(alt.sport, after.sport)
        assertEquals(planned.sport, after.slot)  // ocupa el sitio de la planificada en el calendario del reloj
        assertTrue(after.alternatives.any { it.sport == planned.sport })
    }

    @Test
    fun cannotMarkFutureSessionsAndChangesOnlyThisWeek() {
        val a = app()
        a.saveProfile(profile)
        a.setGoal(GoalKind.FITNESS)
        assertFailsWith<IllegalArgumentException> { a.markDone(today.plusDays(1).toString(), "running") }
        a.markDone(today.toString(), "running")
        assertTrue(a.data.manualDone.single().sport == "running")
        assertFailsWith<IllegalArgumentException> { a.changeDay(today.plusDays(7).toString(), 0) }
        a.changeDay(today.plusDays(1).toString(), 0)
        assertTrue(a.day(today.plusDays(1).toString()).items.isEmpty())
    }

    @Test
    fun monthShowsWhatYouDidWithAMeaningfulTitle() {
        val store = MemoryStore()
        val a = app(store)
        a.saveProfile(profile)
        a.setGoal(GoalKind.FITNESS)
        store.write("trainsync.data", Json.encodeToString(AppData.serializer(), a.data.copy(cache = cache())))
        val month = app(store).month(2026, 10)
        assertEquals("Octubre 2026", month.title)
        assertTrue(month.weeks.all { it.size == 7 })
        val yesterday = month.weeks.flatten().first { it.date == "2026-10-21" }
        assertEquals("Carrera · Tempo (Z3)", yesterday.done.single().title)
        assertTrue(month.weeks.flatten().filter { it.date > today.toString() }.all { it.done.isEmpty() })
    }

    @Test
    fun progressExplainsEachMetric() {
        val store = MemoryStore()
        val a = app(store)
        a.saveProfile(profile)
        a.setGoal(GoalKind.HEALTH)
        assertFalse(a.progress().linked)
        store.write("trainsync.data", Json.encodeToString(AppData.serializer(), a.data.copy(cache = cache())))
        val p = app(store).progress()
        val hrv = p.metrics.first { it.key == "hrv" }
        assertTrue("HRV" in hrv.explanation && hrv.normalLow != null && hrv.points.size > 30)
        assertNotNull(p.metrics.firstOrNull { it.key == "tsb" })
        assertTrue(p.weeklyMinutes.size == 12)
    }

    @Test
    fun linkSyncRenameSendAndForget() = runBlocking {
        val calls = mutableListOf<String>()
        val sent = mutableListOf<JsonObject>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            calls += "${request.method.value} $path"
            if (request.headers[HttpHeaders.Authorization] != "Basic QVBJX0tFWTpidWVuYQ==") {
                return@MockEngine respond("no", HttpStatusCode.Unauthorized)
            }
            val body = when {
                request.method == HttpMethod.Post -> {
                    val events = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonArray
                    sent += events.map { it.jsonObject }
                    JsonArray(events.mapIndexed { n, e -> JsonObject(e.jsonObject + ("id" to JsonPrimitive(n))) }).toString()
                }
                request.method == HttpMethod.Put -> "{}"
                path.endsWith("/wellness") -> cache().wellness.let { JsonArray(it) }.toString()
                path.endsWith("/activities") -> cache().activities.let { JsonArray(it) }.toString()
                else -> cache().athlete.toString()
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val secrets = MemoryStore()
        val a = app(secrets = secrets, engine = engine)
        a.saveProfile(profile)
        a.setGoal(GoalKind.FITNESS)
        assertFailsWith<IllegalArgumentException> { a.linkIntervals("i1", "mala") }
        assertEquals(null, a.linkedAthlete)
        a.linkIntervals(" i1 ", "buena")
        assertEquals("i1", a.linkedAthlete)
        assertNotNull(a.data.cache)
        assertTrue(calls.any { it == "PUT /api/v1/activity/act-1" })  // la de ayer, con su título de verdad
        assertTrue("act-1" in a.data.renamed)
        assertFalse("buena" in a.exportJson())  // la clave nunca sale en la exportación

        val day = a.todayScreen().day
        if (day.items.isNotEmpty()) {
            val names = a.sendToday()
            assertEquals(day.items.size, names.size)
            assertTrue(sent.all { (it["external_id"] as JsonPrimitive).content.startsWith("trainsync-${today}") })
        }
        a.deleteAll()
        assertEquals(null, secrets.read("intervals.apiKey"))
        assertTrue(a.needsOnboarding)
    }

    /** Datos sintéticos de intervals.icu: 90 días de wellness y una carrera de tempo ayer. */
    private fun cache(ctl: Double = 55.0, atl: Double = 50.0): IntervalsCache {
        val athlete = buildJsonObject {
            put("icu_resting_hr", 48)
            put("sportSettings", buildJsonArray {
                add(buildJsonObject { put("types", buildJsonArray { add(JsonPrimitive("Run")) }); put("lthr", 172); put("max_hr", 190) })
                add(buildJsonObject { put("types", buildJsonArray { add(JsonPrimitive("Ride")) }); put("lthr", 165); put("max_hr", 188) })
            })
        }
        val wellness = (0 until 90).map { d ->
            buildJsonObject {
                put("id", today.plusDays(-d).toString())
                put("ctl", ctl); put("atl", atl)
                put("hrv", 55.0 + (d % 7)); put("restingHR", 48 + d % 3); put("sleepSecs", 25000 + 600 * (d % 5))
                put("sleepScore", 70 + d % 20)
            }
        }
        val activities = listOf(buildJsonObject {
            put("id", "act-1"); put("name", "Carrera matutina"); put("type", "Run")
            put("start_date_local", "2026-10-21T07:30:00"); put("moving_time", 3000)
            put("icu_training_load", 60)
            put("icu_hr_zone_times", buildJsonArray { listOf(300, 900, 1500, 300, 0, 0, 0).forEach { add(JsonPrimitive(it)) } })
        })
        return IntervalsCache(athlete, wellness, activities, today.toString())
    }
}

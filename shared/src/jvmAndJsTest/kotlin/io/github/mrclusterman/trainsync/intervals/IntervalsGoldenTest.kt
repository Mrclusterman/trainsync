package io.github.mrclusterman.trainsync.intervals

import io.github.mrclusterman.trainsync.engine.HealthStrength
import io.github.mrclusterman.trainsync.engine.Profile
import io.github.mrclusterman.trainsync.engine.Session
import io.github.mrclusterman.trainsync.engine.rewriteGolden
import io.github.mrclusterman.trainsync.engine.updatingGolden
import io.github.mrclusterman.trainsync.engine.WorkoutEvent
import io.github.mrclusterman.trainsync.engine.diff
import io.github.mrclusterman.trainsync.engine.golden
import io.github.mrclusterman.trainsync.engine.strengthEvent
import io.github.mrclusterman.trainsync.engine.workoutEvent
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/** Texto para el reloj y lectura de intervals.icu iguales que en los casos de referencia (resources/golden/intervals.json). */
class IntervalsGoldenTest {
    private val cases = golden("intervals")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val profiles = mapOf(
        "fc" to Profile(listOf("running", "cycling"), lthr = 172.0, maxHr = 190.0),
        "ritmo y potencia" to Profile(listOf("running", "cycling"), lthr = 170.0, maxHr = 188.0, runPace = 265.0, ftp = 250.0),
        "modelo" to Profile(listOf("running"), lthr = 174.0, maxHr = 192.0, runModel = 0.9 to 0.0165),
        "natación" to Profile(listOf("swimming_pool", "swimming_open"), css = 98.0),
        "sin datos" to Profile(listOf("running", "walking")),
    )

    private fun check(kind: String, run: (JsonElement) -> JsonElement?) {
        if (updatingGolden) return rewriteGolden("intervals", kind, cases[kind]!!.jsonArray.map { run(it.jsonObject["in"]!!) })
        val failures = cases[kind]!!.jsonArray.mapNotNull { c ->
            val case = c.jsonObject
            diff("", case["out"], run(case["in"]!!))?.let { "${case["in"].toString().take(300)}\n    $it" }
        }
        if (failures.isNotEmpty()) fail("$kind: ${failures.size} de ${cases[kind]!!.jsonArray.size} casos distintos. Primero:\n${failures.first()}")
    }

    private fun profileJson(p: Profile) = buildJsonObject {
        put("active_sports", JsonArray(p.activeSports.map { JsonPrimitive(it) }))
        put("strength", p.strength)
        put("rest_days", JsonArray(p.restDays.map { JsonPrimitive(it) }))
        put("lthr", p.lthr?.let { JsonPrimitive(it) } ?: JsonNull)
        put("lthr_by_sport", JsonObject(p.lthrBySport.mapValues { JsonPrimitive(it.value) }))
        put("max_hr", p.maxHr?.let { JsonPrimitive(it) } ?: JsonNull)
        put("rest_hr", p.restHr?.let { JsonPrimitive(it) } ?: JsonNull)
        put("css", p.css?.let { JsonPrimitive(it) } ?: JsonNull)
        put("run_pace", p.runPace?.let { JsonPrimitive(it) } ?: JsonNull)
        put("ftp", p.ftp?.let { JsonPrimitive(it) } ?: JsonNull)
        put("warnings", JsonArray(p.warnings.map { JsonPrimitive(it) }))
        put("needs_test", JsonArray(p.needsTest.sorted().map { JsonPrimitive(it) }))
    }

    private fun objects(e: JsonElement?) = e!!.jsonArray.map { it.jsonObject }

    @Test
    fun describe() = check("describe") { i ->
        val c = i.jsonObject
        val session = json.decodeFromJsonElement<Session>(c["session"]!!)
        json.encodeToJsonElement(workoutEvent(session, c["day"].str()!!, profiles[c["profile"].str()]!!, c["key"].str()))
    }

    @Test
    fun strengthEvents() = check("strength_event") { i ->
        val c = i.jsonObject
        json.encodeToJsonElement(strengthEvent(json.decodeFromJsonElement<HealthStrength>(c["session"]!!), c["day"].str()!!))
    }

    @Test
    fun fillPhysiology() = check("fill_physiology") { i ->
        val c = i.jsonObject
        val p = fillPhysiology(Profile(c["active_sports"]!!.jsonArray.map { it.str()!! }, lthr = c["lthr"].num()),
            c["athlete"]!!.jsonObject)
        profileJson(p)
    }

    @Test
    fun dayState() = check("day_state") { i ->
        val c = i.jsonObject
        val s = dayState(objects(c["wellness"]), objects(c["activities"]), LocalDate.parse(c["today"].str()!!))
        buildJsonObject {
            put("ctl", s.ctl)
            put("atl", s.atl)
            put("hrv_history", JsonArray(s.hrvHistory.map { it?.let { JsonPrimitive(it) } ?: JsonNull }))
            put("sleep_score", s.sleepScore?.let { JsonPrimitive(it) } ?: JsonNull)
            put("consecutive_days", s.consecutiveDays)
            put("effort_mismatch", s.effortMismatch)
            put("load_focus", s.loadFocus?.let { f -> JsonObject(f.mapValues { JsonPrimitive(it.value) }) } ?: JsonNull)
            put("days_since_activity", s.daysSinceActivity?.let { JsonPrimitive(it) } ?: JsonNull)
        }
    }

    @Test
    fun raceToGoal() = check("race_to_goal") { i ->
        val g = raceToGoal(i.jsonObject)
        buildJsonObject {
            put("name", g.name); put("sport", g.sport); put("date", g.date.toString()); put("priority", g.priority)
            put("distance_km", g.distanceKm?.let { JsonPrimitive(it) } ?: JsonNull); put("trail", g.trail)
        }
    }

    @Test
    fun pickGoal() = check("pick_goal") { i ->
        val c = i.jsonObject
        pickGoal(objects(c["races"]).map { raceToGoal(it) }, LocalDate.parse(c["today"].str()!!))?.let { JsonPrimitive(it.name) }
    }

    @Test
    fun lastActivity() = check("last_activity") { i ->
        lastActivity(objects(i))?.let { a ->
            buildJsonObject {
                put("name", a.name); put("date", a.date); put("sport", a.sport); put("duration_min", a.durationMin)
                put("tss", a.tss?.let { JsonPrimitive(it) } ?: JsonNull)
                put("hr_zone_secs", JsonArray(a.hrZoneSecs.map { JsonPrimitive(it) })); put("gray_zone", a.grayZone)
            }
        }
    }

    @Test
    fun watchSessions() = check("watch_sessions") { i ->
        JsonObject(watchSessions(objects(i)).mapValues { (_, done) ->
            JsonArray(done.map { d ->
                buildJsonObject {
                    put("sport", d.sport); put("source", d.source); put("minutes", d.minutes)
                    put("start", d.start?.let { JsonPrimitive(it) } ?: JsonNull)
                }
            })
        })
    }

    @Test
    fun clientSendsAuthAndUpserts() = runTest {
        val seen = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { request ->
            seen += request.method.value to request.url.toString()
            assertEquals("Basic QVBJX0tFWTpjbGF2ZQ==", request.headers[HttpHeaders.Authorization])  // API_KEY:clave
            val body = if (request.method == HttpMethod.Post) {
                val sent = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonArray
                JsonArray(sent.mapIndexed { n, e -> JsonObject(e.jsonObject + ("id" to JsonPrimitive(100 + n))) }).toString()
            } else "[]"
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = IntervalsClient("i123", "clave", engine)
        val event = WorkoutEvent(startDateLocal = "2026-10-22T00:00:00", type = "Run", name = "TrainSync · Tempo",
            description = "- 10m", movingTime = 600, externalId = "trainsync-2026-10-22")
        val created = client.upsertWorkouts(listOf(event))
        assertEquals("100", created.single()["id"].toString())
        assertTrue(seen.single().second.startsWith("https://intervals.icu/api/v1/athlete/i123/events/bulk?upsert=true"))
        client.activities(LocalDate(2026, 10, 1), LocalDate(2026, 10, 22))
        assertEquals("https://intervals.icu/api/v1/athlete/i123/activities?oldest=2026-10-01&newest=2026-10-22", seen.last().second)
    }

    @Test
    fun clientReportsBadKey() = runTest {
        val client = IntervalsClient("i1", "mala", MockEngine { respond("Unauthorized", HttpStatusCode.Unauthorized) })
        assertEquals(401, assertFailsWith<IntervalsException> { client.athlete() }.status)
    }
}

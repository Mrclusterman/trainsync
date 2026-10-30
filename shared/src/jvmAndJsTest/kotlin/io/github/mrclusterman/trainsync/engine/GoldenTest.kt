package io.github.mrclusterman.trainsync.engine

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * El motor da lo mismo que en los casos de referencia (resources/golden/engine.json). Ver `rewriteGolden` para
 * regenerarlos tras un cambio a propósito.
 */
class GoldenTest {
    private val golden = golden("engine")
    private val cases = golden["cases"]!!.jsonObject
    private val json = Json { encodeDefaults = true }

    private fun JsonElement?.str() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement?.dbl() = (this as? JsonPrimitive)?.doubleOrNull

    private fun profile(name: String): Profile {
        val p = golden["profiles"]!!.jsonObject[name]!!.jsonObject
        return Profile(
            activeSports = p["active_sports"]!!.jsonArray.map { it.str()!! },
            strength = p["strength"].str() ?: "gym",
            restDays = p["rest_days"]?.jsonArray?.map { it.str()!! } ?: listOf("MON"),
            lthr = p["lthr"].dbl(),
            lthrBySport = p["lthr_by_sport"]?.jsonObject?.mapValues { it.value.dbl()!! } ?: emptyMap(),
            maxHr = p["max_hr"].dbl(),
            css = p["css"].dbl(),
            runModel = p["run_model"]?.jsonArray?.let { it[0].dbl()!! to it[1].dbl()!! },
            dayMinutes = p["day_minutes"]?.jsonObject?.mapValues { it.value.jsonPrimitive.int },
            needsTest = p["needs_test"]?.jsonArray?.map { it.str()!! }?.toSet() ?: emptySet(),
        )
    }

    private fun goal(name: String): Goal? {
        val g = golden["goals"]!!.jsonObject[name] as? JsonObject ?: return null
        return Goal(g["name"].str()!!, g["sport"].str()!!, LocalDate.parse(g["date"].str()!!), g["priority"].str()!!,
            g["distance_km"].dbl(), (g["trail"] as JsonPrimitive).booleanOrNull!!)
    }

    private fun state(name: String): DayState? {
        val s = golden["states"]!!.jsonObject[name] as? JsonObject ?: return null
        return DayState(
            ctl = s["ctl"].dbl()!!, atl = s["atl"].dbl()!!,
            hrvHistory = s["hrv_history"]!!.jsonArray.map { it.dbl() },
            sleepScore = s["sleep_score"].dbl(),
            consecutiveDays = s["consecutive_days"]!!.jsonPrimitive.int,
            effortMismatch = (s["effort_mismatch"] as JsonPrimitive).booleanOrNull!!,
            loadFocus = (s["load_focus"] as? JsonObject)?.mapValues { it.value.dbl()!! },
            daysSinceActivity = (s["days_since_activity"] as? JsonPrimitive)?.doubleOrNull?.toInt(),
        )
    }

    private fun check(kind: String, run: (JsonObject) -> JsonElement?) {
        if (updatingGolden) return rewriteGolden("engine", kind, cases[kind]!!.jsonArray.map { run(it.jsonObject["in"]!!.jsonObject) })
        val failures = cases[kind]!!.jsonArray.mapNotNull { c ->
            val case = c.jsonObject
            diff("", case["out"], run(case["in"]!!.jsonObject))?.let { "${case["in"]}\n    $it" }
        }
        if (failures.isNotEmpty()) fail("$kind: ${failures.size} casos distintos. Primero:\n${failures.first()}")
    }

    @Test
    fun buildSession() = check("build_session") { i ->
        buildSession(i["kind"].str()!!, i["sport"].str()!!, profile(i["profile"].str()!!), i["volume"].dbl()!!,
            i["phase"].str()!!, i["week"]!!.jsonPrimitive.int, i["event"].str(), i["max_minutes"].dbl()?.toInt())
            ?.let { json.encodeToJsonElement(it) }
    }

    @Test
    fun plan() = check("plan") { i ->
        json.encodeToJsonElement(plan(LocalDate.parse(i["start"].str()!!), i["days"]!!.jsonPrimitive.int,
            profile(i["profile"].str()!!), goal(i["goal"].str()!!), state(i["state"].str()!!)))
    }

    @Test
    fun phaseFor() = check("phase_for") { i ->
        val (phase, recovery) = phaseFor(LocalDate.parse(i["day"].str()!!), goal(i["goal"].str()!!), null)
        JsonArray(listOf(JsonPrimitive(phase), JsonPrimitive(recovery)))
    }

    @Test
    fun fitDays() = check("fit_days") { i ->
        val (placed, moved) = fitDays(TEMPLATES[i["phase"].str()!!]!!, i["rest_days"]!!.jsonArray.map { it.str()!! })
        JsonArray(listOf(json.encodeToJsonElement(placed), json.encodeToJsonElement(moved)))
    }

    @Test
    fun monotony() {
        for (c in cases["monotony"]!!.jsonArray) {
            val (load, mono, strain) = fosterMonotony(c.jsonObject["in"]!!.jsonArray.map { it.dbl()!! })
            val got = JsonArray(listOf(JsonPrimitive(load), mono?.let { JsonPrimitive(it) } ?: JsonNull, JsonPrimitive(strain)))
            diff("", c.jsonObject["out"], got)?.let { fail("monotony: $it") }
        }
    }

    @Test
    fun hrPaceModel() {
        val case = cases["hr_pace_model"]!!.jsonArray.first().jsonObject
        val samples = case["in"]!!.jsonArray.map { s -> s.jsonArray.let { RunSample(it[0].dbl(), it[1].dbl(), it[2].dbl()) } }
        val model = hrPaceModel(samples)!!
        val expected = case["out"]!!.jsonArray
        assertTrue(abs(model.first - expected[0].dbl()!!) < 1e-9 && abs(model.second - expected[1].dbl()!!) < 1e-9)
    }
}

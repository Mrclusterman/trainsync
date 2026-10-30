package io.github.mrclusterman.trainsync.engine

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.fail

/** Los planes de salud dan lo mismo que en los casos de referencia (resources/golden/health.json). */
class HealthGoldenTest {
    private val data = golden("health")
    private val cases = data["cases"]!!.jsonObject
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun JsonElement?.str() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement?.dbl() = (this as? JsonPrimitive)?.doubleOrNull

    private fun profile(name: String): HealthProfile {
        val p = data["profiles"]!!.jsonObject[name]!!.jsonObject
        return HealthProfile(
            goal = p["goal"].str()!!,
            days = p["days"]!!.jsonObject.mapValues { it.value.jsonPrimitive.int },
            strength = p["strength"].str() ?: "gym",
            cardio = p["cardio"]?.jsonArray?.map { it.str()!! } ?: listOf("walking"),
            level = p["level"].str() ?: "beginner",
            age = p["age"].dbl()?.toInt(),
            intensityCap = p["intensity_cap"].str() ?: "vigorous",
            lthr = p["lthr"].dbl(), maxHr = p["max_hr"].dbl(), css = p["css"].dbl(),
            keep = p["keep"]!!.jsonArray.map { it.str()!! }.toSet(),
        )
    }

    private fun planned(e: JsonElement): Planned =
        if (e.jsonObject["sport"].str() == "strength") json.decodeFromJsonElement<HealthStrength>(e)
        else HealthCardio(json.decodeFromJsonElement<Session>(e))

    private fun enc(s: Planned): JsonElement = when (s) {
        is HealthStrength -> json.encodeToJsonElement(s)
        is HealthCardio -> json.encodeToJsonElement(s.session)
        is RaceStrengthItem -> json.encodeToJsonElement(s.strength)
    }

    private fun enc(w: WeekPlan): JsonElement = buildJsonObject {
        put("goal", w.goal)
        put("week_in_block", w.weekInBlock)
        put("dose", json.encodeToJsonElement(w.dose))
        put("days", JsonArray(w.days.map { d ->
            buildJsonObject {
                put("date", d.date)
                put("weekday", d.weekday)
                put("sessions", JsonArray(d.sessions.map { enc(it) }))
                d.note?.let { put("note", it) }
                d.availableMin?.let { put("available_min", it) }
            }
        }))
        put("warnings", JsonArray(w.warnings.map { JsonPrimitive(it) }))
        put("info", JsonArray(w.info.map { JsonPrimitive(it) }))
        put("equivalent_min", w.equivalentMin)
        w.changed?.let { put("changed", it) }
    }

    private fun done(e: JsonElement) = e.jsonObject.let {
        Done(it["sport"].str()!!, it["source"].str()!!, it["minutes"].dbl()?.toInt(), it["start"].str())
    }

    private fun check(kind: String, run: (JsonObject) -> JsonElement?) {
        if (updatingGolden) return rewriteGolden("health", kind, cases[kind]!!.jsonArray.map { run(it.jsonObject["in"]!!.jsonObject) })
        val failures = cases[kind]!!.jsonArray.mapNotNull { c ->
            val case = c.jsonObject
            diff("", case["out"], run(case["in"]!!.jsonObject))?.let { "${case["in"].toString().take(300)}\n    $it" }
        }
        if (failures.isNotEmpty()) fail("$kind: ${failures.size} de ${cases[kind]!!.jsonArray.size} casos distintos. Primero:\n${failures.first()}")
    }

    private fun doubles(e: JsonElement?) = e!!.jsonArray.map { it.dbl()!! }

    @Test
    fun weekDose() = check("week_dose") { i ->
        json.encodeToJsonElement(weekDose(profile(i["profile"].str()!!), doubles(i["adherence"]), i["week_in_block"]!!.jsonPrimitive.int))
    }

    @Test
    fun spread() = check("spread") { i ->
        JsonArray(spread(i["days"]!!.jsonArray.map { it.str()!! }, i["n"]!!.jsonPrimitive.int).map { JsonPrimitive(it) })
    }

    @Test
    fun weekPlan() = check("week_plan") { i ->
        enc(weekPlan(profile(i["profile"].str()!!), LocalDate.parse(i["start"].str()!!), doubles(i["adherence"]),
            i["week_in_block"]!!.jsonPrimitive.int))
    }

    @Test
    fun weekWithChanges() = check("week_with_changes") { i ->
        enc(weekWithChanges(profile(i["profile"].str()!!), LocalDate.parse(i["start"].str()!!), doubles(i["adherence"]),
            i["week_in_block"]!!.jsonPrimitive.int, i["changes"]!!.jsonObject.mapValues { it.value.jsonPrimitive.int },
            LocalDate.parse(i["since"].str()!!)))
    }

    @Test
    fun adjustDay() = check("adjust_day") { i ->
        val (sessions, note) = adjustDay(profile(i["profile"].str()!!), i["sessions"]!!.jsonArray.map { planned(it) },
            i["status"].str()!!, i["week"]!!.jsonPrimitive.int)
        buildJsonObject {
            put("sessions", JsonArray(sessions.map { enc(it) }))
            put("note", note?.let { JsonPrimitive(it) } ?: JsonNull)
        }
    }

    @Test
    fun alternatives() = check("alternatives") { i ->
        val s = (planned(i["session"]!!) as HealthCardio).session
        JsonArray(alternatives(profile(i["profile"].str()!!), s, i["week"]!!.jsonPrimitive.int).map { json.encodeToJsonElement(it) })
    }

    @Test
    fun matchDay() = check("match_day") { i ->
        JsonArray(matchDay(i["sessions"]!!.jsonArray.map { planned(it) }, i["done"]!!.jsonArray.map { done(it) }).map { m ->
            buildJsonObject {
                put("sport", m.done.sport)
                put("source", m.done.source)
                put("minutes", m.done.minutes?.let { JsonPrimitive(it) } ?: JsonNull)
                put("start", m.done.start?.let { JsonPrimitive(it) } ?: JsonNull)
                put("matched", m.matched?.let { JsonPrimitive(it) } ?: JsonNull)
                put("why", m.why)
            }
        })
    }

    @Test
    fun adherence() = check("adherence") { i ->
        val history = adherenceHistory(profile(i["profile"].str()!!), LocalDate.parse(i["block_start"].str()!!),
            i["done"]!!.jsonObject.mapValues { (_, v) -> v.jsonArray.map { done(it) } }, i["blocks"]!!.jsonPrimitive.int,
            i["changes"]!!.jsonObject.mapValues { (_, v) ->
                listOf(v.jsonArray[0].jsonObject.mapValues { it.value.jsonPrimitive.int } to LocalDate.parse(v.jsonArray[1].str()!!))
            })
        JsonArray(history.map { JsonPrimitive(it) })
    }
}

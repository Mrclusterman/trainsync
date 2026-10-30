package io.github.mrclusterman.trainsync.web

import io.github.mrclusterman.trainsync.app.GoalKind
import io.github.mrclusterman.trainsync.app.KeyValueStore
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.UserProfile
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

private val fs: dynamic = js("process.getBuiltinModule('fs')")

/** web/src/types.ts está escrito a mano: cada campo que declara tiene que salir en el JSON de la pantalla. */
class WebTypesTest {
    private class MemoryStore : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private fun keys(e: JsonElement): Set<String> = when (e) {
        is JsonObject -> e.keys + e.values.flatMap { keys(it) }
        is JsonArray -> e.flatMap { keys(it) }.toSet()
        else -> emptySet()
    }

    @Test
    fun typesMatchTodayJson() {
        // Viernes de descanso (con su motivo) y sábado con bici por pasos y fuerza: entre los dos salen todos los tipos
        var today = LocalDate(2026, 10, 30)
        val app = TrainSyncApp(MemoryStore(), MemoryStore(), { today })
        app.saveProfile(UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
            cardio = listOf("running", "cycling"), days = mapOf("MON" to 60, "TUE" to 60, "THU" to 75, "SAT" to 90, "SUN" to 60)))
        app.setGoal(GoalKind.FITNESS)
        val json = Json { encodeDefaults = true }
        val sent = listOf(30, 31).flatMap { d ->
            today = LocalDate(2026, 10, d)
            keys(Json.parseToJsonElement(json.encodeToString(app.todayScreen())))
        }.toSet()
        val types = fs.readFileSync(js("process.env.WEB_TYPES"), "utf8") as String
        val declared = Regex("""(\w+)\??:\s""").findAll(types.substringAfter("export type Tone")).map { it.groupValues[1] }.toSet()
        assertEquals(emptySet(), declared - sent, "campos de web/src/types.ts que el núcleo ya no manda")
    }
}

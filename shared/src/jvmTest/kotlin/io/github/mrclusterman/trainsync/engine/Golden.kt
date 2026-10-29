package io.github.mrclusterman.trainsync.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.math.abs

/**
 * Casos de referencia: entradas y la salida esperada del motor. Los generó el prototipo de Python; desde 2026-10-29 el
 * motor de Kotlin es la única fuente, así que tras un cambio a propósito se regeneran con
 * `UPDATE_GOLDEN=1 ./gradlew :shared:jvmTest` (y se revisa el diff antes de guardarlo).
 */
internal fun golden(name: String): JsonObject =
    Json.parseToJsonElement(Golden::class.java.getResource("/golden/$name.json")!!.readText()).jsonObject

private object Golden

internal val updatingGolden = System.getenv("UPDATE_GOLDEN") == "1"

/** Reescribe la salida esperada de cada caso de `kind` con la que da ahora el motor. */
internal fun rewriteGolden(name: String, kind: String, outs: List<JsonElement?>) {
    val file = File("src/jvmTest/resources/golden/$name.json")
    val root = Json.parseToJsonElement(file.readText()).jsonObject
    val nested = "cases" in root
    val cases = if (nested) root["cases"]!!.jsonObject else root
    val updated = JsonArray(cases[kind]!!.jsonArray.mapIndexed { i, c -> JsonObject(c.jsonObject + ("out" to (outs[i] ?: JsonNull))) })
    val newCases = JsonObject(cases + (kind to updated))
    file.writeText(Json.encodeToString(JsonObject.serializer(), if (nested) JsonObject(root + ("cases" to newCases)) else newCases))
}

/** Igualdad de JSON sin distinguir "clave con null" de "sin clave" ni 10 de 10.0. Devuelve la primera diferencia. */
internal fun diff(path: String, a: JsonElement?, b: JsonElement?): String? {
    val x = a ?: JsonNull
    val y = b ?: JsonNull
    return when {
        x is JsonObject && y is JsonObject -> (x.keys + y.keys).firstNotNullOfOrNull { k ->
            diff("$path.$k", x[k].takeUnless { it is JsonNull }, y[k].takeUnless { it is JsonNull })
        }
        x is JsonArray && y is JsonArray ->
            if (x.size != y.size) "$path: ${x.size} elementos frente a ${y.size}"
            else x.indices.firstNotNullOfOrNull { diff("$path[$it]", x[it], y[it]) }
        x is JsonPrimitive && y is JsonPrimitive && !x.isString && !y.isString && x.doubleOrNull != null ->
            if (y.doubleOrNull != null && abs(x.doubleOrNull!! - y.doubleOrNull!!) < 1e-9) null else "$path: $x ≠ $y"
        else -> if (x == y) null else "$path: $x ≠ $y"
    }
}

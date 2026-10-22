package io.github.mrclusterman.trainsync.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs

/** Casos de referencia del prototipo (tools/export_golden*.py). */
internal fun golden(name: String): JsonObject =
    Json.parseToJsonElement(Golden::class.java.getResource("/golden/$name.json")!!.readText()).jsonObject

private object Golden

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

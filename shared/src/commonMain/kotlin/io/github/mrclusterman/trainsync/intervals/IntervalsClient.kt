package io.github.mrclusterman.trainsync.intervals

import io.github.mrclusterman.trainsync.engine.WorkoutEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

/** Error de intervals.icu con su código HTTP (401 = clave o ID de atleta incorrectos). */
class IntervalsException(val status: Int, message: String) : Exception(message)

/**
 * Cliente de la API de intervals.icu, desde el propio móvil con la clave del usuario (docs/VISION.md §7).
 * `engine` solo para tests (MockEngine); en la app, el motor HTTP de cada plataforma.
 */
class IntervalsClient(private val athleteId: String, apiKey: String, engine: HttpClientEngine? = null) {
    private val http: HttpClient = (if (engine != null) HttpClient(engine) else HttpClient()).config {
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        defaultRequest {
            // Basic Auth: usuario literal "API_KEY" y contraseña la clave del atleta
            header(HttpHeaders.Authorization, "Basic " + Base64.encode("API_KEY:$apiKey".encodeToByteArray()))
        }
    }
    private val json = Json { encodeDefaults = true }
    private val athleteUrl = "$BASE_URL/athlete/$athleteId"

    private suspend fun read(response: HttpResponse): JsonElement {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw IntervalsException(response.status.value, text.take(200))
        return Json.parseToJsonElement(text)
    }

    private suspend fun list(path: String, vararg params: Pair<String, Any>): List<JsonObject> =
        read(http.get(athleteUrl + path) { params.forEach { (k, v) -> parameter(k, v.toString()) } })
            .jsonArray.map { it.jsonObject }

    suspend fun athlete(): JsonObject = read(http.get(athleteUrl)).jsonObject

    suspend fun wellness(oldest: LocalDate, newest: LocalDate) = list("/wellness", "oldest" to oldest, "newest" to newest)

    suspend fun activities(oldest: LocalDate, newest: LocalDate) = list("/activities", "oldest" to oldest, "newest" to newest)

    /** Objetivos = eventos de carrera A/B/C del calendario. */
    suspend fun races(oldest: LocalDate, newest: LocalDate) =
        list("/events", "oldest" to oldest, "newest" to newest, "category" to "RACE_A,RACE_B,RACE_C")

    suspend fun events(oldest: LocalDate, newest: LocalDate) = list("/events", "oldest" to oldest, "newest" to newest)

    /** Datos segundo a segundo de una actividad (endpoint fuera de /athlete/{id}). */
    suspend fun streams(activityId: String, types: String = "heartrate,velocity_smooth,grade_smooth"): Map<String, List<Double?>> =
        read(http.get("$BASE_URL/activity/$activityId/streams") { parameter("types", types) }).jsonArray
            .map { it.jsonObject }
            .associate { s -> s["type"].str()!! to ((s["data"] as? JsonArray)?.map { it.num() } ?: emptyList()) }

    suspend fun deleteEvent(eventId: Long) {
        read(http.delete("$athleteUrl/events/$eventId"))
    }

    /** Crea o actualiza (por external_id) entrenamientos del calendario; intervals.icu los pasa al reloj. */
    suspend fun upsertWorkouts(events: List<WorkoutEvent>): List<JsonObject> = read(http.post("$athleteUrl/events/bulk") {
        parameter("upsert", "true")
        parameter("upsertOnUid", "false")
        parameter("updatePlanApplied", "false")
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(events))
    }).jsonArray.map { it.jsonObject }

    /** Cambia el título de una actividad (p. ej. "Carrera · Tempo (Z3)" en vez del nombre automático del reloj). */
    suspend fun renameActivity(activityId: String, name: String) {
        read(http.put("$BASE_URL/activity/$activityId") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(mapOf("name" to name)))
        })
    }

    fun close() = http.close()

    companion object {
        const val BASE_URL = "https://intervals.icu/api/v1"
    }
}

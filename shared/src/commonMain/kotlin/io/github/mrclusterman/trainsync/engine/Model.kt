package io.github.mrclusterman.trainsync.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Bloque del catálogo (MOTOR §4). `minutes = null`: absorbe la diferencia hasta la duración elegida. */
data class CatalogBlock(
    val block: String,
    val minutes: Double?,
    val zone: String,
    val reps: Int = 1,
    val rest: Double? = null,
    val detail: String? = null,
    val duty: Double? = null,  // fracción de trabajo de un intermitente (30/15 = 0,67), para no sobreestimar el TSS
)

data class CatalogEntry(
    val id: String,
    val sport: String,
    val category: String,
    val phases: List<String>,
    val duration: Pair<Int, Int>,
    val blocks: List<CatalogBlock>,
    val events: List<String> = emptyList(),
)

internal fun B(block: String, minutes: Double?, zone: String, reps: Int = 1, rest: Double? = null,
               detail: String? = null, duty: Double? = null) = CatalogBlock(block, minutes, zone, reps, rest, detail, duty)

internal fun S(id: String, sport: String, category: String, phases: List<String>, duration: Pair<Int, Int>,
               blocks: List<CatalogBlock>, events: List<String> = emptyList()) =
    CatalogEntry(id, sport, category, phases, duration, blocks, events)

@Serializable
data class Block(
    val block: String,
    var reps: Int,
    var minutes: Double,
    val zone: String,
    @SerialName("rest_minutes") val restMinutes: Double? = null,
    @SerialName("rest_zone") val restZone: String? = null,
    val detail: String? = null,
    val duty: Double? = null,
    var rpe: String? = null,
    var guide: String? = null,
    var meters: Int? = null,  // natación con CSS: la distancia de cada serie (múltiplo de 25 m), que es como se nada
)

/** Sesión de resistencia. `equivalentMin` y `alternatives` solo en los planes de salud. */
@Serializable
data class Session(
    val id: String,
    val sport: String,
    val type: String,
    val name: String,
    val goal: String,
    @SerialName("duration_min") val durationMin: Int,
    val tss: Int,
    val blocks: List<Block>,
    val targets: Map<String, String>,
    @SerialName("hr_cap") val hrCap: Int? = null,
    @SerialName("if_bad") val ifBad: String,
    @SerialName("if_good") val ifGood: String,
    @SerialName("equivalent_min") var equivalentMin: Int? = null,
    var alternatives: List<Session>? = null,
)

/** Fuerza del modo carrera (MOTOR §5). */
@Serializable
data class RaceStrength(
    val sport: String = "strength",
    val type: String = "strength",
    val name: String,
    @SerialName("duration_min") val durationMin: Int,
    val dose: String,
    @SerialName("rpe_planned") val rpePlanned: Int,
    val tss: Int,
    val exercises: List<String>,
    val order: String,
)

@Serializable
data class Day(
    val date: String,
    val weekday: String,
    val phase: String,
    @SerialName("recovery_week") val recoveryWeek: Boolean,
    val reasons: MutableList<String>,
    var main: Session? = null,
    val alternatives: MutableList<Session> = mutableListOf(),
    var strength: RaceStrength? = null,
    /** Motivos del ajuste por el estado del día (fatiga, sueño, HRV): la frase de "Hoy" sale de aquí. */
    @Transient val adjustments: List<String> = emptyList(),
)

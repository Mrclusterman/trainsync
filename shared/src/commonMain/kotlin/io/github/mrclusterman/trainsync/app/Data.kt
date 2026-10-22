package io.github.mrclusterman.trainsync.app

// Lo que la app guarda en el propio móvil (docs/VISION.md §7): sin servidor ni cuentas. Los datos de salud no salen del
// dispositivo; la clave de intervals.icu va aparte, en el almacén seguro del sistema (Keystore o Llavero).

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Almacén de texto clave → valor de cada plataforma (SharedPreferences en Android, UserDefaults en iOS). */
interface KeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String?)
}

/** Qué quiere conseguir el usuario. El objetivo vive fuera del perfil y siempre se puede cambiar (VISION §5). */
enum class GoalKind { RACE, FITNESS, STRENGTH, HEALTH }

@Serializable
data class RaceInfo(
    val name: String,
    val sport: String,  // running / cycling / swimming_pool / swimming_open / triathlon / duathlon
    val date: String,  // AAAA-MM-DD
    val priority: String = "A",
    val distanceKm: Double? = null,
    val trail: Boolean = false,
)

@Serializable
data class UserGoal(val kind: GoalKind, val race: RaceInfo? = null, val since: String)

@Serializable
data class UserProfile(
    val birthYear: Int? = null,
    val level: String = "beginner",  // beginner / intermediate / advanced
    val strength: String = "bodyweight",  // gym / bodyweight / none
    val cardio: List<String> = listOf("walking"),  // walking / running / cycling / swimming_pool
    val days: Map<String, Int> = mapOf("MON" to 45, "WED" to 45, "SAT" to 60),  // día → minutos disponibles
    val renameActivities: Boolean = true,  // poner a las actividades de intervals.icu un título que diga lo que fueron
)

@Serializable
data class ManualDone(val date: String, val sport: String)

@Serializable
data class WeekChange(val changes: Map<String, Int>, val since: String)

@Serializable
data class Readiness(val sleep: Int, val energy: Int, val soreness: Int)

/** Copia local de lo descargado de intervals.icu: Progreso y el plan funcionan sin conexión. */
@Serializable
data class IntervalsCache(
    val athlete: JsonObject,
    val wellness: List<JsonObject>,
    val activities: List<JsonObject>,
    val syncedAt: String,
)

/** Versión del formato guardado: al cambiarlo, `migrate` adapta los datos antiguos (sin servidor no hay migraciones). */
const val SCHEMA = 2

@Serializable
data class AppData(
    val schema: Int = SCHEMA,
    val profile: UserProfile? = null,
    val goal: UserGoal? = null,
    val blockStart: String? = null,  // inicio del primer bloque de 4 semanas (lunes)
    val manualDone: List<ManualDone> = emptyList(),
    val weekChanges: Map<String, WeekChange> = emptyMap(),  // lunes → cambios de esa semana
    val readiness: Map<String, Readiness> = emptyMap(),  // fecha → respuestas del día
    val choices: Map<String, Map<String, String>> = emptyMap(),  // fecha → deporte planificado → deporte elegido
    val renamed: Set<String> = emptySet(),  // actividades ya renombradas en intervals.icu
    val cache: IntervalsCache? = null,
    val intervalsAthlete: String? = null,  // ID del atleta (no es secreto); la clave va en el almacén seguro
    val health: HealthSnapshot? = null,  // Health Connect o Apple Salud, leídos en el propio móvil
)

/** Adapta datos guardados con un formato anterior. v1 → v2: añade los datos de salud del móvil (vacíos). */
fun migrate(data: AppData): AppData = when {
    data.schema >= SCHEMA -> data
    else -> data.copy(schema = SCHEMA)
}

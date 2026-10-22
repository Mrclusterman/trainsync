package io.github.mrclusterman.trainsync.app

// Controlador común de las dos apps: guarda en el móvil, sincroniza con intervals.icu y expone cada apartado.

import io.github.mrclusterman.trainsync.engine.Profile
import io.github.mrclusterman.trainsync.engine.WorkoutEvent
import io.github.mrclusterman.trainsync.engine.plusDays
import io.github.mrclusterman.trainsync.engine.strengthEvent
import io.github.mrclusterman.trainsync.engine.workoutEvent
import io.github.mrclusterman.trainsync.intervals.IntervalsClient
import io.github.mrclusterman.trainsync.intervals.IntervalsException
import io.github.mrclusterman.trainsync.intervals.str
import io.ktor.client.engine.HttpClientEngine
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

private const val DATA_KEY = "trainsync.data"
private const val API_KEY = "intervals.apiKey"

/**
 * store: datos de la app; secrets: almacén seguro del sistema (solo la clave de intervals.icu).
 * today: la fecha de hoy (inyectable para los tests); httpEngine: solo para tests.
 */
class TrainSyncApp(
    private val store: KeyValueStore,
    private val secrets: KeyValueStore,
    private val today: () -> LocalDate,
    private val httpEngine: HttpClientEngine? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var data: AppData = store.read(DATA_KEY)?.let { runCatching { json.decodeFromString<AppData>(it) }.getOrNull() } ?: AppData()
        private set

    private fun save(new: AppData) {
        data = new
        store.write(DATA_KEY, json.encodeToString(new))
    }

    // ------------------------------------------------------------ objetivo y perfil

    /** Primera vez: falta el perfil o el objetivo. */
    val needsOnboarding: Boolean get() = data.profile == null || data.goal == null

    fun saveProfile(profile: UserProfile) = save(data.copy(profile = profile))

    /** Nuevo objetivo: los planes sin fecha empiezan un bloque nuevo este lunes. */
    fun setGoal(kind: GoalKind, race: RaceInfo? = null) {
        require(kind != GoalKind.RACE || race != null) { "Una carrera necesita nombre, deporte y fecha" }
        val now = today()
        save(data.copy(goal = UserGoal(kind, race, now.toString()), blockStart = mondayOf(now).toString()))
    }

    /** Abandonar el objetivo: la app vuelve a preguntar qué quieres. */
    fun abandonGoal() = save(data.copy(goal = null))

    // ------------------------------------------------------------ apartados

    fun todayScreen(): TodayScreen = buildToday(data, today())
    fun month(year: Int, month: Int): MonthScreen = buildMonth(data, today(), year, month)
    fun progress(): ProgressScreen = buildProgress(data, today())
    fun day(date: String): DayView = Planner(data, today()).day(LocalDate.parse(date))

    // ------------------------------------------------------------ acciones del día

    fun markDone(date: String, sport: String) {
        require(date <= today().toString()) { "No se puede marcar como hecha una sesión futura" }
        if (data.manualDone.none { it.date == date && it.sport == sport }) {
            save(data.copy(manualDone = data.manualDone + ManualDone(date, sport)))
        }
    }

    fun unmarkDone(date: String, sport: String) =
        save(data.copy(manualDone = data.manualDone.filterNot { it.date == date && it.sport == sport }))

    /** La alternativa elegida pasa a ser la sesión del día (en la app y, al enviarla, en el reloj). */
    fun chooseAlternative(date: String, plannedSport: String, chosenSport: String) {
        val day = (data.choices[date] ?: emptyMap()) + (plannedSport to chosenSport)
        save(data.copy(choices = data.choices + (date to day)))
    }

    fun clearChoice(date: String) = save(data.copy(choices = data.choices - date))

    /** "Hoy no puedo" (0) o "puedo entrenar este día" (minutos): la semana se recalcula desde hoy. */
    fun changeDay(date: String, minutes: Int) {
        val now = today()
        val target = LocalDate.parse(date)
        val monday = mondayOf(now)
        require(target >= now && target < monday.plusDays(7)) { "Solo se pueden cambiar los días que quedan de esta semana" }
        val key = monday.toString()
        val weekday = Planner.weekdayOf(target)
        val changes = (data.weekChanges[key]?.changes ?: emptyMap()) + (weekday to minutes)
        save(data.copy(weekChanges = data.weekChanges + (key to WeekChange(changes, now.toString()))))
    }

    fun resetWeek() = save(data.copy(weekChanges = data.weekChanges - mondayOf(today()).toString()))

    /** Sin reloj: sueño, energía y agujetas de 1 (mal) a 5 (bien) ajustan la sesión de hoy. */
    fun answerReadiness(sleep: Int, energy: Int, soreness: Int) {
        require(listOf(sleep, energy, soreness).all { it in 1..5 })
        save(data.copy(readiness = data.readiness + (today().toString() to Readiness(sleep, energy, soreness))))
    }

    // ------------------------------------------------------------ intervals.icu

    val linkedAthlete: String? get() = data.intervalsAthlete?.takeIf { secrets.read(API_KEY) != null }

    private fun client(): IntervalsClient? {
        val athlete = data.intervalsAthlete ?: return null
        val key = secrets.read(API_KEY) ?: return null
        return IntervalsClient(athlete, key, httpEngine)
    }

    /** Vincula la cuenta una vez: comprueba la clave contra intervals.icu antes de guardarla. */
    suspend fun linkIntervals(athleteId: String, apiKey: String) {
        val id = athleteId.trim()
        val key = apiKey.trim()
        val probe = IntervalsClient(id, key, httpEngine)
        try {
            probe.athlete()
        } catch (e: IntervalsException) {
            throw IllegalArgumentException(
                if (e.status == 401 || e.status == 403) "intervals.icu no acepta ese ID de atleta o esa clave" else "intervals.icu respondió ${e.status}")
        } finally {
            probe.close()
        }
        secrets.write(API_KEY, key)
        save(data.copy(intervalsAthlete = id))
        sync()
    }

    fun unlinkIntervals() {
        secrets.write(API_KEY, null)
        save(data.copy(intervalsAthlete = null, cache = null))
    }

    /** Descarga lo necesario y lo guarda en el móvil; renombra las actividades recientes si el usuario lo quiere. */
    suspend fun sync() {
        val c = client() ?: return
        try {
            val now = today()
            val athlete = c.athlete()
            val wellness = c.wellness(now.plusDays(-365), now)
            val activities = c.activities(now.plusDays(-365), now)
            var renamed = data.renamed
            if (data.profile?.renameActivities != false) {
                for (a in activities.filter { it["start_date_local"].str().orEmpty() >= now.plusDays(-14).toString() }) {
                    val id = a["id"].str() ?: continue
                    val title = activityTitle(a) ?: continue
                    if (id in renamed || a["name"].str() == title) continue
                    runCatching { c.renameActivity(id, title) }.onSuccess { renamed = renamed + id }
                }
            }
            val fixed = activities.map { a ->
                val id = a["id"].str()
                val title = activityTitle(a)
                if (id != null && id in renamed && title != null) kotlinx.serialization.json.JsonObject(
                    a + ("name" to kotlinx.serialization.json.JsonPrimitive(title))) else a
            }
            save(data.copy(cache = IntervalsCache(athlete, wellness, fixed, now.toString()), renamed = renamed))
        } finally {
            c.close()
        }
    }

    /** Envía al reloj (vía intervals.icu) las sesiones de hoy o solo la del deporte indicado. */
    suspend fun sendToday(sport: String? = null): List<String> {
        val c = client() ?: throw IllegalStateException("Vincula primero tu cuenta de intervals.icu")
        try {
            val planner = Planner(data, today())
            val day = planner.day(today())
            val items = day.items.filter { sport == null || it.sport == sport }
            check(items.isNotEmpty()) { "Hoy no hay sesiones que enviar" }
            val physio = planner.physiology ?: Profile(activeSports = listOf("running"))
            val events: List<WorkoutEvent> = items.map { item ->
                when {
                    item.session != null -> workoutEvent(item.session, day.date, physio, key = item.slot)
                    item.healthStrength != null -> strengthEvent(item.healthStrength, day.date)
                    else -> WorkoutEvent(startDateLocal = "${day.date}T00:00:00", type = "WeightTraining",
                        name = "TrainSync · Fuerza", movingTime = item.durationMin * 60,
                        description = (listOf(item.hint, "") + item.exercises.map { "• $it" } + listOfNotNull(item.notes))
                            .joinToString("\n"),
                        externalId = "trainsync-${day.date}-strength")
                }
            }
            c.upsertWorkouts(events)
            return events.map { it.name.removePrefix("TrainSync · ") }
        } finally {
            c.close()
        }
    }

    // ------------------------------------------------------------ tus datos

    /** Todo lo guardado, en JSON (portabilidad, RGPD art. 20). La clave de intervals.icu no se incluye. */
    fun exportJson(): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(data)

    /** Borra todo del móvil, clave incluida. */
    fun deleteAll() {
        secrets.write(API_KEY, null)
        store.write(DATA_KEY, null)
        data = AppData()
    }
}

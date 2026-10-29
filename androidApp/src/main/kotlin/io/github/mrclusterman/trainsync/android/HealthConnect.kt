package io.github.mrclusterman.trainsync.android

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.mrclusterman.trainsync.app.DailyHealth
import io.github.mrclusterman.trainsync.app.HealthSnapshot
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.Workout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/** Lee de Health Connect lo que usan el plan y Progreso (solo lectura) y lo entrega al núcleo. */
class HealthConnectReader(private val context: Context) {
    private val types: List<KClass<out Record>> = listOf(SleepSessionRecord::class, HeartRateVariabilityRmssdRecord::class,
        RestingHeartRateRecord::class, WeightRecord::class, StepsRecord::class, Vo2MaxRecord::class,
        ExerciseSessionRecord::class, HeartRateRecord::class, DistanceRecord::class)

    val permissions: Set<String> = types.map { HealthPermission.getReadPermission(it) }.toSet() +
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    /** Health Connect viene con Android 14 o posterior; en versiones anteriores hay que instalarlo. */
    val available: Boolean get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Set<String> = if (available) client.permissionController.getGrantedPermissions() else emptySet()

    private suspend fun <T : Record> read(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = token))
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }

    /** Lee los últimos `days` días y los guarda en la app. Lo que no tenga permiso se omite. */
    suspend fun sync(app: TrainSyncApp, days: Long = 365) {
        val granted = granted()
        val zone = ZoneId.systemDefault()
        val to = Instant.now()
        val from = to.minus(Duration.ofDays(days))
        fun date(i: Instant): String = i.atZone(zone).toLocalDate().toString()
        suspend fun <T : Record> maybe(type: KClass<T>): List<T> =
            if (HealthPermission.getReadPermission(type) in granted) read(type, from, to) else emptyList()

        val sleep = maybe(SleepSessionRecord::class).groupBy { date(it.endTime) }
            .mapValues { (_, s) -> s.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toDouble() }
        val hrv = maybe(HeartRateVariabilityRmssdRecord::class).groupBy { date(it.time) }
            .mapValues { (_, r) -> r.map { it.heartRateVariabilityMillis }.average() }
        val resting = maybe(RestingHeartRateRecord::class).groupBy { date(it.time) }
            .mapValues { (_, r) -> r.minOf { it.beatsPerMinute }.toDouble() }
        val weight = maybe(WeightRecord::class).groupBy { date(it.time) }
            .mapValues { (_, r) -> r.maxBy { it.time }.weight.inKilograms }
        val steps = maybe(StepsRecord::class).groupBy { date(it.endTime) }
            .mapValues { (_, r) -> r.sumOf { it.count }.toDouble() }
        val vo2 = maybe(Vo2MaxRecord::class).groupBy { date(it.time) }
            .mapValues { (_, r) -> r.maxBy { it.time }.vo2MillilitersPerMinuteKilogram }
        val dates = (sleep.keys + hrv.keys + resting.keys + weight.keys + steps.keys + vo2.keys).sorted()
        val dailies = dates.map { d ->
            DailyHealth(d, sleep[d], hrv[d], resting[d], weight[d], steps[d], vo2[d])
        }

        val heart = maybe(HeartRateRecord::class).flatMap { r -> r.samples.map { it.time to it.beatsPerMinute.toDouble() } }
            .sortedBy { it.first }
        val measuredMax = heart.maxOfOrNull { it.second }
        val bounds = app.zoneBounds(measuredMax)
        val local = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val distances = maybe(DistanceRecord::class)
        val workouts = maybe(ExerciseSessionRecord::class).mapNotNull { s ->
            val sport = SPORTS[s.exerciseType] ?: return@mapNotNull null
            val samples = heart.filter { it.first >= s.startTime && it.first <= s.endTime }
            // Tiempo en cada zona: cada pulso cuenta hasta el siguiente (como mucho 10 s, para no inventar huecos)
            val zones = DoubleArray(7)
            samples.zipWithNext { a, b ->
                val secs = minOf(Duration.between(a.first, b.first).seconds.toDouble(), 10.0)
                zones[bounds.indexOfFirst { a.second < it }.let { if (it == -1) 6 else it }] += secs
            }
            Workout(id = s.metadata.id, sport = sport, start = s.startTime.atZone(zone).format(local),
                minutes = Duration.between(s.startTime, s.endTime).seconds / 60.0,
                avgHr = samples.map { it.second }.takeIf { it.isNotEmpty() }?.average(),
                maxHr = samples.maxOfOrNull { it.second },
                zoneSeconds = if (zones.sum() > 0) zones.toList() else null,
                distanceMeters = distances.filter { it.startTime >= s.startTime && it.endTime <= s.endTime }
                    .sumOf { it.distance.inMeters }.takeIf { it > 0 })
        }
        app.importHealth(HealthSnapshot("Health Connect", dailies, workouts, LocalDate.now().toString()))
    }

    companion object {
        private val SPORTS = mapOf(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "running",
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to "running",
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "cycling",
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to "cycling",
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to "swimming_pool",
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to "swimming_pool",
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "walking",
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING to "walking",
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to "strength",
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING to "strength",
        )
    }
}

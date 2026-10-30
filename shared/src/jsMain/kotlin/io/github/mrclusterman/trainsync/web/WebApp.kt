@file:OptIn(ExperimentalJsExport::class)

package io.github.mrclusterman.trainsync.web

// Puente de la versión web: el mismo controlador que las apps, con los datos en el navegador. Las pantallas salen en
// JSON para que la interfaz (web/) reciba objetos planos de JavaScript.

import io.github.mrclusterman.trainsync.app.GoalKind
import io.github.mrclusterman.trainsync.app.KeyValueStore
import io.github.mrclusterman.trainsync.app.SPORT_LABEL
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.UserProfile
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.js.Date
import org.w3c.dom.Storage

private class WebStore(private val storage: Storage) : KeyValueStore {
    override fun read(key: String) = storage.getItem(key)
    override fun write(key: String, value: String?) = if (value == null) storage.removeItem(key) else storage.setItem(key, value)
}

// La fecha local del navegador; TimeZone.currentSystemDefault() en JS necesita la base de zonas horarias aparte
private fun browserToday() = Date().let { LocalDate(it.getFullYear(), it.getMonth() + 1, it.getDate()) }

@JsExport
class WebApp {
    // La clave de intervals.icu no se queda en el navegador: sessionStorage se borra al cerrar la pestaña. Cifrarla no
    // serviría (la llave estaría en el mismo navegador); contra scripts ajenos protege la CSP de web/vite.config.ts
    private val app = TrainSyncApp(WebStore(localStorage), WebStore(sessionStorage), ::browserToday)
    private val json = Json { encodeDefaults = true }

    val needsOnboarding: Boolean get() = app.needsOnboarding

    fun todayJson(): String = json.encodeToString(app.todayScreen())

    /** Datos de prueba: el perfil de los tests de la app y un objetivo de forma física. */
    fun startDemo() {
        app.saveProfile(UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
            cardio = listOf("running", "cycling"), days = mapOf("MON" to 60, "TUE" to 60, "THU" to 75, "SAT" to 90, "SUN" to 60)))
        app.setGoal(GoalKind.FITNESS)
    }

    fun sportLabel(sport: String): String = SPORT_LABEL[sport] ?: sport

    fun markDone(date: String, sport: String) = app.markDone(date, sport)
    fun unmarkDone(date: String, sport: String) = app.unmarkDone(date, sport)
}

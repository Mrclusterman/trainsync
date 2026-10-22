package io.github.mrclusterman.trainsync.app

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LiftsTest {
    private val line = "Sentadilla con barra: 3 × 8–12 repeticiones"

    @Test
    fun doubleProgression() {
        val app = TrainSyncApp(MemoryStore(), MemoryStore(), { LocalDate(2026, 10, 22) })
        assertNull(app.liftHint(line).suggestion)  // sin historial no se inventa un peso
        app.logLift("2026-10-20", line, 60.0, 10, 3)
        assertEquals("Mismo peso (60 kg) y busca 11 repeticiones", app.liftHint(line).suggestion)
        app.logLift("2026-10-22", line, 60.0, 12, 3)
        assertEquals("Llegaste al máximo: hoy prueba 63 kg y vuelve a 8 repeticiones", app.liftHint(line).suggestion)
        app.logLift("2026-10-22", line, 62.5, 8, 3)  // mismo día: sustituye
        assertEquals(2, app.data.lifts.size)
        assertEquals("Sentadilla con barra", app.data.lifts.last().exercise)
        assertFailsWith<IllegalArgumentException> { app.logLift("2026-10-23", line, 60.0, 8, 3) }
        assertNull(repRange("Plancha frontal y lateral: 2 × 20–45″ por lado"))
        app.logLift("2026-10-22", "Flexiones (inclinadas al empezar): 2 × 10–15 repeticiones", null, 15, 2)
        assertEquals("Llegaste al máximo: pasa a una variante más difícil y vuelve a 10 repeticiones",
            app.liftHint("Flexiones (inclinadas al empezar): 2 × 10–15 repeticiones").suggestion)
    }
}

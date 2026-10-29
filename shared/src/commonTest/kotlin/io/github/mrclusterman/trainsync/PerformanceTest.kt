package io.github.mrclusterman.trainsync

import io.github.mrclusterman.trainsync.engine.danielsTime
import io.github.mrclusterman.trainsync.engine.trainingStatus
import io.github.mrclusterman.trainsync.engine.vdot
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceTest {
    @Test
    fun danielsMatchesHisTables() {
        // Tablas VDOT de Daniels (Daniels' Running Formula): VDOT 50 → 5 km 19:57, maratón 3:10:49; VDOT 40 → 10 km 50:03
        assertTrue(abs(danielsTime(50.0, 5000.0) - (19 + 57 / 60.0)) < 0.3, "${danielsTime(50.0, 5000.0)}")
        assertTrue(abs(danielsTime(50.0, 42195.0) - (190 + 49 / 60.0)) < 2.0, "${danielsTime(50.0, 42195.0)}")
        assertTrue(abs(danielsTime(40.0, 10000.0) - (50 + 3 / 60.0)) < 0.5, "${danielsTime(40.0, 10000.0)}")
        assertTrue(abs(vdot(5000.0, danielsTime(45.0, 5000.0)) - 45.0) < 0.01)  // inversos
    }

    @Test
    fun statusFollowsLoad() {
        assertEquals("Sobrecarga", trainingStatus(40.0, 38.0, 65.0, null).first)
        assertEquals("Mejorando", trainingStatus(45.0, 40.0, 48.0, 1.0).first)
        assertEquals("Perdiendo forma", trainingStatus(30.0, 40.0, 15.0, null).first)
        assertEquals("Recuperando", trainingStatus(40.0, 39.0, 28.0, null).first)
        assertEquals("Manteniendo", trainingStatus(40.0, 39.5, 42.0, null).first)
        assertEquals("Sin entrenamiento reciente", trainingStatus(2.0, 3.0, 1.0, null).first)
    }
}

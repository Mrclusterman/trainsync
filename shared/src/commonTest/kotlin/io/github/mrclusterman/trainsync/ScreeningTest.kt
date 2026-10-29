package io.github.mrclusterman.trainsync

import io.github.mrclusterman.trainsync.engine.Screening
import io.github.mrclusterman.trainsync.engine.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Algoritmo de cribado del ACSM (Riebe et al. 2015/2017): las seis ramas más el paso de otras condiciones. */
class ScreeningTest {
    @Test
    fun acsmBranches() {
        val cases = listOf(
            Triple(Triple(false, false, false), "none", "light_moderate"),  // inactivo sano: empezar ligero-moderado
            Triple(Triple(false, true, false), "recommended", "light_moderate"),  // inactivo con enfermedad
            Triple(Triple(false, false, true), "recommended", "light_moderate"),  // inactivo con síntomas
            Triple(Triple(false, true, true), "recommended", "light_moderate"),
            Triple(Triple(true, false, false), "none", "vigorous"),  // activo sano
            Triple(Triple(true, true, false), "vigorous_only", "moderate"),  // activo con enfermedad sin síntomas
            Triple(Triple(true, false, true), "stop", "light_moderate"),  // activo con síntomas: parar y consultar
            Triple(Triple(true, true, true), "stop", "light_moderate"),
        )
        for ((answers, clearance, cap) in cases) {
            val r = screen(Screening(active = answers.first, knownDisease = answers.second, symptoms = answers.third))
            assertEquals(clearance to cap, r.clearance to r.intensityCap, answers.toString())
            assertTrue(r.message.isNotEmpty())
        }
        val other = screen(Screening(active = true, knownDisease = false, symptoms = false, otherConditions = true))
        assertEquals("recommended" to "light_moderate", other.clearance to other.intensityCap)
    }
}

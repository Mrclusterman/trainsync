package io.github.mrclusterman.trainsync.engine

// Cribado previo al ejercicio: algoritmo del ACSM (Riebe et al., Med Sci Sports Exerc 2015;47(11):2473–2479; ACSM's
// Guidelines for Exercise Testing and Prescription, 2017), en lugar del PAR-Q+, que no puede incorporarse a una app sin
// permiso escrito de la PAR-Q+ Collaboration. Las preguntas están redactadas con palabras propias. Definiciones (Riebe
// 2017, en la guía de cribado de Leeds Beckett University, 2020):
// - Activo: ejercicio planificado de al menos 30 min a intensidad moderada, ≥ 3 días/semana, en los últimos 3 meses.
// - Moderada: 40–59 % de la FC de reserva, 3–5,9 MET, RPE 12–13 (Borg 6–20). Vigorosa: ≥ 60 % FCR, ≥ 6 MET, RPE ≥ 14.

import kotlinx.serialization.Serializable

/** Signos o síntomas que sugieren enfermedad cardiovascular, metabólica o renal (lista del ACSM, redactada aquí). */
val SYMPTOMS = listOf(
    "Dolor o molestia en el pecho, el cuello, la mandíbula o los brazos que pueda deberse a falta de riego",
    "Falta de aire en reposo o con un esfuerzo ligero",
    "Mareos o desmayos",
    "Falta de aire al estar tumbado o que te despierta por la noche",
    "Tobillos hinchados",
    "Palpitaciones o latidos muy rápidos o irregulares que te resultan desagradables",
    "Dolor o calambres en las piernas al caminar distancias cortas",
    "Un soplo en el corazón conocido",
    "Cansancio o falta de aire fuera de lo normal con tus actividades de siempre",
)
val DISEASES = listOf("Enfermedad del corazón o de los vasos sanguíneos", "Diabetes tipo 1 o tipo 2", "Enfermedad del riñón")

@Serializable
data class Screening(
    val active: Boolean,  // cumple la definición de activo
    val knownDisease: Boolean,  // enfermedad cardiovascular, metabólica o renal diagnosticada
    val symptoms: Boolean,  // algún signo o síntoma de SYMPTOMS
    val otherConditions: Boolean = false,  // lesión, problema de huesos o articulaciones, embarazo
)

/** clearance: none / recommended / vigorous_only / stop. intensityCap: lo más alto que puede proponer el plan. */
data class ScreeningResult(val clearance: String, val intensityCap: String, val message: String)

fun screen(s: Screening): ScreeningResult = when {
    s.symptoms && s.active -> ScreeningResult("stop", "light_moderate",
        "Deja de hacer ejercicio y consulta a tu médico antes de volver: tienes algún síntoma que hay que valorar.")
    s.symptoms || (s.knownDisease && !s.active) -> ScreeningResult("recommended", "light_moderate",
        "Te recomendamos consultar a tu médico antes de empezar. Hasta entonces, el plan se queda en intensidad ligera a moderada.")
    s.knownDisease -> ScreeningResult("vigorous_only", "moderate",  // activo, sin síntomas
        "Puedes seguir con intensidad moderada. Antes de hacer ejercicio vigoroso, consulta a tu médico.")
    s.otherConditions -> ScreeningResult("recommended", "light_moderate",  // paso adicional de Leeds Beckett
        "Por lo que nos cuentas, consulta a un profesional sanitario antes de subir la intensidad.")
    s.active -> ScreeningResult("none", "vigorous", "Puedes seguir entrenando y progresar de forma gradual.")
    else -> ScreeningResult("none", "light_moderate", "Puedes empezar con intensidad ligera a moderada y progresar poco a poco.")
}

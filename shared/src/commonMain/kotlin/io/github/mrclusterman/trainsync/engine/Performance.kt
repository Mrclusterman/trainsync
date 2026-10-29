package io.github.mrclusterman.trainsync.engine

// Predicción de tiempos de carrera y estado de entrenamiento (Progreso, fase 2 de docs/VISION.md).

import kotlin.math.exp

/** Coste de correr a `v` m/min, en ml/kg/min (Daniels y Gilbert 1979). [Verificado] */
fun vo2AtSpeed(v: Double): Double = -4.60 + 0.182258 * v + 0.000104 * v * v

/** Fracción del VO2max que se sostiene durante `t` minutos (Daniels y Gilbert 1979). [Verificado] */
fun sustainableFraction(t: Double): Double = 0.8 + 0.1894393 * exp(-0.012778 * t) + 0.2989558 * exp(-0.1932605 * t)

/** Tiempo (min) en el que se corre `meters` con ese VO2max: el t con vo2AtSpeed(d/t) = VO2max × fracción(t), por bisección. */
fun danielsTime(vo2max: Double, meters: Double): Double {
    var lo = 1.0
    var hi = 1000.0
    repeat(60) {
        val t = (lo + hi) / 2
        if (vo2AtSpeed(meters / t) > vo2max * sustainableFraction(t)) lo = t else hi = t  // demasiado rápido: más tiempo
    }
    return (lo + hi) / 2
}

/** VO2max equivalente (VDOT) de una carrera: el inverso de danielsTime. */
fun vdot(meters: Double, minutes: Double): Double = vo2AtSpeed(meters / minutes) / sustainableFraction(minutes)

val RACE_DISTANCES = listOf("5 km" to 5000.0, "10 km" to 10000.0, "Media maratón" to 21097.5, "Maratón" to 42195.0)

/**
 * Estado de entrenamiento, como el de Garmin, con la carga de los últimos días (ATL) frente a la habitual (CTL) y la
 * forma física de hoy frente a la de hace 4 semanas. [Práctica: Gabbett 2016, relación aguda:crónica 0,8–1,3 y > 1,5;
 * umbrales del 5 % y 15 %: Inferencia]
 */
fun trainingStatus(ctlNow: Double, ctl28: Double?, atl: Double, vo2Trend: Double?): Pair<String, String> {
    if (ctlNow < 5 && atl < 5) return "Sin entrenamiento reciente" to "Hay muy poca carga en las últimas semanas para valorar tu forma."
    val ratio = atl / ctlNow.coerceAtLeast(1.0)
    val change = ctl28?.takeIf { it > 1 }?.let { ctlNow / it - 1 }
    val vo2 = when {
        vo2Trend == null -> ""
        vo2Trend >= 0.5 -> " Tu VO2max también sube."
        vo2Trend <= -0.5 -> " Tu VO2max ha bajado un poco."
        else -> ""
    }
    return when {
        ratio > 1.5 -> "Sobrecarga" to "Tu carga de esta semana es más de 1,5 veces la habitual: sube el riesgo de lesión. Unos días más suaves te ayudarán."
        change != null && change <= -0.15 && ratio < 0.8 -> "Perdiendo forma" to "Entrenas menos que hace un mes y tu forma física baja. Con constancia vuelve en pocas semanas.$vo2"
        ratio < 0.8 && atl < ctlNow -> "Recuperando" to "Esta semana cargas menos de lo habitual: estás descansando y absorbiendo lo entrenado.$vo2"
        change != null && change >= 0.05 && ratio <= 1.3 -> "Mejorando" to "Tu forma física sube y la carga va al ritmo adecuado.$vo2"
        else -> "Manteniendo" to "Tu carga es estable: mantienes la forma. Para mejorar, sube poco a poco.$vo2"
    }
}

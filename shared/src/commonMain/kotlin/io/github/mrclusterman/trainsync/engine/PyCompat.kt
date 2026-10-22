package io.github.mrclusterman.trainsync.engine

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sqrt

// Equivalentes de lo que el prototipo hacía con Python, para que el port dé exactamente los mismos planes.

/** `round(x)` de Python: al par en los empates (kotlin.math.round también). */
internal fun pyRound(x: Double): Int = round(x).toInt()

internal fun pyRound(x: Double, digits: Int): Double {
    var f = 1.0
    repeat(digits) { f *= 10 }
    return round(x * f) / f
}

/** División entera de Python (hacia abajo). */
internal fun floorDiv(a: Double, b: Double): Double = floor(a / b)

/** `f"{x:.Nf}"` de Python. */
internal fun fmt(x: Double, decimals: Int): String {
    val r = pyRound(abs(x), decimals)
    val sign = if (x < 0 && r != 0.0) "-" else ""
    if (decimals == 0) return sign + r.toLong().toString()
    var f = 1L
    repeat(decimals) { f *= 10 }
    val scaled = round(r * f).toLong()
    return sign + (scaled / f).toString() + "." + (scaled % f).toString().padStart(decimals, '0')
}

/** `f"{x:.0%}"` de Python. */
internal fun pct(x: Double): String = fmt(x * 100, 0) + "%"

internal fun twoDigits(n: Int): String = n.toString().padStart(2, '0')

internal fun mean(xs: List<Double>): Double = xs.sum() / xs.size

/** Desviación típica muestral (`statistics.stdev`). */
internal fun stdev(xs: List<Double>): Double {
    val m = mean(xs)
    return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
}

internal fun median(xs: List<Double>): Double {
    val s = xs.sorted()
    val n = s.size
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
}

/** `statistics.linear_regression`: (pendiente, ordenada) por mínimos cuadrados. */
internal fun linearRegression(x: List<Double>, y: List<Double>): Pair<Double, Double> {
    val mx = mean(x)
    val my = mean(y)
    val sxy = x.indices.sumOf { (x[it] - mx) * (y[it] - my) }
    val sxx = x.sumOf { (it - mx) * (it - mx) }
    val slope = sxy / sxx
    return slope to my - slope * mx
}

internal fun lnOrNull(v: Double?): Double? = if (v == null || v == 0.0) null else ln(v)

internal val WEEKDAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

internal fun LocalDate.weekday(): String = WEEKDAYS[dayOfWeek.isoDayNumber - 1]

internal fun LocalDate.plusDays(n: Int): LocalDate = plus(DatePeriod(days = n))

/** Semana ISO (`date.isocalendar().week`): la del jueves de esa semana. */
internal fun LocalDate.isoWeek(): Int {
    val thursday = plusDays(4 - dayOfWeek.isoDayNumber)
    return (thursday.dayOfYear - 1) / 7 + 1
}

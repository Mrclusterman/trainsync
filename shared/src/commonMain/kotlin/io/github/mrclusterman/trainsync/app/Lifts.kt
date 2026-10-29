package io.github.mrclusterman.trainsync.app

// Registro de fuerza y progresión doble (punto 8 del roast): sin apuntar pesos, "sube el peso cuando llegues al máximo
// de repeticiones" es una nota que nadie puede seguir.

/** Nombre del ejercicio sin la dosis: "Sentadilla con barra: 2 × 8–12 repeticiones" → "Sentadilla con barra". */
fun exerciseName(line: String): String = line.substringBefore(":").trim()

/** Rango de repeticiones de la línea del plan ("8–12"), o null si va por tiempo (planchas) o no lo indica. */
fun repRange(line: String): IntRange? {
    val m = Regex("""(\d+)[–-](\d+) repeticiones""").find(line) ?: return null
    return m.groupValues[1].toInt()..m.groupValues[2].toInt()
}

/** Lo último que apuntaste en ese ejercicio y qué toca hoy. */
data class LiftHint(val exercise: String, val last: LiftLog?, val suggestion: String?)

/**
 * Progresión doble: si la última vez llegaste al máximo del rango en todas las series, sube el peso un ~5 % (redondeado a
 * 0,5 kg; con tu peso, una variante más difícil); si no, el mismo peso buscando una repetición más. Si te quedaste por
 * debajo del rango, o llevas 3 sesiones con el mismo peso sin ganar repeticiones, baja un ~10 % y vuelve a subir.
 * [Práctica: ACSM 2009, progresión doble; el 5 % y el 10 % son Inferencia dentro del 2–10 % de la nota]
 */
fun liftHint(lifts: List<LiftLog>, line: String): LiftHint {
    val name = exerciseName(line)
    val history = lifts.filter { it.exercise == name }.sortedBy { it.date }
    val last = history.lastOrNull()
    val range = repRange(line)
    val recent = history.takeLast(3)
    val stalled = recent.size == 3 && last?.weightKg != null && recent.all { it.weightKg == last.weightKg } &&
        recent.last().reps <= recent.first().reps
    fun lighter(w: Double) = kg(maxOf(kotlin.math.round(w * 0.9 * 2) / 2, 0.5))
    val suggestion = when {
        last == null || range == null -> null
        last.reps >= range.last && last.weightKg != null -> {
            val next = kotlin.math.round(last.weightKg * 1.05 * 2) / 2
            "Llegaste al máximo: hoy prueba ${kg(maxOf(next, last.weightKg + 0.5))} y vuelve a ${range.first} repeticiones"
        }
        last.reps >= range.last -> "Llegaste al máximo: pasa a una variante más difícil y vuelve a ${range.first} repeticiones"
        last.reps < range.first && last.weightKg != null ->
            "Te quedaste por debajo de ${range.first}: hoy baja a ${lighter(last.weightKg)} y haz ${range.first} o más"
        last.reps < range.first -> "Te quedaste por debajo de ${range.first}: pasa a una variante más fácil"
        stalled -> "Tres sesiones sin ganar repeticiones con ${kg(last.weightKg!!)}: baja a ${lighter(last.weightKg)} " +
            "y vuelve a subir desde ${range.first}"
        last.weightKg != null -> "Mismo peso (${kg(last.weightKg)}) y busca ${last.reps + 1} repeticiones"
        else -> "Busca ${last.reps + 1} repeticiones"
    }
    return LiftHint(name, last, suggestion)
}

fun kg(v: Double): String = (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')) + " kg"

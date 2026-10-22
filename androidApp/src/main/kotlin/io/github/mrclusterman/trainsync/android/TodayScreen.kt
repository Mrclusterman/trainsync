package io.github.mrclusterman.trainsync.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mrclusterman.trainsync.app.DayView
import io.github.mrclusterman.trainsync.app.Reason
import io.github.mrclusterman.trainsync.app.SPORT_LABEL
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.kg
import io.github.mrclusterman.trainsync.app.repRange
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun TodayTab(app: TrainSyncApp, version: Int, act: Act, openGoal: () -> Unit) {
    val screen = remember(version) { app.todayScreen() }
    val day = screen.day
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(screen.dateLabel, color = Palette.secondary, fontSize = 13.sp)
        screen.goalLine?.let { Text(it, color = Palette.secondary, fontSize = 13.sp) }
        if (screen.goalFinished) {
            Module(accent = Palette.bio) {
                Text("Tu carrera ya pasó", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Secondary("¿Qué quieres ahora? Otra carrera, mantener la forma o descansar unos días.")
                Button(onClick = openGoal) { Text("Elegir objetivo") }
            }
        }
        // Tu cuerpo hoy en una frase: sale de la misma decisión que la sesión
        Module(accent = if (day.adjustments.isNotEmpty()) Palette.alert else Palette.accent) {
            Text(screen.headline, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            screen.headlineDetail.forEach { ReasonRow(it, if (day.adjustments.isNotEmpty()) Palette.alert else Palette.accent) }
        }
        if (screen.askReadiness) ReadinessCard(act)
        DayContent(app, day, screen.linked, act, today = true)
        Module {
            Text(screen.weekLine, fontSize = 15.sp)
            if (screen.meetsWho) Text("✓ Cumple la recomendación de la OMS para tu salud", color = Palette.bio, fontSize = 14.sp)
            screen.warnings.forEach { ReasonRow(Reason(it, null)) }
            screen.info.forEach { Secondary(it) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Las sesiones de un día con sus acciones: hecha, enviar al reloj, alternativas, hoy no puedo. */
@Composable
fun DayContent(app: TrainSyncApp, day: DayView, linked: Boolean, act: Act, today: Boolean) {
    // En Hoy la frase de arriba ya dice que descansas: aquí solo lo que se puede hacer
    if (day.items.isEmpty() && (!today || day.availableMin != null)) {
        Module {
            if (!today) Text("Descanso", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (!today) day.note?.let { Secondary(it) }
            if (!day.isPast && day.availableMin != null) {
                Button(onClick = { act.run { app.changeDay(day.date, day.availableMin!!) } }) { Text("Prefiero entrenar este día") }
            }
        }
    }
    for (item in day.items) {
        val done = item.slot in day.doneSports || item.sport in day.doneSports
        SessionCard(item, done) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!done && (day.isPast || today)) {
                    OutlinedButton(onClick = { act.run { app.markDone(day.date, item.sport) } }) { Text("Marcar como hecha") }
                }
                if (today && linked) {
                    Button(onClick = { act.suspend("Enviada a intervals.icu: llegará a tu reloj si tienes la sincronización activada") {
                        app.sendToday(item.sport)
                    } }) { Text("Enviar al reloj") }
                }
            }
            if (item.isStrength && (day.isPast || today)) LiftRows(app, day.date, item.exercises, act)
            if (item.chosen) TextButton(onClick = { act.run { app.clearChoice(day.date) } }) { Text("Volver a la planificada") }
            if (!done && !day.isPast && item.alternatives.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = !open }) { Text(if (item.chosen) "Otras opciones" else "¿Prefieres otro deporte?") }
                if (open) item.alternatives.forEach { alt ->
                    Row {
                        Text("${SPORT_LABEL[alt.sport]} · ${alt.title} · ${alt.durationMin}′", modifier = Modifier.weight(1f).padding(top = 12.dp), fontSize = 14.sp)
                        TextButton(onClick = { act.run { app.chooseAlternative(day.date, item.slot, alt.sport) } }) { Text("Haré esta") }
                    }
                }
            }
        }
    }
    day.matches.forEach { ReasonRow(Reason(it.text, null), if (it.counts) Palette.bio else Palette.alert) }
    day.reasons.forEach { ReasonRow(it, Palette.accent) }
    if (!day.isPast && day.items.isNotEmpty()) {
        TextButton(onClick = { act.run { app.changeDay(day.date, 0) } }) { Text("Hoy no puedo entrenar") }
    }
}

@Composable
private fun ReadinessCard(act: Act) {
    var sleep by remember { mutableIntStateOf(3) }
    var energy by remember { mutableIntStateOf(3) }
    var soreness by remember { mutableIntStateOf(3) }
    Module {
        Text("¿Cómo estás hoy?", fontWeight = FontWeight.Bold)
        Secondary("Sin reloj vinculado, tus respuestas ajustan la sesión de hoy: si dormiste mal o estás cargado, será más suave.")
        for ((label, value, set) in listOf(
            Triple("Sueño", sleep) { v: Int -> sleep = v },
            Triple("Energía", energy) { v: Int -> energy = v },
            Triple("Agujetas o molestias", soreness) { v: Int -> soreness = v },
        )) {
            Text(label, fontSize = 14.sp)
            ChipRow {
                (1..5).forEach { n ->
                    FilterChip(selected = value == n, onClick = { set(n) },
                        label = { Text(when (n) { 1 -> "1 mal"; 5 -> "5 bien"; else -> "$n" }) })
                }
            }
        }
        Button(onClick = { act.run { act.app.answerReadiness(sleep, energy, soreness) } }) { Text("Ajustar hoy") }
    }
}

/** Cada ejercicio de fuerza: lo apuntado la última vez, la sugerencia de hoy y apuntar peso y repeticiones. */
@Composable
fun LiftRows(app: TrainSyncApp, date: String, exercises: List<String>, act: Act) {
    exercises.filter { repRange(it) != null }.forEach { line ->
        val hint = app.liftHint(line)
        var open by remember { mutableStateOf(false) }
        var weight by remember { mutableStateOf(hint.last?.weightKg?.let { kg(it).removeSuffix(" kg") } ?: "") }
        var reps by remember { mutableStateOf("") }
        Column(Modifier.padding(top = 6.dp)) {
            Text(hint.exercise, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            hint.last?.let { l ->
                Secondary("La última vez: ${l.weightKg?.let { kg(it) } ?: "tu peso"} × ${l.reps} (${l.sets} series)")
            }
            hint.suggestion?.let { Text(it, fontSize = 13.sp, color = Palette.accent) }
            if (!open) TextButton(onClick = { open = true }) { Text("Apuntar", fontSize = 13.sp) }
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(weight, { weight = it }, label = { Text("kg") }, singleLine = true, modifier = Modifier.width(90.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(reps, { reps = it.filter(Char::isDigit) }, label = { Text("reps") }, singleLine = true,
                    modifier = Modifier.width(90.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                TextButton(onClick = {
                    act.run("Apuntado") {
                        val sets = Regex("""(\d+) ×""").find(line)?.groupValues?.get(1)?.toInt() ?: 1
                        app.logLift(date, line, weight.replace(',', '.').toDoubleOrNull(), reps.toIntOrNull() ?: 0, sets)
                    }
                    open = false
                }) { Text("Guardar") }
            }
        }
    }
}

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

package io.github.mrclusterman.trainsync.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mrclusterman.trainsync.app.GoalKind
import io.github.mrclusterman.trainsync.app.HEALTH_NOTICE
import io.github.mrclusterman.trainsync.app.MIN_AGE
import io.github.mrclusterman.trainsync.app.PRIVACY_NOTE
import io.github.mrclusterman.trainsync.app.RaceInfo
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.UserProfile
import io.github.mrclusterman.trainsync.app.ageAllowed
import io.github.mrclusterman.trainsync.app.systemToday
import io.github.mrclusterman.trainsync.engine.DISEASES
import io.github.mrclusterman.trainsync.engine.SYMPTOMS
import io.github.mrclusterman.trainsync.engine.Screening
import io.github.mrclusterman.trainsync.engine.screen
import kotlinx.datetime.LocalDate

private val GOALS = listOf(
    GoalKind.RACE to ("Preparar una carrera" to "Un plan con fecha hacia tu prueba: carrera, bici, natación, triatlón…"),
    GoalKind.FITNESS to ("Ponerme en forma" to "Más resistencia y fuerza para el día a día, sin fecha."),
    GoalKind.STRENGTH to ("Ganar fuerza y músculo" to "Fuerza primero, con algo de cardio para la salud."),
    GoalKind.HEALTH to ("Mejorar mi salud a largo plazo" to "Cumplir lo que recomienda la OMS, con constancia."),
)
private val RACE_SPORTS = listOf("running" to "Carrera a pie", "cycling" to "Bici", "swimming_pool" to "Natación",
    "triathlon" to "Triatlón", "duathlon" to "Duatlón")

/** Primera vez: bienvenida, objetivo (como Garmin), tus datos y el aviso de salud. Con perfil, solo el objetivo. */
@Composable
fun Onboarding(app: TrainSyncApp, act: Act) {
    val hasProfile = app.data.profile != null
    var step by remember { mutableIntStateOf(if (hasProfile) 1 else 0) }
    var goal by remember { mutableStateOf<GoalKind?>(null) }
    var race by remember { mutableStateOf(RaceInfo("", "running", "")) }
    var profile by remember { mutableStateOf(app.data.profile ?: UserProfile()) }
    var accepted by remember { mutableStateOf(false) }
    var screening by remember { mutableStateOf(app.data.profile?.screening ?: Screening(false, false, false)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(24.dp))
        when (step) {
            0 -> {
                Text("TrainSync", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("El sistema operativo de tu cuerpo", fontSize = 18.sp, color = Palette.accent)
                Secondary("Te dice qué hacer hoy y por qué, con tus datos y según la evidencia. Gratis, sin anuncios y de código abierto.")
                Secondary(PRIVACY_NOTE)
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Empezar") }
            }
            1 -> {
                Text("¿Qué quieres conseguir?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Secondary("Podrás cambiarlo o abandonarlo cuando quieras.")
                GOALS.forEach { (kind, texts) ->
                    Module(Modifier.clickable { goal = kind }, accent = if (goal == kind) Palette.accent else null) {
                        Text(texts.first, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Secondary(texts.second)
                    }
                }
                if (goal == GoalKind.RACE) RaceForm(race) { race = it }
                val raceOk = goal != GoalKind.RACE || (race.name.isNotBlank() && runCatching { LocalDate.parse(race.date) }
                    .getOrNull()?.let { it > systemToday() } == true)
                Button(enabled = goal != null && raceOk, modifier = Modifier.fillMaxWidth(), onClick = {
                    if (hasProfile) act.run { app.setGoal(goal!!, race.takeIf { goal == GoalKind.RACE }) } else step = 2
                }) { Text(if (hasProfile) "Guardar objetivo" else "Siguiente") }
            }
            2 -> {
                Text("Tu semana", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Secondary("Con esto el plan cabe en tu vida: solo los días y minutos que tienes.")
                ProfileForm(profile) { profile = it }
                val year = profile.birthYear
                val ageOk = year != null && ageAllowed(year, systemToday().year)
                if (year != null && !ageOk) Secondary("Hace falta tener al menos $MIN_AGE años.", Palette.alert)
                Button(enabled = ageOk && profile.days.values.any { it > 0 }, modifier = Modifier.fillMaxWidth(),
                    onClick = { step = 3 }) { Text("Siguiente") }
                TextButton(onClick = { step = 1 }) { Text("Atrás") }
            }
            else -> {
                Text("Antes de empezar", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Module(accent = Palette.alert) { Secondary(HEALTH_NOTICE, Palette.text) }
                ScreeningForm(screening) { screening = it }
                Row(Modifier.toggleable(accepted, role = Role.Checkbox) { accepted = it }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(accepted, null)
                    Text("Lo he leído y entreno bajo mi responsabilidad", fontSize = 14.sp)
                }
                Button(enabled = accepted, modifier = Modifier.fillMaxWidth(), onClick = {
                    act.run {
                        app.saveProfile(profile.copy(screening = screening))
                        app.setGoal(goal!!, race.takeIf { goal == GoalKind.RACE })
                    }
                }) { Text("Ver mi plan") }
                TextButton(onClick = { step = 2 }) { Text("Atrás") }
            }
        }
    }
}

/** Cuestionario de salud del ACSM: cuatro preguntas con palabras propias y lo que significa la respuesta. */
@Composable
fun ScreeningForm(value: Screening, onChange: (Screening) -> Unit) {
    Module {
        SectionTitle("Cuatro preguntas de salud")
        Secondary("Sirven para que el plan no te proponga más intensidad de la recomendable. Se quedan en este móvil.")
        CheckRow("En los últimos 3 meses he hecho ejercicio planificado al menos 3 días a la semana, 30 min o más a " +
            "intensidad moderada", value.active) { onChange(value.copy(active = it)) }
        CheckRow("Tengo diagnosticada alguna de estas: ${DISEASES.joinToString(", ") { it.lowercase() }}", value.knownDisease) {
            onChange(value.copy(knownDisease = it))
        }
        CheckRow("Noto alguno de estos síntomas: ${SYMPTOMS.joinToString("; ") { it.lowercase() }}", value.symptoms) {
            onChange(value.copy(symptoms = it))
        }
        CheckRow("Tengo una lesión, un problema de huesos o articulaciones, o estoy embarazada", value.otherConditions) {
            onChange(value.copy(otherConditions = it))
        }
        Secondary(screen(value).message, if (screen(value).clearance == "none") Palette.secondary else Palette.alert)
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null)
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun RaceForm(race: RaceInfo, onChange: (RaceInfo) -> Unit) {
    Module {
        OutlinedTextField(race.name, { onChange(race.copy(name = it)) }, label = { Text("Nombre de la carrera") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        ChipRow {
            RACE_SPORTS.forEach { (key, label) ->
                FilterChip(selected = race.sport == key, label = { Text(label) }, onClick = { onChange(race.copy(sport = key)) })
            }
        }
        OutlinedTextField(race.date, { onChange(race.copy(date = it.trim())) }, label = { Text("Fecha (AAAA-MM-DD)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(race.distanceKm?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "",
            { v -> onChange(race.copy(distanceKm = v.replace(',', '.').toDoubleOrNull())) },
            label = { Text("Distancia en km (opcional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if (race.sport == "running") Row(Modifier.toggleable(race.trail, role = Role.Checkbox) { onChange(race.copy(trail = it)) },
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(race.trail, null)
            Text("Es por montaña (trail)")
        }
        ChipRow {
            listOf("A" to "Principal", "B" to "Importante", "C" to "Preparatoria").forEach { (p, label) ->
                FilterChip(selected = race.priority == p, label = { Text(label) }, onClick = { onChange(race.copy(priority = p)) })
            }
        }
    }
}

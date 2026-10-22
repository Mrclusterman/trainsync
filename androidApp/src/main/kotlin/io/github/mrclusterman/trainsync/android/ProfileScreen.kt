package io.github.mrclusterman.trainsync.android

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mrclusterman.trainsync.app.HEALTH_NOTICE
import io.github.mrclusterman.trainsync.app.PRIVACY_NOTE
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.UserProfile
import io.github.mrclusterman.trainsync.app.WEEKDAY_LABEL
import io.github.mrclusterman.trainsync.app.goalLabel

private val CARDIO_OPTIONS = listOf("walking" to "Caminar", "running" to "Correr", "cycling" to "Bici", "swimming_pool" to "Nadar")
private val STRENGTH_OPTIONS = listOf("gym" to "Gimnasio", "bodyweight" to "Con tu peso", "none" to "Sin fuerza")
private val LEVEL_OPTIONS = listOf("beginner" to "Empiezo", "intermediate" to "Entreno a veces", "advanced" to "Entreno a menudo")
private val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** Datos para el plan: actividades, días y minutos, material y nivel. El objetivo no vive aquí (VISION §5). */
@Composable
fun ProfileForm(initial: UserProfile, onChange: (UserProfile) -> Unit) {
    var p by remember { mutableStateOf(initial) }
    fun set(new: UserProfile) { p = new; onChange(new) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Qué actividades haces")
        ChipRow {
            CARDIO_OPTIONS.forEach { (key, label) ->
                FilterChip(selected = key in p.cardio, label = { Text(label) }, onClick = {
                    val next = if (key in p.cardio) p.cardio - key else p.cardio + key
                    if (next.isNotEmpty()) set(p.copy(cardio = next))
                })
            }
        }
        SectionTitle("Fuerza")
        ChipRow {
            STRENGTH_OPTIONS.forEach { (key, label) ->
                FilterChip(selected = p.strength == key, label = { Text(label) }, onClick = { set(p.copy(strength = key)) })
            }
        }
        SectionTitle("Tu nivel")
        ChipRow {
            LEVEL_OPTIONS.forEach { (key, label) ->
                FilterChip(selected = p.level == key, label = { Text(label) }, onClick = { set(p.copy(level = key)) })
            }
        }
        SectionTitle("Qué días puedes entrenar y cuánto tiempo")
        DAYS.forEach { d ->
            val minutes = p.days[d] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = minutes > 0, label = { Text(WEEKDAY_LABEL[d]!!) }, onClick = {
                    set(p.copy(days = p.days + (d to if (minutes > 0) 0 else 45)))
                }, modifier = Modifier.width(120.dp))
                if (minutes > 0) {
                    TextButton(onClick = { set(p.copy(days = p.days + (d to maxOf(15, minutes - 15)))) }) { Text("−") }
                    Text("$minutes min", fontFamily = Mono)
                    TextButton(onClick = { set(p.copy(days = p.days + (d to minOf(240, minutes + 15)))) }) { Text("+") }
                }
            }
        }
        SectionTitle("Año de nacimiento")
        OutlinedTextField(value = p.birthYear?.toString() ?: "", singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = { v -> set(p.copy(birthYear = v.filter { it.isDigit() }.take(4).toIntOrNull())) })
    }
}

@Composable
fun ProfileTab(app: TrainSyncApp, version: Int, act: Act, changeGoal: () -> Unit) {
    val context = LocalContext.current
    var draft by remember(version) { mutableStateOf(app.data.profile ?: UserProfile()) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Module {
            SectionTitle("Tu objetivo")
            Text(app.data.goal?.let { goalLabel(it) } ?: "Sin objetivo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Secondary("Puedes cambiarlo o abandonarlo cuando quieras; el plan se rehace al momento.")
            OutlinedButton(onClick = changeGoal) { Text("Cambiar objetivo") }
        }
        Module {
            ProfileForm(draft) { draft = it }
            Button(onClick = { act.run("Perfil guardado") { app.saveProfile(draft) } }) { Text("Guardar") }
        }
        IntervalsCard(app, act)
        Module {
            SectionTitle("Tus datos")
            Secondary(PRIVACY_NOTE)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Poner a tus actividades de intervals.icu un título que diga lo que fueron", modifier = Modifier.weight(1f), fontSize = 14.sp)
                Switch(checked = draft.renameActivities, onCheckedChange = {
                    draft = draft.copy(renameActivities = it)
                    act.run { app.saveProfile(draft) }
                })
            }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, app.exportJson())
                context.startActivity(Intent.createChooser(send, "Exportar mis datos"))
            }) { Text("Exportar todos mis datos (JSON)") }
            Button(onClick = { confirmDelete = true }, colors = ButtonDefaults.buttonColors(containerColor = Palette.danger)) {
                Text("Borrar todo de este móvil")
            }
        }
        Module {
            SectionTitle("Aviso de salud")
            Secondary(HEALTH_NOTICE)
        }
        Secondary("TrainSync es gratis y de código abierto (AGPL-3.0). Sin niveles de pago, sin anuncios y sin notificaciones comerciales.")
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar todo?") },
            text = { Text("Se borran tu perfil, tu objetivo, tu historial y la clave de intervals.icu de este móvil. No se puede deshacer. Tus datos en intervals.icu no se tocan.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; act.run { app.deleteAll() } }) { Text("Borrar", color = Palette.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun IntervalsCard(app: TrainSyncApp, act: Act) {
    var athlete by remember { mutableStateOf(app.data.intervalsAthlete ?: "") }
    var key by remember { mutableStateOf("") }
    Module {
        SectionTitle("Reloj (intervals.icu)")
        val linked = app.linkedAthlete
        if (linked != null) {
            Text("Vinculado: $linked", fontWeight = FontWeight.Bold)
            app.data.cache?.let { Secondary("Datos al día del ${it.syncedAt}") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { act.suspend("Datos actualizados") { app.sync() } }) { Text("Actualizar ahora") }
                OutlinedButton(onClick = { act.run { app.unlinkIntervals() } }) { Text("Desvincular") }
            }
        } else {
            Secondary("intervals.icu es gratis y recoge los datos de tu reloj (Garmin, Polar, Coros, Suunto, Wahoo…). " +
                "En intervals.icu: Settings → Developer Settings, copia tu Athlete ID y crea una API key.")
            OutlinedTextField(athlete, { athlete = it }, label = { Text("Athlete ID (p. ej. i123456)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { act.suspend("Cuenta vinculada") { app.linkIntervals(athlete, key); key = "" } },
                enabled = athlete.isNotBlank() && key.isNotBlank()) { Text("Vincular") }
        }
    }
}

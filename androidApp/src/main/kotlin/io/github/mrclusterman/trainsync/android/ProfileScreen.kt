package io.github.mrclusterman.trainsync.android

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.health.connect.client.PermissionController
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
import io.github.mrclusterman.trainsync.app.GoalKind
import io.github.mrclusterman.trainsync.app.RaceInfo
import io.github.mrclusterman.trainsync.app.dateLabel

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
            OtherRaces(app, act)
        }
        Module {
            ProfileForm(draft) { draft = it }
            Button(onClick = { act.run("Perfil guardado") { app.saveProfile(draft) } }) { Text("Guardar") }
        }
        ReminderCard(app, act, version)
        HealthCard(app, act, version)
        IntervalsCard(app, act, version)
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

/** Carreras preparatorias (B o C) antes de la principal: el plan afina unos días antes y recupera después. */
@Composable
private fun OtherRaces(app: TrainSyncApp, act: Act) {
    val goal = app.data.goal ?: return
    val main = goal.race?.takeIf { goal.kind == GoalKind.RACE } ?: return
    var adding by remember { mutableStateOf(false) }
    var extra by remember { mutableStateOf(RaceInfo("", main.sport, "", "C")) }
    SectionTitle("Otras carreras antes de ${main.name}")
    goal.otherRaces.forEach { r ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, fontWeight = FontWeight.Medium)
                Secondary("${dateLabel(r.date)} · ${if (r.priority == "B") "importante" else "de preparación"}")
            }
            TextButton(onClick = { act.run { app.removeRace(r.date) } }) { Text("Quitar") }
        }
    }
    if (adding) {
        RaceForm(extra) { extra = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { act.run("Carrera añadida") { app.addRace(extra); adding = false; extra = RaceInfo("", main.sport, "", "C") } }) { Text("Añadir") }
            TextButton(onClick = { adding = false }) { Text("Cancelar") }
        }
    } else {
        Secondary("El plan afina unos días antes de cada una y te deja recuperar después. Elige importante (B) o de preparación (C).")
        OutlinedButton(onClick = { adding = true }) { Text("Añadir otra carrera") }
    }
}

/** Recordatorio diario opcional: apagado por defecto, a la hora que elijas, solo con lo que toca ese día. */
@Composable
private fun ReminderCard(app: TrainSyncApp, act: Act, @Suppress("UNUSED_PARAMETER") version: Int) {
    val context = LocalContext.current
    fun set(hour: Int?) = act.run { app.setReminder(hour); scheduleReminder(context, hour) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) set(8) else act.run("Sin permiso para avisarte: actívalo en Ajustes → Notificaciones") {}
    }
    val hour = app.data.reminderHour
    Module {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Recordatorio diario")
                Secondary("Un aviso con la sesión del día. Los días de descanso o con todo hecho no avisa.")
            }
            Switch(checked = hour != null, onCheckedChange = { on ->
                when {
                    !on -> set(null)
                    Build.VERSION.SDK_INT >= 33 && !canNotify(context) -> request.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> set(8)
                }
            })
        }
        if (hour != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A las", fontSize = 14.sp)
            TextButton(onClick = { set((hour + 23) % 24) }) { Text("−") }
            Text("${hour.toString().padStart(2, '0')}:00", fontFamily = Mono)
            TextButton(onClick = { set((hour + 1) % 24) }) { Text("+") }
        }
    }
}

/** Health Connect: los datos del reloj y la báscula sin intervals.icu (solo lectura, en el propio móvil). */
@Composable
private fun HealthCard(app: TrainSyncApp, act: Act, @Suppress("UNUSED_PARAMETER") version: Int) {  // version: redibuja tras cada acción
    val context = LocalContext.current
    val reader = remember { HealthConnectReader(context) }
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.isNotEmpty()) act.suspend("Datos de Health Connect leídos") { reader.sync(app) }
    }
    Module {
        SectionTitle("Datos de salud del móvil (Health Connect)")
        val health = app.data.health
        if (health != null) {
            Text("Conectado: ${health.days.size} días y ${health.workouts.size} entrenamientos", fontWeight = FontWeight.Bold)
            Secondary("Datos al día del ${health.syncedAt}. Los permisos se cambian en Ajustes → Health Connect.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { act.suspend("Datos actualizados") { reader.sync(app) } }) { Text("Actualizar ahora") }
                OutlinedButton(onClick = { act.run { app.clearHealth() } }) { Text("Olvidar") }
            }
        } else if (reader.available) {
            Secondary("Lee de Health Connect tu sueño, tu VFC, tu pulso en reposo, tu peso, tus pasos y tus entrenamientos " +
                "de cualquier reloj o app que escriba allí. Solo lectura y solo en este móvil.")
            Button(onClick = { request.launch(reader.permissions) }) { Text("Conectar Health Connect") }
        } else {
            Secondary("Health Connect no está en este móvil. Viene con Android 14 o posterior; en versiones anteriores " +
                "se instala desde Google Play.")
        }
    }
}

@Composable
private fun IntervalsCard(app: TrainSyncApp, act: Act, @Suppress("UNUSED_PARAMETER") version: Int) {
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

package io.github.mrclusterman.trainsync.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.systemToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Iconos de la barra de estado claros u oscuros según el modo del sistema
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        val app = TrainSyncApp(PrefsStore(this), KeystoreStore(this), { systemToday() })
        setContent { TrainSyncTheme { Root(app) } }
    }
}

/** Ejecuta una acción, refresca las pantallas y enseña el error si lo hay. */
class Act(val app: TrainSyncApp, private val scope: CoroutineScope, private val snackbar: SnackbarHostState,
          private val refresh: () -> Unit) {
    private fun show(text: String?) = scope.launch { snackbar.showSnackbar(text ?: "No se pudo completar") }

    fun run(success: String? = null, block: () -> Unit) {
        try {
            block()
            success?.let { show(it) }
        } catch (e: Exception) {
            show(e.message)
        }
        refresh()
    }

    fun run(block: () -> Unit) = run(null, block)

    fun suspend(success: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                success?.let { show(it) }
            } catch (e: Exception) {
                show(e.message)
            }
            refresh()
        }
    }
}

private val TABS = listOf("Hoy" to R.drawable.ic_tab_today, "Calendario" to R.drawable.ic_tab_calendar,
    "Progreso" to R.drawable.ic_tab_progress, "Perfil" to R.drawable.ic_tab_profile)

@Composable
fun Root(app: TrainSyncApp) {
    var version by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val act = remember { Act(app, scope, snackbar) { version += 1 } }
    val context = LocalContext.current

    // Al abrir, datos frescos de intervals.icu si está vinculado; sin red, la copia guardada
    LaunchedEffect(Unit) {
        scheduleReminder(context, app.data.reminderHour)  // por si el sistema quitó la alarma (actualización, forzar cierre)
        if (app.linkedAthlete != null) runCatching { app.sync() }
        // Health Connect, si ya dio permiso: datos frescos sin pulsar nada
        if (app.data.health != null) runCatching { HealthConnectReader(context).sync(app) }
        version += 1
    }

    val onboarding = version >= 0 && app.needsOnboarding  // version: se recalcula tras cada acción
    Scaffold(
        containerColor = Palette.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!onboarding) NavigationBar(containerColor = Palette.surface) {
                TABS.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(painterResource(icon), contentDescription = null) },
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Palette.chip,
                            selectedTextColor = Palette.ok, selectedIconColor = Palette.ok,
                            unselectedTextColor = Palette.muted, unselectedIconColor = Palette.muted))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (onboarding) {
                Onboarding(app, act)
            } else when (tab) {
                0 -> TodayTab(app, version, act) { act.run { app.abandonGoal() } }
                1 -> CalendarTab(app, version, act)
                2 -> ProgressTab(app, version) { tab = 3 }
                else -> ProfileTab(app, version, act) { act.run { app.abandonGoal() } }
            }
        }
    }
}


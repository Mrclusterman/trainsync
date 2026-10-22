package io.github.mrclusterman.trainsync.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mrclusterman.trainsync.app.CalendarDay
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.dateLabel
import io.github.mrclusterman.trainsync.app.WEEKDAY_SHORT
import io.github.mrclusterman.trainsync.app.systemToday
import kotlinx.datetime.number

/** Calendario mensual: el pasado muestra lo que hiciste de verdad y el futuro, el plan (punto 4 de la revisión). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTab(app: TrainSyncApp, version: Int, act: Act) {
    val now = remember { systemToday() }
    var year by remember { mutableIntStateOf(now.year) }
    var month by remember { mutableIntStateOf(now.month.number) }
    val screen = remember(version, year, month) { app.month(year, month) }
    var selected by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (month == 1) { month = 12; year -= 1 } else month -= 1 }) { Text("‹", fontSize = 22.sp) }
            Text(screen.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (month == 12) { month = 1; year += 1 } else month += 1 }) { Text("›", fontSize = 22.sp) }
        }
        Row {
            listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEach {
                Text(WEEKDAY_SHORT[it]!!, color = Palette.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
            }
        }
        for (week in screen.weeks) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d -> DayCell(d, Modifier.weight(1f)) { selected = d.date } }
            }
        }
        Secondary("Punto de color: sesión planificada. ✓: hecha. Toca un día para ver el detalle.")
        Spacer(Modifier.height(24.dp))
    }

    selected?.let { date ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = Palette.surface1) {
            val day = remember(version, date) { app.day(date) }
            val cell = screen.weeks.flatten().firstOrNull { it.date == date }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(dateLabel(date), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                cell?.done?.forEach { a ->
                    Module(accent = Palette.bio) {
                        Text(a.title, fontWeight = FontWeight.Bold)
                        Secondary(listOfNotNull("${a.minutes} min", a.start?.let { "a las $it" }).joinToString(" "))
                    }
                }
                DayContent(app, day, app.linkedAthlete != null, act, today = day.isToday)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DayCell(d: CalendarDay, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.aspectRatio(0.8f).clip(shape)
            .background(if (d.inMonth) Palette.surface1 else Palette.bg)
            .then(if (d.isToday) Modifier.border(1.dp, Palette.accent, shape) else Modifier)
            .clickable(onClick = onClick).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("${d.dayOfMonth}", fontFamily = Mono, fontSize = 12.sp, color = if (d.inMonth) Palette.text else Palette.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            d.planned.take(3).forEach { SportDot(it.sport, 7) }
        }
        val done = d.done.map { it.sport } + d.manual
        if (done.isNotEmpty()) Text("✓" + if (done.size > 1) done.size.toString() else "", color = Palette.bio, fontSize = 11.sp)
    }
}


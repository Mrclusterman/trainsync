import SwiftUI
import Shared

struct TodayView: View {
    @EnvironmentObject var model: AppModel
    let openGoal: () -> Void

    var body: some View {
        let _ = model.version
        let screen = model.core.todayScreen()
        let day = screen.day
        let adjusted = !day.adjustments.isEmpty
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Secondary(screen.dateLabel)
                if let goal = screen.goalLine { Secondary(goal) }
                if screen.goalFinished {
                    Module(accent: Palette.bio) {
                        Text("Tu carrera ya pasó").font(.title3).bold()
                        Secondary("¿Qué quieres ahora? Otra carrera, mantener la forma o descansar unos días.")
                        Button("Elegir objetivo", action: openGoal).buttonStyle(.borderedProminent)
                    }
                }
                // Tu cuerpo hoy en una frase: sale de la misma decisión que la sesión
                Module(accent: adjusted ? Palette.alert : Palette.accent) {
                    Text(screen.headline).font(.title2).bold()
                    ForEach(Array(screen.headlineDetail.enumerated()), id: \.offset) { _, r in
                        ReasonRow(reason: r, color: adjusted ? Palette.alert : Palette.accent)
                    }
                }
                if screen.askReadiness { ReadinessCard() }
                DayContent(day: day, linked: screen.linked, today: true)
                Module {
                    Text(screen.weekLine)
                    if screen.meetsWho { Text("✓ Cumple la recomendación de la OMS para tu salud").font(.subheadline).foregroundStyle(Palette.bio) }
                    ForEach(screen.warnings, id: \.self) { ReasonRow(reason: Reason(text: $0, evidence: nil)) }
                    ForEach(screen.info, id: \.self) { Secondary($0) }
                }
            }.padding(16)
        }.background(Palette.bg)
    }
}

/// Las sesiones de un día con sus acciones: hecha, enviar al reloj, alternativas, hoy no puedo.
struct DayContent: View {
    @EnvironmentObject var model: AppModel
    let day: DayView
    let linked: Bool
    let today: Bool

    var body: some View {
        let core = model.core
        VStack(alignment: .leading, spacing: 12) {
            if day.items.isEmpty && (!today || day.availableMin != nil) {
                Module {
                    if !today { Text("Descanso").font(.headline) }
                    if !today, let note = day.note { Secondary(note) }
                    if !day.isPast, let minutes = day.availableMin {
                        Button("Prefiero entrenar este día") {
                            model.run { try core.changeDay(date: day.date, minutes: minutes.int32Value) }
                        }.buttonStyle(.borderedProminent)
                    }
                }
            }
            ForEach(Array(day.items.enumerated()), id: \.offset) { _, item in
                let done = day.doneSports.contains(item.slot) || day.doneSports.contains(item.sport)
                SessionCard(item: item, done: done) {
                    HStack {
                        if !done && (day.isPast || today) {
                            Button("Marcar como hecha") { model.run { try core.markDone(date: day.date, sport: item.sport) } }
                                .buttonStyle(.bordered)
                        }
                        if today && linked {
                            Button("Enviar al reloj") {
                                model.runAsync("Enviada a intervals.icu: llegará a tu reloj si tienes la sincronización activada") {
                                    _ = try await core.sendToday(sport: item.sport)
                                }
                            }.buttonStyle(.borderedProminent)
                        }
                    }
                    if item.isStrength && (day.isPast || today) { LiftRows(date: day.date, exercises: item.exercises) }
                    if item.chosen { Button("Volver a la planificada") { model.run { core.clearChoice(date: day.date) } } }
                    if !done && !day.isPast && !item.alternatives.isEmpty {
                        DisclosureGroup(item.chosen ? "Otras opciones" : "¿Prefieres otro deporte?") {
                            ForEach(Array(item.alternatives.enumerated()), id: \.offset) { _, alt in
                                HStack {
                                    Text("\(sportLabel(alt.sport)) · \(alt.title) · \(alt.durationMin)′").font(.subheadline)
                                    Spacer()
                                    Button("Haré esta") {
                                        model.run { try core.chooseAlternative(date: day.date, plannedSport: item.slot, chosenSport: alt.sport) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ForEach(Array(day.matches.enumerated()), id: \.offset) { _, m in
                ReasonRow(reason: Reason(text: m.text, evidence: nil), color: m.counts ? Palette.bio : Palette.alert)
            }
            ForEach(Array(day.reasons.enumerated()), id: \.offset) { _, r in ReasonRow(reason: r, color: Palette.accent) }
            if !day.isPast && !day.items.isEmpty {
                Button("Hoy no puedo entrenar") { model.run { try core.changeDay(date: day.date, minutes: 0) } }
            }
        }
    }
}

private struct ReadinessCard: View {
    @EnvironmentObject var model: AppModel
    @State private var sleep = 3
    @State private var energy = 3
    @State private var soreness = 3

    var body: some View {
        Module {
            Text("¿Cómo estás hoy?").font(.headline)
            Secondary("Sin reloj vinculado, tus respuestas ajustan la sesión de hoy: si dormiste mal o estás cargado, será más suave.")
            Picker("Sueño", selection: $sleep) { ForEach(1...5, id: \.self) { Text("\($0)") } }.pickerStyle(.segmented)
            Picker("Energía", selection: $energy) { ForEach(1...5, id: \.self) { Text("\($0)") } }.pickerStyle(.segmented)
            Picker("Agujetas", selection: $soreness) { ForEach(1...5, id: \.self) { Text("\($0)") } }.pickerStyle(.segmented)
            Secondary("1 = mal · 5 = bien. Por orden: sueño, energía, agujetas o molestias.")
            Button("Ajustar hoy") {
                model.run { try model.core.answerReadiness(sleep: Int32(sleep), energy: Int32(energy), soreness: Int32(soreness)) }
            }.buttonStyle(.borderedProminent)
        }
    }
}

/// Cada ejercicio de fuerza: lo apuntado la última vez, la sugerencia de hoy y apuntar peso y repeticiones.
struct LiftRows: View {
    @EnvironmentObject var model: AppModel
    let date: String
    let exercises: [String]

    var body: some View {
        ForEach(exercises.filter { LiftsKt.repRange(line: $0) != nil }, id: \.self) { line in LiftRow(date: date, line: line) }
    }
}

private struct LiftRow: View {
    @EnvironmentObject var model: AppModel
    let date: String
    let line: String
    @State private var open = false
    @State private var weight = ""
    @State private var reps = ""

    var body: some View {
        let _ = model.version
        let hint = model.core.liftHint(exerciseLine: line)
        VStack(alignment: .leading, spacing: 4) {
            Text(hint.exercise).font(.subheadline).fontWeight(.medium)
            if let last = hint.last {
                Secondary("La última vez: \(last.weightKg.map { LiftsKt.kg(v: $0.doubleValue) } ?? "tu peso") × \(last.reps) (\(last.sets) series)")
            }
            if let s = hint.suggestion { Text(s).font(.footnote).foregroundStyle(Palette.accent) }
            if open {
                HStack {
                    TextField("kg", text: $weight).keyboardType(.decimalPad).textFieldStyle(.roundedBorder).frame(width: 80)
                    TextField("reps", text: $reps).keyboardType(.numberPad).textFieldStyle(.roundedBorder).frame(width: 80)
                    Button("Guardar") {
                        let sets = Int32(line.split(separator: " ").first { Int($0) != nil }.flatMap { Int($0) } ?? 1)
                        let kgValue = Double(weight.replacingOccurrences(of: ",", with: ".")).map { KotlinDouble(double: $0) }
                        let r = Int32(reps) ?? 0
                        model.run("Apuntado") { try model.core.logLift(date: date, exercise: line, weightKg: kgValue, reps: r, sets: sets) }
                        open = false
                    }
                }
            } else {
                Button("Apuntar") { weight = hint.last?.weightKg.map { String($0.doubleValue) } ?? ""; open = true }.font(.footnote)
            }
        }
    }
}

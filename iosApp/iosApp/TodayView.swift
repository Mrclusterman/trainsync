import SwiftUI
import Shared

struct TodayView: View {
    @EnvironmentObject var model: AppModel
    let openGoal: () -> Void

    var body: some View {
        let _ = model.version
        let screen = model.core.todayScreen()
        let day = screen.day
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenTitle(text: "Hoy", subtitle: screen.dateLabel)
                if let goal = screen.goalLine { Secondary(goal) }
                if screen.goalFinished {
                    Module {
                        Text("Tu carrera ya pasó").font(.section)
                        Secondary("¿Qué quieres ahora? Otra carrera, mantener la forma o descansar unos días.")
                        Button("Elegir objetivo", action: openGoal).buttonStyle(PrimaryStyle())
                    }
                }
                // Tu cuerpo hoy en una frase: sale de la misma decisión que la sesión
                StateCard(tone: screen.tone, title: screen.stateTitle, detail: screen.stateDetail)
                if screen.askReadiness { ReadinessCard() }
                DayContent(day: day, linked: screen.linked, today: true)
                Module {
                    HStack {
                        Text("Esta semana").font(.section)
                        Spacer()
                        Text("\(screen.weekMinutes) de 150 min").font(.mono(14, relativeTo: .subheadline)).foregroundStyle(Palette.muted)
                    }
                    WeekDots(days: screen.weekDots)
                    if screen.meetsWho {
                        HStack(spacing: 6) {
                            Glyph(name: "check", size: 18, color: Palette.ok)
                            Text("Cumples la recomendación de la OMS para tu salud").font(.ui(14, .semibold, relativeTo: .subheadline))
                                .foregroundStyle(Palette.ok)
                        }
                    } else {
                        Secondary(screen.strengthDays >= 2 ? "Fuerza: \(screen.strengthDays) días, mínimo cumplido" : "Fuerza: \(screen.strengthDays) de 2 días")
                    }
                    ForEach(screen.warnings, id: \.self) { ReasonRow(reason: Reason(text: $0, evidence: nil)) }
                    ForEach(screen.info, id: \.self) { Secondary($0) }
                }
            }.padding(.horizontal, 18).padding(.vertical, 16)
        }.background(Palette.bg)
    }
}

/// Las sesiones de un día con sus acciones: hecha, cambiar de deporte, enviar al reloj, hoy no puedo.
struct DayContent: View {
    @EnvironmentObject var model: AppModel
    let day: DayView
    let linked: Bool
    let today: Bool

    var body: some View {
        let core = model.core
        VStack(alignment: .leading, spacing: 14) {
            // En Hoy la tarjeta de estado ya dice que descansas: aquí solo lo que se puede hacer
            if day.items.isEmpty && (!today || day.availableMin != nil) {
                Module {
                    if !today { Text("Descanso").font(.section) }
                    if !today, let note = day.note { Secondary(note) }
                    if !day.isPast, let minutes = day.availableMin {
                        Button("Prefiero entrenar este día") {
                            model.run { try core.changeDay(date: day.date, minutes: minutes.int32Value) }
                        }.buttonStyle(SecondaryStyle())
                    }
                }
            }
            ForEach(Array(day.items.enumerated()), id: \.offset) { _, item in
                SessionItem(day: day, item: item, linked: linked, today: today)
            }
            ForEach(Array(day.matches.enumerated()), id: \.offset) { _, m in
                ReasonRow(reason: Reason(text: m.text, evidence: nil), color: m.counts ? Palette.ok : Palette.caution)
            }
            // En Hoy, sin ajuste, los motivos ya van en la tarjeta de estado
            if !today || !day.adjustments.isEmpty {
                ForEach(Array(day.reasons.enumerated()), id: \.offset) { _, r in ReasonRow(reason: r, color: Palette.ok) }
            }
            if !day.isPast && !day.items.isEmpty {
                Button("Hoy no puedo entrenar") { model.run { try core.changeDay(date: day.date, minutes: 0) } }
                    .font(.ui(14, .semibold)).foregroundStyle(Palette.muted)
            }
        }
    }
}

private struct SessionItem: View {
    @EnvironmentObject var model: AppModel
    let day: DayView
    let item: PlanItem
    let linked: Bool
    let today: Bool
    @State private var open = false

    var body: some View {
        let core = model.core
        let done = day.doneSports.contains(item.slot) || day.doneSports.contains(item.sport)
        SessionCard(item: item, done: done) {
            HStack(spacing: 10) {
                if !done && (day.isPast || today) {
                    Button { model.run { try core.markDone(date: day.date, sport: item.sport) } } label: {
                        Text("La he hecho").frame(maxWidth: .infinity)
                    }.buttonStyle(PrimaryStyle())
                }
                if !done && !day.isPast && !item.alternatives.isEmpty {
                    Button(open ? "Cerrar" : "Cambiar") { open.toggle() }.buttonStyle(SecondaryStyle())
                }
            }
            if open {
                ForEach(Array(item.alternatives.enumerated()), id: \.offset) { _, alt in
                    HStack(spacing: 10) {
                        SportBadge(sport: alt.sport, size: 32)
                        Text("\(alt.title) · \(alt.durationMin)′").font(.small)
                        Spacer()
                        Button("Haré esta") {
                            model.run { try core.chooseAlternative(date: day.date, plannedSport: item.slot, chosenSport: alt.sport) }
                        }.font(.ui(14, .bold))
                    }
                }
            }
            if today && linked {
                Button {
                    model.runAsync("Enviada a intervals.icu: llegará a tu reloj si tienes la sincronización activada") {
                        _ = try await core.sendToday(sport: item.sport)
                    }
                } label: {
                    HStack(spacing: 6) { Glyph(name: "watch", size: 18, color: Palette.ok); Text("Enviar al reloj") }
                }.font(.ui(14, .bold))
            }
            if item.isStrength && (day.isPast || today) { LiftRows(date: day.date, exercises: item.exercises) }
            if item.chosen { Button("Volver a la planificada") { model.run { core.clearChoice(date: day.date) } }.font(.ui(14, .bold)) }
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
            Text("¿Cómo estás hoy?").font(.section)
            Secondary("Sin reloj vinculado, tus respuestas ajustan la sesión de hoy: si dormiste mal o estás cargado, será más suave.")
            Picker("Sueño", selection: $sleep) { ForEach(1...5, id: \.self) { Text("\($0)") } }.pickerStyle(.segmented)
            Picker("Energía", selection: $energy) { ForEach(1...5, id: \.self) { Text("\($0)") } }.pickerStyle(.segmented)
            Picker("Agujetas", selection: $soreness) { ForEach(1...5, id: \.self) { Text("\($0)") } }.pickerStyle(.segmented)
            Secondary("1 = mal · 5 = bien. Por orden: sueño, energía, agujetas o molestias.")
            Button("Ajustar hoy") {
                model.run { try model.core.answerReadiness(sleep: Int32(sleep), energy: Int32(energy), soreness: Int32(soreness)) }
            }.buttonStyle(PrimaryStyle())
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
            Text(hint.exercise).font(.ui(14, .semibold, relativeTo: .subheadline))
            if let last = hint.last {
                Secondary("La última vez: \(last.weightKg.map { LiftsKt.kg(v: $0.doubleValue) } ?? "tu peso") × \(last.reps) (\(last.sets) series)")
            }
            if let s = hint.suggestion { Text(s).font(.ui(13, relativeTo: .footnote)).foregroundStyle(Palette.ok) }
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
                Button("Apuntar") { weight = hint.last?.weightKg.map { String($0.doubleValue) } ?? ""; open = true }.font(.ui(13, .bold, relativeTo: .footnote))
            }
        }
    }
}

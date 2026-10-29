import SwiftUI
import Shared

private let cardioOptions = [("walking", "Caminar"), ("running", "Correr"), ("cycling", "Bici"), ("swimming_pool", "Nadar")]
private let strengthOptions = [("gym", "Gimnasio"), ("bodyweight", "Con tu peso"), ("none", "Sin fuerza")]
private let levelOptions = [("beginner", "Empiezo"), ("intermediate", "Entreno a veces"), ("advanced", "Entreno a menudo")]
private let weekdays = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]

enum ReminderError: LocalizedError {
    case denied
    var errorDescription: String? { "Sin permiso para avisarte: actívalo en Ajustes → Notificaciones → TrainSync" }
}

/// Borrador editable del perfil en tipos de Swift; se convierte al del núcleo al guardar.
struct ProfileDraft {
    var birthYear: Int?
    var level = "beginner"
    var strength = "bodyweight"
    var cardio: [String] = ["walking"]
    var days: [String: Int] = ["MON": 45, "WED": 45, "SAT": 60]
    var renameActivities = true
    var screening: Screening?  // cuestionario del ACSM: las apps no lo preguntan (decisión 2026-10-21); se conserva si existe

    init(_ p: UserProfile?) {
        guard let p else { return }
        birthYear = p.birthYear?.intValue
        level = p.level
        strength = p.strength
        cardio = p.cardio
        days = p.days.mapValues { $0.intValue }
        renameActivities = p.renameActivities
        screening = p.screening
    }

    var core: UserProfile {
        UserProfile(birthYear: birthYear.map { KotlinInt(int: Int32($0)) }, level: level, strength: strength, cardio: cardio,
                    days: days.mapValues { KotlinInt(int: Int32($0)) }, renameActivities: renameActivities,
                    screening: screening)
    }
}

/// Datos para el plan: actividades, días y minutos, material y nivel. El objetivo no vive aquí (VISION §5).
struct ProfileForm: View {
    @Binding var draft: ProfileDraft

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Secondary("Qué actividades haces")
            FlowChips(options: cardioOptions, isSelected: { draft.cardio.contains($0) }) { key in
                if draft.cardio.contains(key) { if draft.cardio.count > 1 { draft.cardio.removeAll { $0 == key } } } else { draft.cardio.append(key) }
            }
            Secondary("Fuerza")
            FlowChips(options: strengthOptions, isSelected: { draft.strength == $0 }) { draft.strength = $0 }
            Secondary("Tu nivel")
            FlowChips(options: levelOptions, isSelected: { draft.level == $0 }) { draft.level = $0 }
            Secondary("Qué días puedes entrenar y cuánto tiempo")
            ForEach(weekdays, id: \.self) { d in
                let minutes = draft.days[d] ?? 0
                HStack {
                    Chip(label: TextsKt.WEEKDAY_LABEL[d] ?? d, selected: minutes > 0) { draft.days[d] = minutes > 0 ? 0 : 45 }
                        .frame(width: 130, alignment: .leading)
                    if minutes > 0 {
                        Stepper("\(minutes) min", value: Binding(get: { draft.days[d] ?? 45 }, set: { draft.days[d] = $0 }), in: 15...240, step: 15)
                            .font(.system(.body, design: .monospaced))
                    }
                }
            }
            Secondary("Año de nacimiento")
            TextField("1990", text: Binding(get: { draft.birthYear.map(String.init) ?? "" },
                                            set: { draft.birthYear = Int($0.filter(\.isNumber).prefix(4)) }))
                .keyboardType(.numberPad).textFieldStyle(.roundedBorder)
        }
    }
}

/// Carreras preparatorias (B o C) antes de la principal: el plan afina unos días antes y recupera después.
struct OtherRacesView: View {
    @EnvironmentObject var model: AppModel
    @State private var adding = false
    @State private var name = ""
    @State private var date = Calendar.current.date(byAdding: .month, value: 1, to: Date()) ?? Date()
    @State private var priority = "C"

    var body: some View {
        let core = model.core
        if let goal = core.data.goal, goal.kind == .race, let main = goal.race {
            VStack(alignment: .leading, spacing: 8) {
                Secondary("Otras carreras antes de \(main.name)")
                ForEach(goal.otherRaces, id: \.date) { r in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(r.name).font(.subheadline).bold()
                            Secondary(ScreensKt.dateLabel(date: r.date) + (r.priority == "B" ? " · importante" : " · de preparación"))
                        }
                        Spacer()
                        Button("Quitar") { model.run { core.removeRace(date: r.date) } }
                    }
                }
                if adding {
                    TextField("Nombre de la carrera", text: $name).textFieldStyle(.roundedBorder)
                    DatePicker("Fecha", selection: $date, displayedComponents: .date)
                    Picker("Prioridad", selection: $priority) {
                        Text("Importante (B)").tag("B")
                        Text("De preparación (C)").tag("C")
                    }.pickerStyle(.segmented)
                    HStack {
                        Button("Añadir") {
                            let f = DateFormatter()
                            f.dateFormat = "yyyy-MM-dd"
                            f.locale = Locale(identifier: "en_US_POSIX")
                            let race = RaceInfo(name: name.trimmingCharacters(in: .whitespaces), sport: main.sport, date: f.string(from: date),
                                                priority: priority, distanceKm: nil, trail: false)
                            model.run("Carrera añadida") { try core.addRace(race: race); adding = false; name = "" }
                        }.buttonStyle(.borderedProminent)
                        Button("Cancelar") { adding = false }
                    }
                } else {
                    Secondary("El plan afina unos días antes de cada una y te deja recuperar después.")
                    Button("Añadir otra carrera") { adding = true }.buttonStyle(.bordered)
                }
            }
        }
    }
}

struct FlowChips: View {
    let options: [(String, String)]
    let isSelected: (String) -> Bool
    let toggle: (String) -> Void

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), alignment: .leading)], alignment: .leading, spacing: 8) {
            ForEach(options, id: \.0) { key, label in Chip(label: label, selected: isSelected(key)) { toggle(key) } }
        }
    }
}

struct ProfileView: View {
    @EnvironmentObject var model: AppModel
    @State private var draft = ProfileDraft(nil)
    @State private var loaded = false
    @State private var athlete = ""
    @State private var apiKey = ""
    @State private var confirmDelete = false

    var body: some View {
        let _ = model.version
        let core = model.core
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Perfil").font(.title2).bold()
                Module {
                    Secondary("Tu objetivo")
                    Text(core.data.goal.map { TextsKt.goalLabel(goal: $0) } ?? "Sin objetivo").font(.title3).bold()
                    Secondary("Puedes cambiarlo o abandonarlo cuando quieras; el plan se rehace al momento.")
                    Button("Cambiar objetivo") { model.run { core.abandonGoal() } }.buttonStyle(.bordered)
                    OtherRacesView()
                }
                Module {
                    ProfileForm(draft: $draft)
                    Button("Guardar") { model.run("Perfil guardado") { core.saveProfile(profile: draft.core) } }.buttonStyle(.borderedProminent)
                }
                Module {
                    Toggle(isOn: Binding(get: { core.data.reminderHour != nil }, set: { on in
                        if on {
                            model.runAsync {
                                guard try await Reminders.requestAccess() else {
                                    throw ReminderError.denied
                                }
                                try core.setReminder(hour: KotlinInt(int: 8))
                            }
                        } else {
                            model.run { try core.setReminder(hour: nil) }
                        }
                    })) {
                        VStack(alignment: .leading, spacing: 4) {
                            Secondary("Recordatorio diario")
                            Secondary("Un aviso con la sesión del día. Los días de descanso o con todo hecho no avisa.")
                        }
                    }
                    if let hour = core.data.reminderHour?.intValue {
                        Stepper(String(format: "A las %02d:00", hour), value: Binding(get: { hour }, set: { h in
                            model.run { try core.setReminder(hour: KotlinInt(int: Int32((h + 24) % 24))) }
                        }), in: -1...24)
                        .font(.system(.body, design: .monospaced))
                    }
                }
                Module {
                    Secondary("Datos de salud del iPhone (Apple Salud)")
                    if let health = core.data.health {
                        Text("Conectado: \(health.days.count) días y \(health.workouts.count) entrenamientos").font(.headline)
                        Secondary("Datos al día del \(health.syncedAt). Los permisos se cambian en Ajustes → Salud → Acceso a datos.")
                        HStack {
                            Button("Actualizar ahora") { model.runAsync("Datos actualizados") { try await HealthKitReader.shared.sync(core) } }
                                .buttonStyle(.borderedProminent)
                            Button("Olvidar") { model.run { core.clearHealth() } }.buttonStyle(.bordered)
                        }
                    } else if HealthKitReader.shared.available {
                        Secondary("Lee de Apple Salud tu sueño, tu variabilidad cardíaca, tu pulso en reposo, tu peso, tus pasos y tus entrenamientos de cualquier reloj o app. Solo lectura y solo en este iPhone.")
                        Button("Conectar Apple Salud") {
                            model.runAsync("Datos de Apple Salud leídos") {
                                try await HealthKitReader.shared.requestAccess()
                                try await HealthKitReader.shared.sync(core)
                            }
                        }.buttonStyle(.borderedProminent)
                    } else {
                        Secondary("Apple Salud no está disponible en este dispositivo.")
                    }
                }
                Module {
                    Secondary("Reloj (intervals.icu)")
                    if let linked = core.linkedAthlete {
                        Text("Vinculado: \(linked)").font(.headline)
                        if let cache = core.data.cache { Secondary("Datos al día del \(cache.syncedAt)") }
                        HStack {
                            Button("Actualizar ahora") { model.runAsync("Datos actualizados") { try await core.sync() } }.buttonStyle(.borderedProminent)
                            Button("Desvincular") { model.run { core.unlinkIntervals() } }.buttonStyle(.bordered)
                        }
                    } else {
                        Secondary("intervals.icu es gratis y recoge los datos de tu reloj (Garmin, Polar, Coros, Suunto, Wahoo…). En intervals.icu: Settings → Developer Settings, copia tu Athlete ID y crea una API key.")
                        TextField("Athlete ID (p. ej. i123456)", text: $athlete).textFieldStyle(.roundedBorder)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                        SecureField("API key", text: $apiKey).textFieldStyle(.roundedBorder)
                        Button("Vincular") {
                            let id = athlete, key = apiKey
                            model.runAsync("Cuenta vinculada") { try await core.linkIntervals(athleteId: id, apiKey: key) }
                            apiKey = ""
                        }.buttonStyle(.borderedProminent).disabled(athlete.isEmpty || apiKey.isEmpty)
                    }
                }
                Module {
                    Secondary("Tus datos")
                    Secondary(TextsKt.PRIVACY_NOTE)
                    Toggle("Poner a tus actividades de intervals.icu un título que diga lo que fueron", isOn: Binding(
                        get: { draft.renameActivities },
                        set: { draft.renameActivities = $0; let p = draft.core; model.run { core.saveProfile(profile: p) } }))
                        .font(.subheadline)
                    ShareLink("Exportar todos mis datos (JSON)", item: core.exportJson())
                    Button("Borrar todo de este móvil", role: .destructive) { confirmDelete = true }
                }
                Module {
                    Secondary("Aviso de salud")
                    Secondary(TextsKt.HEALTH_NOTICE)
                }
                Secondary("TrainSync es gratis y de código abierto (AGPL-3.0). Sin niveles de pago, sin anuncios y sin notificaciones comerciales.")
            }.padding(16)
        }
        .background(Palette.bg)
        .onAppear {
            if !loaded { draft = ProfileDraft(core.data.profile); athlete = core.data.intervalsAthlete ?? ""; loaded = true }
        }
        .confirmationDialog("¿Borrar todo?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Borrar", role: .destructive) { model.run { core.deleteAll() } }
        } message: {
            Text("Se borran tu perfil, tu objetivo, tu historial y la clave de intervals.icu de este móvil. No se puede deshacer. Tus datos en intervals.icu no se tocan.")
        }
    }
}

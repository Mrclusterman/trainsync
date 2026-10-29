import SwiftUI
import Shared

private let goals: [(GoalKind, String, String)] = [
    (.race, "Preparar una carrera", "Un plan con fecha hacia tu prueba: carrera, bici, natación, triatlón…"),
    (.fitness, "Ponerme en forma", "Más resistencia y fuerza para el día a día, sin fecha."),
    (.strength, "Ganar fuerza y músculo", "Fuerza primero, con algo de cardio para la salud."),
    (.health, "Mejorar mi salud a largo plazo", "Cumplir lo que recomienda la OMS, con constancia."),
]
private let raceSports = [("running", "Carrera a pie"), ("cycling", "Bici"), ("swimming_pool", "Natación"),
                          ("triathlon", "Triatlón"), ("duathlon", "Duatlón")]

/// Primera vez: bienvenida, objetivo (como Garmin), tu semana y el aviso de salud. Con perfil, solo el objetivo.
struct OnboardingView: View {
    @EnvironmentObject var model: AppModel
    @State private var step = -1
    @State private var goal: GoalKind?
    @State private var raceName = ""
    @State private var raceSport = "running"
    @State private var raceDate = Calendar.current.date(byAdding: .month, value: 3, to: Date()) ?? Date()
    @State private var distance = ""
    @State private var trail = false
    @State private var priority = "A"
    @State private var draft = ProfileDraft(nil)
    @State private var accepted = false

    private var hasProfile: Bool { model.core.data.profile != nil }

    private var race: RaceInfo? {
        guard goal == .race else { return nil }
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        let km = Double(distance.replacingOccurrences(of: ",", with: "."))
        return RaceInfo(name: raceName.trimmingCharacters(in: .whitespaces), sport: raceSport, date: f.string(from: raceDate),
                        priority: priority, distanceKm: km.map { KotlinDouble(double: $0) }, trail: trail)
    }

    private func save() {
        guard let goal else { return }
        let profile = draft.core
        let race = self.race
        let firstTime = !hasProfile
        model.run {
            if firstTime { model.core.saveProfile(profile: profile) }
            try model.core.setGoal(kind: goal, race: race)
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                switch step == -1 ? (hasProfile ? 1 : 0) : step {
                case 0:
                    Text("TrainSync").font(.ui(36, .heavy, relativeTo: .largeTitle)).padding(.top, 40)
                    Text("El sistema operativo de tu cuerpo").font(.ui(20, .semibold, relativeTo: .title3)).foregroundStyle(Palette.ok)
                    Secondary("Te dice qué hacer hoy y por qué, con tus datos y según la evidencia. Gratis, sin anuncios y de código abierto.")
                    Secondary(TextsKt.PRIVACY_NOTE)
                    Button("Empezar") { step = 1 }.buttonStyle(PrimaryStyle()).frame(maxWidth: .infinity)
                case 1:
                    Text("¿Qué quieres conseguir?").font(.screenTitle).padding(.top, 24)
                    Secondary("Podrás cambiarlo o abandonarlo cuando quieras.")
                    ForEach(goals, id: \.1) { kind, title, text in
                        Module(accent: goal == kind ? Palette.ok : nil, background: goal == kind ? Palette.okSoft : Palette.surface) {
                            Text(title).font(.section)
                            Secondary(text)
                        }.onTapGesture { goal = kind }
                    }
                    if goal == .race {
                        Module {
                            TextField("Nombre de la carrera", text: $raceName).textFieldStyle(.roundedBorder)
                            FlowChips(options: raceSports, isSelected: { raceSport == $0 }) { raceSport = $0 }
                            DatePicker("Fecha", selection: $raceDate, in: Date()..., displayedComponents: .date)
                            TextField("Distancia en km (opcional)", text: $distance).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                            if raceSport == "running" { Toggle("Es por montaña (trail)", isOn: $trail) }
                            FlowChips(options: [("A", "Principal"), ("B", "Importante"), ("C", "Preparatoria")], isSelected: { priority == $0 }) { priority = $0 }
                        }
                    }
                    let raceOk = goal != .race || !raceName.trimmingCharacters(in: .whitespaces).isEmpty
                    Button(hasProfile ? "Guardar objetivo" : "Siguiente") { if hasProfile { save() } else { step = 2 } }
                        .buttonStyle(PrimaryStyle()).disabled(goal == nil || !raceOk)
                case 2:
                    Text("Tu semana").font(.screenTitle).padding(.top, 24)
                    Secondary("Con esto el plan cabe en tu vida: solo los días y minutos que tienes.")
                    ProfileForm(draft: $draft)
                    let year = Calendar.current.component(.year, from: Date())
                    let ageOk = draft.birthYear.map { TextsKt.ageAllowed(birthYear: Int32($0), currentYear: Int32(year)) } ?? false
                    if draft.birthYear != nil && !ageOk { Secondary("Hace falta tener al menos \(TextsKt.MIN_AGE) años.", color: Palette.caution) }
                    Button("Siguiente") { step = 3 }.buttonStyle(PrimaryStyle())
                        .disabled(!ageOk || !draft.days.values.contains { $0 > 0 })
                    Button("Atrás") { step = 1 }
                default:
                    Text("Antes de empezar").font(.screenTitle).padding(.top, 24)
                    Module(background: Palette.cautionSoft) { Secondary(TextsKt.HEALTH_NOTICE, color: Palette.text) }
                    Toggle("Lo he leído y entreno bajo mi responsabilidad", isOn: $accepted).font(.small)
                    Button("Ver mi plan", action: save).buttonStyle(PrimaryStyle()).disabled(!accepted)
                    Button("Atrás") { step = 2 }
                }
            }.padding(20)
        }.background(Palette.bg)
    }
}

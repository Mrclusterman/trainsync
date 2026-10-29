import SwiftUI
import Shared

/// Envuelve el núcleo compartido: cada acción sube `version` y las pantallas se recalculan.
@MainActor
final class AppModel: ObservableObject {
    let core: Shared.TrainSyncApp
    @Published var version = 0
    @Published var message: String?

    init() {
        core = Shared.TrainSyncApp(store: DefaultsStore(), secrets: KeychainStore(),
                                   today: { TrainSyncAppKt.systemToday() }, httpEngine: nil)
    }

    func refresh() {
        version += 1
        Reminders.reschedule(core)  // el texto de cada aviso depende del plan: se rehace tras cada cambio
    }

    /// Ejecuta una acción del núcleo y enseña el error si lo hay.
    func run(_ success: String? = nil, _ block: () throws -> Void) {
        do {
            try block()
            if let success { message = success }
        } catch {
            message = error.localizedDescription
        }
        refresh()
    }

    func runAsync(_ success: String? = nil, _ block: @escaping () async throws -> Void) {
        Task {
            do {
                try await block()
                if let success { message = success }
            } catch {
                message = error.localizedDescription
            }
            refresh()
        }
    }
}

@main
struct TrainSyncMain: App {
    @StateObject private var model = AppModel()

    init() { registerFonts() }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .tint(Palette.ok)
                .font(.ui(17))
                .foregroundStyle(Palette.text)
        }
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @State private var tab = UserDefaults.standard.integer(forKey: "tab")  // `-tab N` al lanzar: capturas del CI

    var body: some View {
        let _ = model.version
        Group {
            if model.core.needsOnboarding {
                OnboardingView()
            } else {
                TabView(selection: $tab) {
                    TodayView(openGoal: { model.run { model.core.abandonGoal() } })
                        .tabItem { Label { Text("Hoy") } icon: { Image("tab_today").renderingMode(.template) } }.tag(0)
                    CalendarTabView()
                        .tabItem { Label { Text("Calendario") } icon: { Image("tab_calendar").renderingMode(.template) } }.tag(1)
                    ProgressTabView(openProfile: { tab = 3 })
                        .tabItem { Label { Text("Progreso") } icon: { Image("tab_progress").renderingMode(.template) } }.tag(2)
                    ProfileView()
                        .tabItem { Label { Text("Perfil") } icon: { Image("tab_profile").renderingMode(.template) } }.tag(3)
                }
            }
        }
        .background(Palette.bg.ignoresSafeArea())
        .task {
            // Al abrir, datos frescos de intervals.icu si está vinculado; sin red, la copia guardada
            if model.core.linkedAthlete != nil { try? await model.core.sync() }
            // Apple Salud, si ya dio permiso: datos frescos sin pulsar nada
            if model.core.data.health != nil { try? await HealthKitReader.shared.sync(model.core) }
            model.refresh()
        }
        .alert(model.message ?? "", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) {
            Button("Vale", role: .cancel) {}
        }
    }
}

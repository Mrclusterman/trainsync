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

    func refresh() { version += 1 }

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

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
                .tint(Palette.accent)
        }
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @State private var tab = 0

    var body: some View {
        let _ = model.version
        Group {
            if model.core.needsOnboarding {
                OnboardingView()
            } else {
                TabView(selection: $tab) {
                    TodayView(openGoal: { model.run { model.core.abandonGoal() } })
                        .tabItem { Label("Hoy", systemImage: "sun.max") }.tag(0)
                    CalendarTabView()
                        .tabItem { Label("Calendario", systemImage: "calendar") }.tag(1)
                    ProgressTabView(openProfile: { tab = 3 })
                        .tabItem { Label("Progreso", systemImage: "chart.line.uptrend.xyaxis") }.tag(2)
                    ProfileView()
                        .tabItem { Label("Perfil", systemImage: "person") }.tag(3)
                }
            }
        }
        .background(Palette.bg.ignoresSafeArea())
        .task {
            // Al abrir, datos frescos de intervals.icu si está vinculado; sin red, la copia guardada
            if model.core.linkedAthlete != nil { try? await model.core.sync() }
            model.refresh()
        }
        .alert(model.message ?? "", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) {
            Button("Vale", role: .cancel) {}
        }
    }
}

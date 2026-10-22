import SwiftUI
import Shared

struct ContentView: View {
    var body: some View {
        Text("TrainSync · motor \(Engine.shared.VERSION)")
            .preferredColorScheme(.dark)
    }
}

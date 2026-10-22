import SwiftUI
import Shared

/// Tu evolución: sueño, VFC, FC en reposo, VO2max, peso, pasos y forma, con tu rango normal (punto 8 de la revisión).
struct ProgressTabView: View {
    @EnvironmentObject var model: AppModel
    let openProfile: () -> Void

    var body: some View {
        let _ = model.version
        let screen = model.core.progress()
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Tu evolución").font(.title2).bold()
                if !screen.linked {
                    Module {
                        Secondary("Para ver tu sueño, tu VFC, tu pulso en reposo o tu VO2max hace falta un reloj. Vincula tu cuenta de intervals.icu (gratis), que recoge los datos de Garmin, Polar, Coros, Suunto, Wahoo y otros.")
                        Button("Vincular intervals.icu", action: openProfile).buttonStyle(.borderedProminent)
                    }
                } else {
                    Module {
                        Text("Minutos de actividad por semana").font(.headline)
                        Secondary("La OMS recomienda al menos 150 minutos moderados (o 75 intensos) a la semana.")
                        Bars(points: screen.weeklyMinutes, target: 150)
                    }
                    ForEach(Array(screen.metrics.enumerated()), id: \.offset) { _, m in MetricCard(metric: m) }
                }
            }.padding(16)
        }.background(Palette.bg)
    }
}

private func format(_ v: Double?) -> String {
    guard let v else { return "—" }
    return v >= 100 ? String(Int(v.rounded())) : String(format: "%.1f", v)
}

private struct MetricCard: View {
    let metric: Metric
    @State private var open = false

    var body: some View {
        let latest = metric.latest?.doubleValue
        let low = metric.normalLow?.doubleValue
        let high = metric.normalHigh?.doubleValue
        Module {
            HStack(alignment: .bottom) {
                Text(metric.label).font(.headline)
                Spacer()
                Text(format(latest) + (metric.unit.isEmpty ? "" : " \(metric.unit)")).font(.system(.title2, design: .monospaced))
            }
            if let low, let high {
                let inRange = (latest ?? 0) >= low && (latest ?? 0) <= high
                Secondary("Tu rango normal: \(format(low))–\(format(high))" + (inRange ? "" : " · hoy fuera de tu rango"),
                          color: inRange ? Palette.secondary : Palette.alert)
            }
            Sparkline(values: metric.points.map { $0.value }, low: low, high: high).frame(height: 64)
            if open { Secondary(metric.explanation) }
        }
        .onTapGesture { open.toggle() }
    }
}

private struct Sparkline: View {
    let values: [Double]
    let low: Double?
    let high: Double?

    var body: some View {
        GeometryReader { geo in
            let lo = min(values.min() ?? 0, low ?? .infinity)
            let hi = max(values.max() ?? 1, high ?? -.infinity)
            let span = max(hi - lo, 1)
            let y = { (v: Double) in geo.size.height * (1 - (v - lo) / span) }
            ZStack {
                if let low, let high {
                    Rectangle().fill(Palette.bio.opacity(0.1))
                        .frame(height: y(low) - y(high)).position(x: geo.size.width / 2, y: (y(low) + y(high)) / 2)
                }
                Path { p in
                    for (i, v) in values.enumerated() {
                        let pt = CGPoint(x: geo.size.width * Double(i) / Double(max(values.count - 1, 1)), y: y(v))
                        if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
                    }
                }.stroke(Palette.accent, lineWidth: 2)
            }
        }
    }
}

private struct Bars: View {
    let points: [Shared.Point]
    let target: Double

    var body: some View {
        let top = max(points.map { $0.value }.max() ?? 0, target * 1.2)
        GeometryReader { geo in
            HStack(alignment: .bottom, spacing: 4) {
                ForEach(Array(points.enumerated()), id: \.offset) { _, p in
                    RoundedRectangle(cornerRadius: 3).fill(p.value >= target ? Palette.bio : Palette.accent.opacity(0.6))
                        .frame(height: geo.size.height * p.value / top)
                }
            }
            .frame(maxHeight: .infinity, alignment: .bottom)
            .overlay(alignment: .top) {
                Rectangle().fill(Color.white.opacity(0.4)).frame(height: 1).offset(y: geo.size.height * (1 - target / top))
            }
        }.frame(height: 96)
    }
}

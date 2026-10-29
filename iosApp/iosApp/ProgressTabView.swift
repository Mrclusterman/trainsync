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
                        Secondary("Para ver tu sueño, tu VFC, tu pulso en reposo o tu VO2max, conecta en Perfil Apple Salud (los datos de tu reloj o tu báscula en este iPhone) o tu cuenta de intervals.icu (gratis).")
                        Button("Ir a Perfil", action: openProfile).buttonStyle(.borderedProminent)
                    }
                } else {
                    if let st = screen.status {
                        Module(accent: st.label == "Sobrecarga" || st.label == "Perdiendo forma" ? Palette.alert : Palette.bio) {
                            Secondary("Estado de entrenamiento")
                            Text(st.label).font(.title3).bold()
                            Secondary(st.text)
                        }
                    }
                    if !screen.predictions.isEmpty {
                        Module {
                            Secondary("Tiempos previstos")
                            ForEach(screen.predictions, id: \.distance) { p in
                                HStack {
                                    Text(p.distance)
                                    Spacer()
                                    Text(p.time).font(.system(.body, design: .monospaced))
                                }
                            }
                            if let source = screen.predictionSource { Secondary("Calculado con \(source) (fórmula de Daniels).") }
                        }
                    }
                    Module {
                        Text("Minutos de actividad por semana").font(.headline)
                        Secondary("La OMS recomienda al menos 150 minutos moderados (o 75 intensos) a la semana.")
                        Bars(points: screen.weeklyMinutes, target: 150)
                    }
                    ForEach(Array(screen.metrics.enumerated()), id: \.offset) { _, m in MetricCard(metric: m) }
                    if !screen.recent.isEmpty {
                        Module {
                            Secondary("Tus últimos entrenamientos")
                            ForEach(Array(screen.recent.enumerated()), id: \.offset) { _, r in RecentRow(activity: r) }
                        }
                    }
                }
            }.padding(16)
        }.background(Palette.bg)
    }
}

private func format(_ v: Double?) -> String {
    guard let v else { return "—" }
    return v >= 100 ? String(Int(v.rounded())) : String(format: "%.1f", v)
}

/// Un entrenamiento: al tocarlo, lo medido y los minutos en cada zona.
private struct RecentRow: View {
    let activity: RecentActivity
    @State private var open = false

    var body: some View {
        let zones = activity.zoneMinutes.map { $0.intValue }
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                SportDot(sport: activity.sport)
                VStack(alignment: .leading) {
                    Text(activity.title).font(.subheadline)
                    Secondary(ScreensKt.dateLabel(date: activity.date))
                }
                Spacer()
                Text("\(activity.minutes)′").font(.system(.body, design: .monospaced))
            }
            if open {
                if let detail = activity.detail { Secondary(detail) }
                if zones.reduce(0, +) > 0 {
                    GeometryReader { geo in
                        HStack(spacing: 0) {
                            ForEach(Array(zones.enumerated()), id: \.offset) { i, m in
                                Rectangle().fill(Palette.zones["Z\(min(i + 1, 6))"] ?? Palette.accent)
                                    .frame(width: geo.size.width * CGFloat(m) / CGFloat(zones.reduce(0, +)))
                            }
                        }
                    }.frame(height: 8)
                    Secondary(zones.enumerated().filter { $0.element > 0 }.map { "Z\($0.offset + 1) \($0.element)′" }.joined(separator: " · "))
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { open.toggle() }
    }
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

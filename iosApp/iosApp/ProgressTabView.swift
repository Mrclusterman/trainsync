import SwiftUI
import Shared

private let bodyKeys = ["sleep", "sleepScore", "hrv", "restingHR", "weight", "steps"]

/// Tu evolución: cómo está tu cuerpo frente a tu rango normal, tu forma y tu actividad (punto 8 de la revisión).
struct ProgressTabView: View {
    @EnvironmentObject var model: AppModel
    let openProfile: () -> Void

    var body: some View {
        let _ = model.version
        let screen = model.core.progress()
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenTitle(text: "Progreso", subtitle: "Últimos 90 días")
                if !screen.linked {
                    Module {
                        Secondary("Para ver tu sueño, tu VFC, tu pulso en reposo o tu VO2max, conecta en Perfil Apple Salud (los datos de tu reloj o tu báscula en este iPhone) o tu cuenta de intervals.icu (gratis).")
                        Button("Ir a Perfil", action: openProfile).buttonStyle(PrimaryStyle())
                    }
                } else {
                    let bodyMetrics = screen.metrics.filter { bodyKeys.contains($0.key) }
                    let training = screen.metrics.filter { !bodyKeys.contains($0.key) }
                    if !bodyMetrics.isEmpty {
                        Module {
                            HStack(alignment: .bottom) {
                                Text("Tu cuerpo").font(.section)
                                Spacer()
                                Text("último dato · banda = tu rango").font(.caption12).foregroundStyle(Palette.muted)
                            }
                            ForEach(Array(bodyMetrics.enumerated()), id: \.offset) { i, m in MetricRow(metric: m, first: i == 0) }
                        }
                    }
                    if screen.status != nil || !training.isEmpty {
                        Module {
                            if let st = screen.status {
                                let warn = st.label == "Sobrecarga" || st.label == "Perdiendo forma"
                                Text("Forma: \(st.label.lowercased())").font(.section)
                                if let ctl = screen.metrics.first(where: { $0.key == "ctl" }) {
                                    Sparkline(values: ctl.points.map { $0.value }, low: nil, high: nil, color: warn ? Palette.caution : Palette.ok)
                                        .frame(height: 56)
                                }
                                Secondary(st.text)
                            }
                            ForEach(Array(training.enumerated()), id: \.offset) { i, m in
                                MetricRow(metric: m, first: i == 0 && screen.status == nil)
                            }
                        }
                    }
                    Module {
                        let last = screen.weeklyMinutes.last?.value ?? 0
                        HStack(alignment: .bottom) {
                            Text("Actividad semanal").font(.section)
                            Spacer()
                            Text("\(Int(last.rounded())) / 150 min").font(.mono(15, .semibold))
                        }
                        ProgressLine(fraction: last / 150)
                        Secondary("La OMS recomienda al menos 150 minutos moderados (o 75 intensos) a la semana.")
                        Bars(points: screen.weeklyMinutes, target: 150)
                    }
                    if !screen.predictions.isEmpty {
                        Module {
                            Text("Tiempos previstos").font(.section)
                            ForEach(screen.predictions, id: \.distance) { p in
                                HStack {
                                    Text(p.distance).font(.body15)
                                    Spacer()
                                    Text(p.time).font(.mono(15, .semibold))
                                }
                            }
                            if let source = screen.predictionSource { Secondary("Calculado con \(source) (fórmula de Daniels).") }
                        }
                    }
                    if !screen.recent.isEmpty {
                        Module {
                            Text("Tus últimos entrenamientos").font(.section)
                            ForEach(Array(screen.recent.enumerated()), id: \.offset) { _, r in RecentRow(activity: r) }
                        }
                    }
                }
            }.padding(.horizontal, 18).padding(.vertical, 16)
        }.background(Palette.bg)
    }
}

private func format(_ v: Double?) -> String {
    guard let v else { return "—" }
    return v >= 20 ? String(Int(v.rounded())) : String(format: "%.1f", v).replacingOccurrences(of: ".", with: ",")
}

/// Un entrenamiento: al tocarlo, lo medido y los minutos en cada zona.
private struct RecentRow: View {
    let activity: RecentActivity
    @State private var open = false

    var body: some View {
        let zones = activity.zoneMinutes.map { $0.intValue }
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 10) {
                SportBadge(sport: activity.sport, size: 32)
                VStack(alignment: .leading) {
                    Text(activity.title).font(.ui(15, .semibold, relativeTo: .subheadline))
                    Text(ScreensKt.dateLabel(date: activity.date)).font(.caption12).foregroundStyle(Palette.muted)
                }
                Spacer()
                Text("\(activity.minutes)′").font(.mono(15))
            }
            if open {
                if let detail = activity.detail { Secondary(detail) }
                if zones.reduce(0, +) > 0 {
                    GeometryReader { geo in
                        HStack(spacing: 0) {
                            ForEach(Array(zones.enumerated()), id: \.offset) { i, m in
                                Rectangle().fill(Palette.zones["Z\(min(i + 1, 6))"] ?? Palette.ok)
                                    .frame(width: geo.size.width * CGFloat(m) / CGFloat(zones.reduce(0, +)))
                            }
                        }
                    }.frame(height: 10).clipShape(Capsule())
                    Secondary(zones.enumerated().filter { $0.element > 0 }.map { "Z\($0.offset + 1) \($0.element)′" }.joined(separator: " · "))
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { open.toggle() }
    }
}

/// Un indicador: valor, barra frente a tu rango y una frase; al tocarlo, su evolución y qué significa.
private struct MetricRow: View {
    let metric: Metric
    let first: Bool
    @State private var open = false

    var body: some View {
        let latest = metric.latest?.doubleValue
        let low = metric.normalLow?.doubleValue
        let high = metric.normalHigh?.doubleValue
        VStack(alignment: .leading, spacing: 6) {
            if !first { Rectangle().fill(Palette.chip).frame(height: 1).padding(.bottom, 6) }
            HStack(alignment: .bottom) {
                Text(metric.label).font(.ui(15, .semibold, relativeTo: .subheadline))
                Spacer()
                Text(format(latest) + (metric.unit.isEmpty ? "" : " \(metric.unit)")).font(.mono(15, .semibold))
            }
            if let low, let high, let latest, format(low) != format(high) {
                let values = metric.points.map { $0.value }
                let lo = min(low, values.min() ?? low)
                let hi = max(high, values.max() ?? high)
                let pad = (hi - lo) * 0.15
                let inRange = latest >= low && latest <= high
                let better = metric.higherIsBetter?.boolValue
                // Fuera de rango hacia el lado bueno (más VO2max, menos pulso en reposo) no es un aviso
                let good = inRange || (better == true && latest > high) || (better == false && latest < low)
                let range = low < 0 ? "\(format(low)) a \(format(high))" : "\(format(low))–\(format(high))"
                RangeBar(value: latest, low: low, high: high, domainMin: lo - pad, domainMax: hi + pad, good: good)
                Text(inRange ? "Dentro de tu rango (\(range))" : latest > high ? "Por encima de lo tuyo (\(range))" : "Por debajo de lo tuyo (\(range))")
                    .font(.caption12).foregroundStyle(good ? Palette.muted : Palette.caution)
            }
            if open {
                Sparkline(values: metric.points.map { $0.value }, low: low, high: high, color: Palette.ok).frame(height: 64)
                Secondary(metric.explanation)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { open.toggle() }
    }
}

private struct Sparkline: View {
    let values: [Double]
    let low: Double?
    let high: Double?
    let color: Color

    var body: some View {
        GeometryReader { geo in
            let lo = min(values.min() ?? 0, low ?? .infinity)
            let hi = max(values.max() ?? 1, high ?? -.infinity)
            let span = max(hi - lo, 1)
            let y = { (v: Double) in geo.size.height * (1 - (v - lo) / span) }
            ZStack {
                if let low, let high {
                    Rectangle().fill(Palette.band)
                        .frame(height: y(low) - y(high)).position(x: geo.size.width / 2, y: (y(low) + y(high)) / 2)
                }
                Path { p in
                    for (i, v) in values.enumerated() {
                        let pt = CGPoint(x: geo.size.width * Double(i) / Double(max(values.count - 1, 1)), y: y(v))
                        if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
                    }
                }.stroke(color, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
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
                    RoundedRectangle(cornerRadius: 4).fill(p.value >= target ? Palette.ok : Palette.chip)
                        .frame(height: geo.size.height * p.value / top)
                }
            }
            .frame(maxHeight: .infinity, alignment: .bottom)
            .overlay(alignment: .top) {
                Rectangle().fill(Palette.muted).frame(height: 1).offset(y: geo.size.height * (1 - target / top))
            }
        }.frame(height: 96)
    }
}

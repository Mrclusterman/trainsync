import SwiftUI
import Shared

/// Athletic Precision (docs/DISENO_ATHLETIC_PRECISION.md): oscuro, números en monoespaciada, color de zona constante.
enum Palette {
    static let bg = Color(hex: 0x0A0A0F)
    static let surface1 = Color(hex: 0x12121A)
    static let surface2 = Color(hex: 0x1A1A26)
    static let accent = Color(hex: 0x3D7EFF)
    static let bio = Color(hex: 0x00E5A0)
    static let alert = Color(hex: 0xFF8C42)
    static let danger = Color(hex: 0xFF4D6D)
    static let secondary = Color(hex: 0x8888AA)
    static let zones: [String: Color] = ["Z1": Color(hex: 0x4ECDC4), "Z2": Color(hex: 0x45B7D1), "Z3": Color(hex: 0xFFA500),
                                         "Z4": Color(hex: 0xFF6B35), "Z5": Color(hex: 0xFF4757), "Z6": Color(hex: 0xC0392B)]
    static let sports: [String: Color] = ["running": Color(hex: 0xFF6B35), "cycling": Color(hex: 0x3D7EFF),
                                          "swimming_pool": Color(hex: 0x45B7D1), "swimming_open": Color(hex: 0x45B7D1),
                                          "walking": Color(hex: 0x00E5A0), "strength": Color(hex: 0xB388FF)]
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

func sportLabel(_ sport: String) -> String { TextsKt.SPORT_LABEL[sport] ?? sport }

struct Module<Content: View>: View {
    var accent: Color? = nil
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Palette.surface1)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(accent?.opacity(0.35) ?? .clear, lineWidth: 1))
    }
}

struct Secondary: View {
    let text: String
    var color: Color = Palette.secondary
    init(_ text: String, color: Color = Palette.secondary) { self.text = text; self.color = color }
    var body: some View { Text(text).font(.subheadline).foregroundStyle(color) }
}

struct SportDot: View {
    let sport: String
    var size: CGFloat = 10
    var body: some View { Circle().fill(Palette.sports[sport] ?? Palette.accent).frame(width: size, height: size) }
}

/// Motivo con su etiqueta de evidencia: se toca para ver qué significa.
struct ReasonRow: View {
    let reason: Reason
    var color: Color = Palette.alert
    @State private var open = false

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Rectangle().fill(color).frame(width: 2, height: 18)
            VStack(alignment: .leading, spacing: 4) {
                Text(reason.text).font(.subheadline)
                if let tag = reason.evidence {
                    Button(tag) { open.toggle() }
                        .font(.caption).foregroundStyle(Palette.secondary)
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(Palette.surface2).clipShape(RoundedRectangle(cornerRadius: 8))
                    if open {
                        Secondary("\(TextsKt.EVIDENCE_MEANING[tag] ?? "") Fuentes en la documentación del motor.")
                    }
                }
            }
        }
    }
}

func minutesText(_ m: Double) -> String {
    if m < 1 { return "\(Int(m * 60))″" }
    if m.truncatingRemainder(dividingBy: 1) == 0 { return "\(Int(m))′" }
    return "\(Int(m))′\(String(format: "%02d", Int(m.truncatingRemainder(dividingBy: 1) * 60)))″"
}

/// Una sesión con su nombre llano, cómo saber si vas bien y sus pasos; el nombre técnico al tocar.
struct SessionCard<Actions: View>: View {
    let item: PlanItem
    let done: Bool
    @ViewBuilder var actions: Actions
    @State private var details = false

    var body: some View {
        Module(accent: done ? Palette.bio : nil) {
            HStack {
                SportDot(sport: item.sport, size: 12)
                VStack(alignment: .leading) {
                    Text(item.isStrength ? item.title : "\(sportLabel(item.sport)) · \(item.title)").font(.headline)
                    if item.chosen { Text("Elegida por ti").font(.caption).foregroundStyle(Palette.accent) }
                }
                Spacer()
                Text("\(item.durationMin)′").font(.system(.body, design: .monospaced))
            }
            Secondary(item.hint)
            ForEach(Array(item.steps.enumerated()), id: \.offset) { _, s in
                HStack {
                    RoundedRectangle(cornerRadius: 3).fill(Palette.zones[s.intensityZone] ?? Palette.accent).frame(width: 8, height: 18)
                    Text("\(s.reps > 1 ? "\(s.reps) × " : "")\(s.meters.map { "\($0.intValue) m" } ?? minutesText(s.minutes)) \(s.name)").font(.subheadline)
                    Spacer()
                    if let target = s.target { Text(target).font(.system(.caption, design: .monospaced)).foregroundStyle(Palette.secondary) }
                }
            }
            ForEach(item.exercises, id: \.self) { Text("• \($0)").font(.subheadline) }
            Button(details ? "Menos detalles" : "Más detalles") { details.toggle() }.font(.footnote)
            if details {
                Secondary("Nombre técnico: \(item.technicalName)")
                if let notes = item.notes { Secondary(notes) }
            }
            if done { Text("Hecha").foregroundStyle(Palette.bio).fontWeight(.medium) }
            actions
        }
    }
}

struct Chip: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.subheadline).padding(.horizontal, 14).padding(.vertical, 8)
                .background(selected ? Color(hex: 0x1E3A70) : Color.clear)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? Color.clear : Palette.secondary.opacity(0.5)))
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }.buttonStyle(.plain)
    }
}

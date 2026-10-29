import SwiftUI
import Shared

/// Pulso (docs/DISENO_PULSO.md): tonos de bosque y piel, formas suaves; el color dice cómo estás. Mismos valores en Android.
enum Palette {
    static let bg = Color(light: 0xF3F0E9, dark: 0x0F1412)
    static let surface = Color(light: 0xFFFFFF, dark: 0x18201C)
    static let chip = Color(light: 0xECE7DC, dark: 0x243029)
    static let text = Color(light: 0x1B2420, dark: 0xEEF2EE)
    static let muted = Color(light: 0x5D6762, dark: 0xA3B0A8)
    static let ok = Color(light: 0x1F7A5C, dark: 0x5FD3A4)
    static let onOk = Color(light: 0xFFFFFF, dark: 0x0B1F16)
    static let okSoft = Color(light: 0xDCEFE5, dark: 0x16302A)
    static let band = Color(light: 0xCDE8DA, dark: 0x1E4A3B)
    static let caution = Color(light: 0x9A6400, dark: 0xF2B95A)
    static let cautionSoft = Color(light: 0xF6E7C8, dark: 0x3A2E17)
    static let danger = Color(light: 0xB3402A, dark: 0xFF8A73)
    // Las zonas no cambian con el modo: son el mismo código de color en la app, el reloj y la documentación
    static let zones: [String: Color] = ["Z1": Color(hex: 0x4ECDC4), "Z2": Color(hex: 0x45B7D1), "Z3": Color(hex: 0xFFA500),
                                         "Z4": Color(hex: 0xFF6B35), "Z5": Color(hex: 0xFF4757), "Z6": Color(hex: 0xC0392B)]
    static let onZone = Color(hex: 0x0B1F22)
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
}

/// Figtree para la interfaz e IBM Plex Mono para los números, empaquetadas (OFL); crecen con el tamaño de texto del sistema.
extension Font {
    static func ui(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("Figtree", size: size, relativeTo: style).weight(weight)
    }
    static func mono(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("IBM Plex Mono", size: size, relativeTo: style).weight(weight)
    }
    static let screenTitle = ui(26, .heavy, relativeTo: .largeTitle)
    static let cardTitle = ui(24, .heavy, relativeTo: .title)
    static let section = ui(15, .bold, relativeTo: .headline)
    static let body15 = ui(15, relativeTo: .subheadline)
    static let small = ui(14, relativeTo: .subheadline)
    static let caption12 = ui(12, relativeTo: .caption)
}

/// Registra las fuentes del paquete de la app al arrancar.
func registerFonts() {
    for url in Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [] {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }
}

func sportLabel(_ sport: String) -> String { TextsKt.SPORT_LABEL[sport] ?? sport }

func sportIcon(_ sport: String) -> String {
    switch sport {
    case "running": return "sport_running"
    case "cycling": return "sport_cycling"
    case "swimming_pool", "swimming_open": return "sport_swimming"
    case "walking": return "sport_walking"
    default: return "sport_strength"
    }
}

/// Tarjeta base: superficie, radio 24 y relleno 18. `accent` la remarca (sesión hecha).
struct Module<Content: View>: View {
    var accent: Color? = nil
    var background: Color = Palette.surface
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(accent ?? .clear, lineWidth: 1.5))
    }
}

/// Título de pantalla con su subtítulo (fecha, periodo).
struct ScreenTitle: View {
    let text: String
    var subtitle: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let subtitle { Text(subtitle).font(.ui(14, .medium, relativeTo: .subheadline)).foregroundStyle(Palette.muted) }
            Text(text).font(.screenTitle)
        }.padding(.top, 8)
    }
}

struct Secondary: View {
    let text: String
    var color: Color = Palette.muted
    init(_ text: String, color: Color = Palette.muted) { self.text = text; self.color = color }
    var body: some View { Text(text).font(.small).foregroundStyle(color) }
}

struct Glyph: View {
    let name: String
    var size: CGFloat = 22
    var color: Color = Palette.text
    var body: some View { Image(name).renderingMode(.template).resizable().frame(width: size, height: size).foregroundStyle(color) }
}

/// Icono de deporte en su pastilla, como en la cabecera de la sesión.
struct SportBadge: View {
    let sport: String
    var size: CGFloat = 36
    var body: some View {
        Glyph(name: sportIcon(sport), size: size * 0.56)
            .frame(width: size, height: size)
            .background(Palette.chip)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .accessibilityLabel(sportLabel(sport))
    }
}

struct ZoneChip: View {
    let zone: String
    var body: some View {
        Text(zone).font(.mono(12, .semibold, relativeTo: .caption)).foregroundStyle(Palette.onZone)
            .frame(width: 40, height: 24).background(Palette.zones[zone] ?? Palette.chip).clipShape(Capsule())
    }
}

struct PrimaryStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { Primary(configuration: configuration) }

    private struct Primary: View {
        let configuration: ButtonStyleConfiguration
        @Environment(\.isEnabled) private var enabled
        var body: some View {
            configuration.label.font(.ui(15, .bold)).padding(.horizontal, 20).frame(minHeight: 50)
                .background(enabled ? Palette.ok : Palette.chip).foregroundStyle(enabled ? Palette.onOk : Palette.muted).clipShape(Capsule())
                .opacity(configuration.isPressed ? 0.8 : 1).scaleEffect(configuration.isPressed ? 0.98 : 1)
        }
    }
}

struct SecondaryStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.ui(15, .semibold)).padding(.horizontal, 18).frame(minHeight: 50)
            .background(Palette.chip).foregroundStyle(Palette.text).clipShape(Capsule())
            .opacity(configuration.isPressed ? 0.8 : 1).scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

/// Cómo estás hoy: el color y el icono dicen bien, con cuidado o descanso; debajo, el porqué.
struct StateCard: View {
    let tone: Tone
    let title: String
    let detail: [Reason]

    var body: some View {
        let (bg, fg, icon): (Color, Color, String) =
            tone == .caution ? (Palette.cautionSoft, Palette.caution, "state_caution")
            : tone == .rest ? (Palette.chip, Palette.muted, "state_rest")
            : (Palette.okSoft, Palette.ok, "state_ok")
        HStack(alignment: detail.isEmpty ? .center : .top, spacing: 12) {
            Glyph(name: icon, color: tone == .ok ? Palette.onOk : Palette.bg)
                .frame(width: 44, height: 44).background(fg).clipShape(Circle())
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(.ui(16, .bold, relativeTo: .headline))
                ForEach(Array(detail.enumerated()), id: \.offset) { _, r in ReasonRow(reason: r, color: fg) }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(bg)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

/// Motivo con su etiqueta de evidencia: se toca para ver qué significa.
struct ReasonRow: View {
    let reason: Reason
    var color: Color = Palette.caution
    @State private var open = false

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 3, height: 16).padding(.top, 3)
            VStack(alignment: .leading, spacing: 4) {
                Text(reason.text).font(.small)
                if let tag = reason.evidence {
                    Button(tag) { open.toggle() }
                        .font(.ui(11, .semibold, relativeTo: .caption2)).foregroundStyle(Palette.muted)
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(Palette.chip).clipShape(RoundedRectangle(cornerRadius: 8))
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

/// Una sesión: deporte y duración, nombre llano, cómo saber si vas bien y sus pasos con su zona; el nombre técnico al tocar.
struct SessionCard<Actions: View>: View {
    let item: PlanItem
    let done: Bool
    @ViewBuilder var actions: Actions
    @State private var details = false

    var body: some View {
        Module(accent: done ? Palette.ok : nil) {
            HStack(spacing: 10) {
                SportBadge(sport: item.sport)
                Text(item.chosen ? "\(sportLabel(item.sport)) · elegida por ti" : sportLabel(item.sport))
                    .font(.ui(13, .semibold, relativeTo: .footnote)).foregroundStyle(Palette.muted)
                Spacer()
                if done {
                    Glyph(name: "check", size: 18, color: Palette.ok)
                    Text("Hecha").font(.ui(13, .bold, relativeTo: .footnote)).foregroundStyle(Palette.ok)
                }
                (Text("\(item.durationMin)").font(.mono(22, .semibold, relativeTo: .title2))
                    + Text(" min").font(.mono(13, relativeTo: .footnote)).foregroundColor(Palette.muted))
            }
            Text(item.title).font(.cardTitle)
            Secondary(item.hint)
            ForEach(Array(item.steps.enumerated()), id: \.offset) { _, s in
                HStack(spacing: 10) {
                    ZoneChip(zone: s.intensityZone)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(s.name.prefix(1).uppercased() + s.name.dropFirst()).font(.ui(15, .semibold, relativeTo: .subheadline))
                        if let target = s.target { Text(target).font(.mono(12, relativeTo: .caption)).foregroundStyle(Palette.muted) }
                    }
                    Spacer()
                    Text("\(s.reps > 1 ? "\(s.reps)×" : "")\(s.meters.map { "\($0.intValue) m" } ?? minutesText(s.minutes))")
                        .font(.mono(14, relativeTo: .subheadline)).foregroundStyle(Palette.muted)
                }
            }
            ForEach(item.exercises, id: \.self) { Text($0).font(.body15) }
            actions
            Button(details ? "Menos detalles" : "Más detalles") { details.toggle() }.font(.ui(13, .bold, relativeTo: .footnote))
            if details {
                Secondary("Nombre técnico: \(item.technicalName)")
                if let notes = item.notes { Secondary(notes) }
            }
        }
    }
}

/// La semana en círculos: relleno si entrenaste, anillo de color hoy.
struct WeekDots: View {
    let days: [WeekDot]
    var body: some View {
        HStack {
            ForEach(Array(days.enumerated()), id: \.offset) { i, d in
                if i > 0 { Spacer(minLength: 0) }
                VStack(spacing: 6) {
                    ZStack {
                        Circle().fill(d.done ? Palette.ok : Color.clear)
                        Circle().stroke(d.isToday ? Palette.ok : Palette.chip, lineWidth: 2)
                        if d.planned && !d.done { Circle().fill(Palette.muted).frame(width: 6, height: 6) }
                    }.frame(width: 34, height: 34)
                    Text(d.letter).font(.ui(12, .semibold, relativeTo: .caption)).foregroundStyle(Palette.muted)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(d.letter): \(d.done ? "hecho" : d.planned ? "planificado" : "libre")")
            }
        }
    }
}

/// Un valor frente a tu rango normal: banda verde = tu rango, marca = el valor (ámbar si se sale hacia el lado malo).
struct RangeBar: View {
    let value: Double, low: Double, high: Double, domainMin: Double, domainMax: Double, good: Bool
    var body: some View {
        GeometryReader { geo in
            let span = domainMax - domainMin > 0 ? domainMax - domainMin : 1
            let f = { (v: Double) in CGFloat(min(max((v - domainMin) / span, 0), 1)) * geo.size.width }
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.chip).frame(height: 8)
                Capsule().fill(Palette.band).frame(width: f(high) - f(low), height: 8).offset(x: f(low))
                RoundedRectangle(cornerRadius: 2).fill(good ? Palette.ok : Palette.caution).frame(width: 4, height: 16).offset(x: f(value) - 2)
            }.frame(height: 16)
        }.frame(height: 16)
    }
}

/// Barra de progreso fina hacia un objetivo (minutos OMS).
struct ProgressLine: View {
    let fraction: Double
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.chip)
                Capsule().fill(Palette.ok).frame(width: geo.size.width * min(max(fraction, 0), 1))
            }
        }.frame(height: 6)
    }
}

struct Chip: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.ui(14, .semibold, relativeTo: .subheadline)).padding(.horizontal, 14).padding(.vertical, 8)
                .background(selected ? Palette.okSoft : Palette.chip)
                .foregroundStyle(selected ? Palette.ok : Palette.text)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(selected ? Palette.ok : .clear, lineWidth: 1.5))
        }.buttonStyle(.plain)
    }
}

import SwiftUI
import Shared

/// Calendario mensual: el pasado muestra lo que hiciste de verdad y el futuro, el plan; el día elegido, debajo (punto 4 de la revisión).
struct CalendarTabView: View {
    @EnvironmentObject var model: AppModel
    @State private var year = Calendar.current.component(.year, from: Date())
    @State private var month = Calendar.current.component(.month, from: Date())
    // El día elegido se ve debajo del mes; hoy al abrir
    @State private var selected: String = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }()

    var body: some View {
        let _ = model.version
        let screen = model.core.month(year: Int32(year), month: Int32(month))
        ScrollView {
            VStack(spacing: 10) {
                HStack {
                    Button { if month == 1 { month = 12; year -= 1 } else { month -= 1 } } label: { Glyph(name: "chevron_left") }
                        .frame(width: 44, height: 44).accessibilityLabel("Mes anterior")
                    Text(screen.title).font(.screenTitle)
                    Spacer()
                    Button { if month == 12 { month = 1; year += 1 } else { month += 1 } } label: { Glyph(name: "chevron_right") }
                        .frame(width: 44, height: 44).accessibilityLabel("Mes siguiente")
                }
                HStack {
                    ForEach(["L", "M", "X", "J", "V", "S", "D"], id: \.self) {
                        Text($0).font(.caption12).foregroundStyle(Palette.muted).frame(maxWidth: .infinity)
                    }
                }
                ForEach(Array(screen.weeks.enumerated()), id: \.offset) { _, week in
                    HStack(spacing: 4) {
                        ForEach(Array(week.enumerated()), id: \.offset) { _, d in
                            DayCell(day: d, selected: d.date == selected).onTapGesture { selected = d.date }
                        }
                    }
                }
                HStack(spacing: 6) {
                    Glyph(name: "sport_running", size: 16, color: Palette.muted)
                    Text("planificada").font(.ui(13)).foregroundStyle(Palette.muted)
                    Glyph(name: "check", size: 16, color: Palette.ok).padding(.leading, 8)
                    Text("hecha").font(.ui(13)).foregroundStyle(Palette.muted)
                    Spacer()
                }
                DayDetail(date: selected, cell: screen.weeks.flatMap { $0 }.first { $0.date == selected })
                    .id(selected)  // sin arrastrar lo abierto de otro día
            }.padding(16)
        }
        .background(Palette.bg)
    }
}

private struct DayCell: View {
    let day: CalendarDay
    let selected: Bool
    var body: some View {
        let done = day.done.count + day.manual.count
        VStack(alignment: .leading, spacing: 3) {
            Text("\(day.dayOfMonth)").font(.mono(12, relativeTo: .caption))
                .foregroundStyle(day.inMonth ? Color.primary : Palette.muted)
            HStack(spacing: 1) { ForEach(Array(day.planned.prefix(2).enumerated()), id: \.offset) { _, p in Glyph(name: sportIcon(p.sport), size: 14, color: Palette.muted) } }
            if done > 0 {
                HStack(spacing: 1) {
                    Glyph(name: "check", size: 14, color: Palette.ok).accessibilityLabel("Hecha")
                    if done > 1 { Text("\(done)").font(.mono(11, relativeTo: .caption2)).foregroundStyle(Palette.ok) }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(4)
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .topLeading)
        .background(selected ? Palette.okSoft : day.inMonth ? Palette.surface : Palette.bg)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(day.isToday ? Palette.ok : .clear, lineWidth: 2))
    }
}

private struct DayDetail: View {
    @EnvironmentObject var model: AppModel
    let date: String
    let cell: CalendarDay?

    var body: some View {
        let _ = model.version
        let day = try? model.core.day(date: date)
        VStack(alignment: .leading, spacing: 12) {
            Text(ScreensKt.dateLabel(date: date)).font(.ui(22, .heavy, relativeTo: .title2))
            ForEach(Array((cell?.done ?? []).enumerated()), id: \.offset) { _, a in
                Module(background: Palette.okSoft) {
                    Text(a.title).font(.section)
                    Secondary("\(a.minutes) min" + (a.start.map { " a las \($0)" } ?? ""))
                }
            }
            if let day { DayContent(day: day, linked: model.core.linkedAthlete != nil, today: day.isToday) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }
}

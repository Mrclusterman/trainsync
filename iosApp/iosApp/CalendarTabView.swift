import SwiftUI
import Shared

/// Calendario mensual: el pasado muestra lo que hiciste de verdad y el futuro, el plan (punto 4 de la revisión).
struct CalendarTabView: View {
    @EnvironmentObject var model: AppModel
    @State private var year = Calendar.current.component(.year, from: Date())
    @State private var month = Calendar.current.component(.month, from: Date())
    @State private var selected: String?

    var body: some View {
        let _ = model.version
        let screen = model.core.month(year: Int32(year), month: Int32(month))
        ScrollView {
            VStack(spacing: 10) {
                HStack {
                    Button("‹") { if month == 1 { month = 12; year -= 1 } else { month -= 1 } }.font(.title2)
                    Spacer()
                    Text(screen.title).font(.title3).bold()
                    Spacer()
                    Button("›") { if month == 12 { month = 1; year += 1 } else { month += 1 } }.font(.title2)
                }
                HStack {
                    ForEach(["L", "M", "X", "J", "V", "S", "D"], id: \.self) {
                        Text($0).font(.caption).foregroundStyle(Palette.secondary).frame(maxWidth: .infinity)
                    }
                }
                ForEach(Array(screen.weeks.enumerated()), id: \.offset) { _, week in
                    HStack(spacing: 4) {
                        ForEach(Array(week.enumerated()), id: \.offset) { _, d in
                            DayCell(day: d).onTapGesture { selected = d.date }
                        }
                    }
                }
                Secondary("Punto de color: sesión planificada. ✓: hecha. Toca un día para ver el detalle.")
            }.padding(16)
        }
        .background(Palette.bg)
        .sheet(item: Binding(get: { selected.map { SelectedDay(id: $0) } }, set: { selected = $0?.id })) { sel in
            DaySheet(date: sel.id, cell: screen.weeks.flatMap { $0 }.first { $0.date == sel.id })
                .environmentObject(model)
                .presentationDetents([.medium, .large])
        }
    }
}

private struct SelectedDay: Identifiable { let id: String }

private struct DayCell: View {
    let day: CalendarDay
    var body: some View {
        let done = day.done.count + day.manual.count
        VStack(alignment: .leading, spacing: 3) {
            Text("\(day.dayOfMonth)").font(.system(.caption, design: .monospaced))
                .foregroundStyle(day.inMonth ? Color.primary : Palette.secondary)
            HStack(spacing: 3) { ForEach(Array(day.planned.prefix(3).enumerated()), id: \.offset) { _, p in SportDot(sport: p.sport, size: 7) } }
            if done > 0 { Text(done > 1 ? "✓\(done)" : "✓").font(.caption2).foregroundStyle(Palette.bio) }
            Spacer(minLength: 0)
        }
        .padding(4)
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .topLeading)
        .background(day.inMonth ? Palette.surface1 : Palette.bg)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(day.isToday ? Palette.accent : .clear))
    }
}

private struct DaySheet: View {
    @EnvironmentObject var model: AppModel
    let date: String
    let cell: CalendarDay?

    var body: some View {
        let _ = model.version
        let day = try? model.core.day(date: date)
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(ScreensKt.dateLabel(date: date)).font(.title3).bold()
                ForEach(Array((cell?.done ?? []).enumerated()), id: \.offset) { _, a in
                    Module(accent: Palette.bio) {
                        Text(a.title).font(.headline)
                        Secondary("\(a.minutes) min" + (a.start.map { " a las \($0)" } ?? ""))
                    }
                }
                if let day { DayContent(day: day, linked: model.core.linkedAthlete != nil, today: day.isToday) }
            }.padding(16)
        }.background(Palette.bg)
    }
}

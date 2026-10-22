import Foundation
import UserNotifications
import Shared

/// Recordatorio diario opcional (VISION fase 3). iOS no deja calcular el texto al sonar, así que se programan los
/// próximos 7 días con lo que toca cada uno y se rehacen al abrir la app o tras cada cambio.
enum Reminders {
    static func requestAccess() async throws -> Bool {
        try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
    }

    static func reschedule(_ core: Shared.TrainSyncApp) {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        guard let hour = core.data.reminderHour?.intValue else { return }
        let calendar = Calendar.current
        let now = Date()
        for offset in 0..<7 {
            guard let day = calendar.date(byAdding: .day, value: offset, to: now),
                  let fire = calendar.date(bySettingHour: hour, minute: 0, second: 0, of: day), fire > now else { continue }
            let parts = calendar.dateComponents([.year, .month, .day], from: day)
            let date = String(format: "%04d-%02d-%02d", parts.year!, parts.month!, parts.day!)
            guard let text = try? core.reminderText(date: date) else { continue }  // descanso o ya hecho: no avisa
            let content = UNMutableNotificationContent()
            content.title = "Hoy toca"
            content.body = text
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: calendar.dateComponents([.year, .month, .day, .hour, .minute], from: fire), repeats: false)
            center.add(UNNotificationRequest(identifier: "reminder-\(date)", content: content, trigger: trigger))
        }
    }
}

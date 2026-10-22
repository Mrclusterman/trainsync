import Foundation
import HealthKit
import Shared

/// Lee de Apple Salud lo que usan el plan y Progreso (solo lectura) y lo entrega al núcleo.
@MainActor
final class HealthKitReader {
    static let shared = HealthKitReader()
    private let store = HKHealthStore()

    var available: Bool { HKHealthStore.isHealthDataAvailable() }

    private let quantities: [HKQuantityTypeIdentifier] = [.heartRateVariabilitySDNN, .restingHeartRate, .bodyMass,
                                                          .stepCount, .vo2Max, .heartRate]

    private var readTypes: Set<HKObjectType> {
        var types: Set<HKObjectType> = Set(quantities.map { HKQuantityType($0) })
        types.insert(HKCategoryType(.sleepAnalysis))
        types.insert(HKObjectType.workoutType())
        return types
    }

    func requestAccess() async throws {
        try await store.requestAuthorization(toShare: [], read: readTypes)
    }

    private func samples<T: HKSample>(_ type: HKSampleType, from: Date, to: Date, as _: T.Type) async throws -> [T] {
        let predicate = HKQuery.predicateForSamples(withStart: from, end: to)
        let descriptor = HKSampleQueryDescriptor(predicates: [.sample(type: type, predicate: predicate)],
                                                 sortDescriptors: [SortDescriptor(\.startDate)])
        return try await descriptor.result(for: store).compactMap { $0 as? T }
    }

    private func dayKey(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// Promedio, mínimo o último valor de una magnitud por día.
    private func daily(_ id: HKQuantityTypeIdentifier, unit: HKUnit, from: Date, to: Date,
                       reduce: ([Double]) -> Double) async throws -> [String: Double] {
        let list: [HKQuantitySample] = try await samples(HKQuantityType(id), from: from, to: to, as: HKQuantitySample.self)
        return Dictionary(grouping: list) { dayKey($0.endDate) }.mapValues { reduce($0.map { $0.quantity.doubleValue(for: unit) }) }
    }

    /// Lee los últimos `days` días y los guarda en la app. Lo que no tenga permiso llega vacío.
    func sync(_ core: Shared.TrainSyncApp, days: Int = 365) async throws {
        let to = Date()
        let from = Calendar.current.date(byAdding: .day, value: -days, to: to) ?? to
        let bpm = HKUnit.count().unitDivided(by: .minute())

        let sleepSamples: [HKCategorySample] = try await samples(HKCategoryType(.sleepAnalysis), from: from, to: to, as: HKCategorySample.self)
        let asleep: Set<Int> = [HKCategoryValueSleepAnalysis.asleepUnspecified.rawValue, HKCategoryValueSleepAnalysis.asleepCore.rawValue,
                                HKCategoryValueSleepAnalysis.asleepDeep.rawValue, HKCategoryValueSleepAnalysis.asleepREM.rawValue]
        let sleep = Dictionary(grouping: sleepSamples.filter { asleep.contains($0.value) }) { dayKey($0.endDate) }
            .mapValues { $0.reduce(0.0) { $0 + $1.endDate.timeIntervalSince($1.startDate) / 60 } }
        let hrv = try await daily(.heartRateVariabilitySDNN, unit: .secondUnit(with: .milli), from: from, to: to) { $0.reduce(0, +) / Double($0.count) }
        let resting = try await daily(.restingHeartRate, unit: bpm, from: from, to: to) { $0.min() ?? 0 }
        let weight = try await daily(.bodyMass, unit: .gramUnit(with: .kilo), from: from, to: to) { $0.last ?? 0 }
        let steps = try await daily(.stepCount, unit: .count(), from: from, to: to) { $0.reduce(0, +) }
        let vo2 = try await daily(.vo2Max, unit: HKUnit(from: "ml/kg*min"), from: from, to: to) { $0.last ?? 0 }

        let keys = Set(sleep.keys).union(hrv.keys).union(resting.keys).union(weight.keys).union(steps.keys).union(vo2.keys).sorted()
        func k(_ v: Double?) -> KotlinDouble? { v.map { KotlinDouble(double: $0) } }
        let dailies = keys.map { d in
            DailyHealth(date: d, sleepMinutes: k(sleep[d]), hrv: k(hrv[d]), restingHr: k(resting[d]), weightKg: k(weight[d]),
                        steps: k(steps[d]), vo2max: k(vo2[d]))
        }

        let heart: [HKQuantitySample] = try await samples(HKQuantityType(.heartRate), from: from, to: to, as: HKQuantitySample.self)
        let beats = heart.map { ($0.startDate, $0.quantity.doubleValue(for: bpm)) }
        let measuredMax = beats.map { $0.1 }.max()
        let bounds = core.zoneBounds(measuredMaxHr: measuredMax.map { KotlinDouble(double: $0) }).map { $0.doubleValue }
        let local = DateFormatter()
        local.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        local.locale = Locale(identifier: "en_US_POSIX")

        let workouts: [HKWorkout] = try await samples(HKObjectType.workoutType(), from: from, to: to, as: HKWorkout.self)
        let converted: [Shared.Workout] = workouts.compactMap { w in
            guard let sport = Self.sport(w.workoutActivityType) else { return nil }
            let inside = beats.filter { $0.0 >= w.startDate && $0.0 <= w.endDate }
            // Tiempo en cada zona: cada pulso cuenta hasta el siguiente (como mucho 10 s, para no inventar huecos)
            var zones = [Double](repeating: 0, count: 7)
            for (a, b) in zip(inside, inside.dropFirst()) {
                let secs = min(b.0.timeIntervalSince(a.0), 10)
                let zone = bounds.firstIndex { a.1 < $0 } ?? 6
                zones[zone] += secs
            }
            let avg = inside.isEmpty ? nil : inside.map { $0.1 }.reduce(0, +) / Double(inside.count)
            return Shared.Workout(id: w.uuid.uuidString, sport: sport, start: local.string(from: w.startDate),
                                  minutes: w.duration / 60, avgHr: k(avg), maxHr: k(inside.map { $0.1 }.max()),
                                  zoneSeconds: zones.reduce(0, +) > 0 ? zones.map { KotlinDouble(double: $0) } : nil)
        }
        core.importHealth(snapshot: HealthSnapshot(source: "Apple Salud", days: dailies, workouts: converted,
                                                   syncedAt: dayKey(Date())))
    }

    private static func sport(_ type: HKWorkoutActivityType) -> String? {
        switch type {
        case .running: return "running"
        case .cycling: return "cycling"
        case .swimming: return "swimming_pool"
        case .walking, .hiking: return "walking"
        case .traditionalStrengthTraining, .functionalStrengthTraining: return "strength"
        default: return nil
        }
    }
}

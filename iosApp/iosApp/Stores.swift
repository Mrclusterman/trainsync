import Foundation
import Security
import Shared

/// Datos de la app en UserDefaults: solo en el móvil.
final class DefaultsStore: KeyValueStore {
    private let defaults = UserDefaults.standard

    func read(key: String) -> String? { defaults.string(forKey: key) }

    func write(key: String, value: String?) {
        if let value { defaults.set(value, forKey: key) } else { defaults.removeObject(forKey: key) }
    }
}

/// La clave de intervals.icu en el Llavero de iOS, solo en este dispositivo y sin copia en iCloud.
final class KeychainStore: KeyValueStore {
    private let service = "io.github.mrclusterman.trainsync"

    private func query(_ key: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: key]
    }

    func read(key: String) -> String? {
        var q = query(key)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func write(key: String, value: String?) {
        SecItemDelete(query(key) as CFDictionary)
        guard let value else { return }
        var q = query(key)
        q[kSecValueData as String] = Data(value.utf8)
        q[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }
}

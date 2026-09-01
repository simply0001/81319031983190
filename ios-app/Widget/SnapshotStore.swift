import Foundation
import UIKit

/// Mirrors `WidgetSnapshot` in shared/src/commonMain/.../widget/WidgetSnapshot.kt.
/// Field names are the wire contract; keep them in sync.
struct WidgetSnapshot: Codable, Equatable {
    var version: Int = 1
    var signedIn: Bool
    var displayName: String
    var bio: String
    var portraitFileName: String?
    var avatarBundledKey: String?
    var encountersToday: Int
    var lastEncounterEpochMillis: Int64?
    var nearbyStatus: String
    var unreadNotifications: Int
    var friendsOnline: Int
    var themeMode: String
    var updatedAtEpochMillis: Int64
}

enum SnapshotStore {
    static let defaultGroupIdentifier = "group.xyz.pocketpass"

    /// The app group the signing tool actually granted, read from the embedded
    /// provisioning profile (same discovery as the Kotlin sink), so that a
    /// rewritten identifier keeps app and widget on the same container.
    static func groupIdentifier() -> String {
        guard
            let path = Bundle.main.path(forResource: "embedded", ofType: "mobileprovision"),
            let data = FileManager.default.contents(atPath: path)
        else { return defaultGroupIdentifier }
        let text = String(decoding: data, as: UTF8.self)
        guard let keyRange = text.range(of: "<key>com.apple.security.application-groups</key>") else {
            return defaultGroupIdentifier
        }
        let tail = text[keyRange.upperBound...]
        guard
            let open = tail.range(of: "<string>"),
            let close = tail[open.upperBound...].range(of: "</string>")
        else { return defaultGroupIdentifier }
        let value = tail[open.upperBound..<close.lowerBound].trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? defaultGroupIdentifier : value
    }

    static func widgetDirectory() -> URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: groupIdentifier())?
            .appendingPathComponent("widget", isDirectory: true)
    }

    static func load() -> WidgetSnapshot? {
        guard let directory = widgetDirectory() else { return nil }
        let file = directory.appendingPathComponent("snapshot.json")
        guard let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    static func portrait(for snapshot: WidgetSnapshot?) -> UIImage? {
        guard
            let name = snapshot?.portraitFileName,
            let directory = widgetDirectory()
        else { return nil }
        return UIImage(contentsOfFile: directory.appendingPathComponent(name).path)
    }
}

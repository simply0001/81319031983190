import UIKit
import WidgetKit

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    let portrait: UIImage?

    static let placeholder = SnapshotEntry(
        date: Date(),
        snapshot: WidgetSnapshot(
            signedIn: true,
            displayName: "Petah",
            bio: "Hello! Nice to meet you!",
            portraitFileName: nil,
            avatarBundledKey: "petah",
            encountersToday: 3,
            lastEncounterEpochMillis: Int64(Date().timeIntervalSince1970 * 1000) - 25 * 60 * 1000,
            nearbyStatus: "Running",
            unreadNotifications: 2,
            friendsOnline: 1,
            themeMode: "System",
            updatedAtEpochMillis: Int64(Date().timeIntervalSince1970 * 1000)
        ),
        portrait: nil
    )
}

struct SnapshotTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        SnapshotEntry.placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(context.isPreview ? SnapshotEntry.placeholder : current())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let entry = current()
        // The app reloads timelines on every change; this keeps relative
        // times ("Last pass 12m ago") moving when nothing else happens.
        let refresh = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date()
        completion(Timeline(entries: [entry], policy: .after(refresh)))
    }

    private func current() -> SnapshotEntry {
        let snapshot = SnapshotStore.load()
        return SnapshotEntry(
            date: Date(),
            snapshot: snapshot,
            portrait: SnapshotStore.portrait(for: snapshot)
        )
    }
}

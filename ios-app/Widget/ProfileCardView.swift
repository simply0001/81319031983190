import SwiftUI
import WidgetKit

struct ProfileCardView: View {
    let entry: SnapshotEntry
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetFamily) private var family

    var body: some View {
        let palette = PocketPalette.resolve(themeMode: entry.snapshot?.themeMode, systemScheme: scheme)
        Group {
            if let snapshot = entry.snapshot, snapshot.signedIn {
                content(snapshot, palette)
            } else {
                PlaceholderView(palette: palette)
            }
        }
        .pocketCard(palette)
        .widgetURL(URL(string: "pocketpass://home"))
    }

    private func content(_ snapshot: WidgetSnapshot, _ palette: PocketPalette) -> some View {
        let large = family == .systemLarge
        let portraitSize: CGFloat = large ? 150 : 92
        let counter: String
        switch snapshot.encountersToday {
        case 0: counter = "No encounters yet today"
        case 1: counter = "1 encounter today"
        default: counter = "\(snapshot.encountersToday) encounters today"
        }
        return HStack(alignment: .center, spacing: 14) {
            PortraitFrame(image: entry.portrait, palette: palette, size: portraitSize)
            VStack(alignment: .leading, spacing: 6) {
                Text(snapshot.displayName.isEmpty ? "PocketPass" : snapshot.displayName)
                    .font(PocketFont.rubik(large ? 26 : 20, weight: .heavy))
                    .foregroundStyle(palette.teal)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .shadow(color: .black.opacity(0.16), radius: 4, x: 1, y: 2)
                Text(snapshot.bio)
                    .font(PocketFont.rubik(large ? 15 : 13))
                    .foregroundStyle(palette.tealSoft)
                    .lineLimit(large ? 4 : 2)
                HStack(spacing: 6) {
                    Circle().fill(palette.teal).frame(width: 7, height: 7)
                    Text(counter)
                        .font(PocketFont.rubik(12))
                        .foregroundStyle(palette.textMuted)
                        .lineLimit(1)
                }
                .padding(.top, 4)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
    }
}

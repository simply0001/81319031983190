import SwiftUI
import WidgetKit

struct StreetPassSummaryView: View {
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
        let wide = family != .systemSmall
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Image("friend_wave")
                        .resizable()
                        .scaledToFit()
                        .frame(width: wide ? 30 : 26, height: wide ? 30 : 26)
                        .alignmentGuide(.firstTextBaseline) { $0[.bottom] }
                    Text("\(snapshot.encountersToday)")
                        .font(PocketFont.rubik(wide ? 44 : 40))
                        .foregroundStyle(palette.teal)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }
                Spacer(minLength: 8)
                NearbyPill(status: snapshot.nearbyStatus, palette: palette)
            }
            Text(snapshot.encountersToday == 1 ? "encounter today" : "encounters today")
                .font(PocketFont.rubik(12, weight: .medium))
                .foregroundStyle(palette.textMuted)
            Spacer(minLength: 6)
            HStack(spacing: 12) {
                Text(LastPass.label(lastEpochMillis: snapshot.lastEncounterEpochMillis, now: entry.date))
                    .font(PocketFont.rubik(12))
                    .foregroundStyle(palette.tealSoft)
                    .lineLimit(1)
                if wide {
                    Spacer(minLength: 4)
                    stat("\(snapshot.unreadNotifications) unread", dot: PocketPalette.accentRed, palette)
                    stat("\(snapshot.friendsOnline) online", dot: palette.teal, palette)
                }
            }
        }
        .padding(14)
    }

    private func stat(_ text: String, dot: Color, _ palette: PocketPalette) -> some View {
        HStack(spacing: 6) {
            Circle().fill(dot).frame(width: 7, height: 7)
            Text(text)
                .font(PocketFont.rubik(13))
                .foregroundStyle(palette.textPrimary)
                .lineLimit(1)
        }
    }
}

struct PlaceholderView: View {
    let palette: PocketPalette

    var body: some View {
        VStack(spacing: 6) {
            Text("Open PocketPass")
                .font(PocketFont.rubik(15))
                .foregroundStyle(palette.teal)
            Text("to sync your widget")
                .font(PocketFont.rubik(11, weight: .medium))
                .foregroundStyle(palette.textMuted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(14)
    }
}

import SwiftUI
import WidgetKit

@main
struct PocketPassWidgetBundle: WidgetBundle {
    var body: some Widget {
        StreetPassSummaryWidget()
        ProfileCardWidget()
    }
}

struct StreetPassSummaryWidget: Widget {
    let kind = "xyz.pocketpass.widget.summary"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SnapshotTimelineProvider()) { entry in
            StreetPassSummaryView(entry: entry)
        }
        .configurationDisplayName("Street-pass")
        .description("Encounters today, your last pass and who's around.")
        .supportedFamilies([.systemSmall, .systemMedium])
        .contentMarginsDisabled()
    }
}

struct ProfileCardWidget: Widget {
    let kind = "xyz.pocketpass.widget.profile"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SnapshotTimelineProvider()) { entry in
            ProfileCardView(entry: entry)
        }
        .configurationDisplayName("Profile card")
        .description("Your Mii, name and greeting, PocketPass style.")
        .supportedFamilies([.systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

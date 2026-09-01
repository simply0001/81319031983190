import SwiftUI
import WidgetKit

/// Swift copy of the design tokens in
/// ui/src/commonMain/kotlin/com/pocketpass/app/ui/theme/PocketPalette.kt
/// (LightPalette / DarkPalette). When those change, change these.
struct PocketPalette {
    let surface: Color
    let surfaceLower: Color
    let borderSoft: Color
    let tealBorder: Color
    let teal: Color
    let tealSoft: Color
    let textPrimary: Color
    let textMuted: Color
    let shadowAlpha: Double
    /// Home backdrop, bottom screen: top colour is held for the first third.
    let backdropTop: Color
    let backdropBottom: Color

    static let light = PocketPalette(
        surface: .white,
        surfaceLower: Color(hex: 0xD9D9D9),
        borderSoft: Color(hex: 0xD9D9D9),
        tealBorder: Color(hex: 0x5E9AAC),
        teal: Color(hex: 0x1D596B),
        tealSoft: Color(hex: 0x26706A),
        textPrimary: Color(hex: 0x5C5C5C),
        textMuted: Color(hex: 0x8A8A8A),
        shadowAlpha: 0.32,
        backdropTop: Color(hex: 0xE9F6F4),
        backdropBottom: Color(hex: 0x92EBAE)
    )

    static let dark = PocketPalette(
        surface: Color(hex: 0x1F2A31),
        surfaceLower: Color(hex: 0x131C22),
        borderSoft: Color(hex: 0x34434C),
        tealBorder: Color(hex: 0x6FA9BC),
        teal: Color(hex: 0xA6D8E8),
        tealSoft: Color(hex: 0x8FC6C0),
        textPrimary: Color(hex: 0xE8EEF1),
        textMuted: Color(hex: 0x8A9BA3),
        shadowAlpha: 0.32,
        backdropTop: Color(hex: 0x16242A),
        backdropBottom: Color(hex: 0x1B4A34)
    )

    static func resolve(themeMode: String?, systemScheme: ColorScheme) -> PocketPalette {
        switch themeMode {
        case "Dark": return .dark
        case "Light": return .light
        default: return systemScheme == .dark ? .dark : .light
        }
    }

    static let onlineGreen = Color(hex: 0x51FF85)
    static let accentRed = Color(hex: 0xE25757)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// Rubik, bundled with the extension; falls back to the system font when the
/// face is unavailable so the widget never renders empty text.
enum PocketFont {
    static func rubik(_ size: CGFloat, weight: Font.Weight = .semibold) -> Font {
        if UIFont.familyNames.contains(where: { $0.caseInsensitiveCompare("Rubik") == .orderedSame }) {
            return .custom("Rubik", size: size).weight(weight)
        }
        return .system(size: size, weight: weight, design: .rounded)
    }
}

extension View {
    /// The card surface: pocketFrame stroke on the Home backdrop gradient,
    /// with pocketShadow's soft drop shadow.
    func pocketCard(_ palette: PocketPalette) -> some View {
        self.modifier(PocketCardBackground(palette: palette))
    }
}

private struct PocketCardBackground: ViewModifier {
    let palette: PocketPalette
    private let radius: CGFloat = 18
    private let frame: CGFloat = 2.5

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        let gradient = LinearGradient(
            stops: [
                .init(color: palette.backdropTop, location: 0),
                .init(color: palette.backdropTop, location: 0.35),
                .init(color: palette.backdropBottom, location: 1),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        let backdrop = shape
            .fill(gradient)
            .overlay(shape.strokeBorder(palette.borderSoft, lineWidth: frame))
            .shadow(color: .black.opacity(palette.shadowAlpha * 0.6), radius: 6, y: 2)
        if #available(iOS 17.0, *) {
            return AnyView(content.containerBackground(for: .widget) { backdrop.padding(2) })
        } else {
            return AnyView(content.background(backdrop.padding(2)))
        }
    }
}

struct NearbyPill: View {
    let status: String
    let palette: PocketPalette

    private var label: String {
        switch status {
        case "Running": return "Nearby on"
        case "BluetoothOff": return "Bluetooth off"
        case "NeedsPermissions", "NeedsOnboarding": return "Nearby setup"
        default: return "Nearby off"
        }
    }

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(status == "Running" ? PocketPalette.onlineGreen : palette.textMuted)
                .frame(width: 7, height: 7)
            Text(label)
                .font(PocketFont.rubik(11))
                .foregroundStyle(palette.textPrimary)
                .lineLimit(1)
        }
        .padding(.horizontal, 9)
        .frame(height: 22)
        .background(Capsule().fill(palette.surface))
        .overlay(Capsule().strokeBorder(palette.borderSoft, lineWidth: 1.5))
    }
}

struct PortraitFrame: View {
    let image: UIImage?
    let palette: PocketPalette
    let size: CGFloat

    var body: some View {
        // Border proportion follows PhoneAvatarFrame: 22 / 449 of the diameter.
        let border = size * 22 / 449
        ZStack {
            Circle()
                .fill(palette.surface)
                .shadow(color: .black.opacity(palette.shadowAlpha), radius: 5, y: size * 0.03)
            Group {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                } else {
                    Image("home_avatar_petah")
                        .resizable()
                        .scaledToFit()
                }
            }
            .frame(width: size - 2 * border - 2, height: size - 2 * border - 2)
            .background(Circle().fill(.white))
            .clipShape(Circle())
            Circle()
                .strokeBorder(palette.tealBorder, lineWidth: border)
        }
        .frame(width: size, height: size)
    }
}

enum LastPass {
    static func label(lastEpochMillis: Int64?, now: Date = Date()) -> String {
        guard let last = lastEpochMillis else { return "No passes yet" }
        let elapsed = max(0, Int64(now.timeIntervalSince1970 * 1000) - last)
        let minutes = elapsed / 60_000
        let hours = elapsed / 3_600_000
        let days = elapsed / 86_400_000
        switch true {
        case minutes < 1: return "Last pass just now"
        case minutes < 60: return "Last pass \(minutes)m ago"
        case hours < 24: return "Last pass \(hours)h ago"
        case days == 1: return "Last pass yesterday"
        default: return "Last pass \(days) days ago"
        }
    }
}

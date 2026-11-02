import AppKit
import SwiftUI

/// Palette and type shared by every screen, mirroring the Android app.
enum Palette {
    static let ink = Color(hex: 0x080A0D)
    static let surface = Color(hex: 0x12161C)
    static let surfaceHigh = Color(hex: 0x19202A)
    static let surfaceHigher = Color(hex: 0x212935)

    static let amber = Color(hex: 0xFFC24B)
    static let amberSoft = Color(hex: 0xFFE2A3)
    static let teal = Color(hex: 0x6FE3C9)
    static let textPrimary = Color(hex: 0xEEF2F7)
    static let textMuted = Color(hex: 0x8B98A9)
    static let hairline = Color(hex: 0x232B36)
    static let danger = Color(hex: 0xFF7A6B)

    /// Text/icon colour used on top of the amber accent.
    static let onAmber = Color(hex: 0x241804)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }

    static func blend(_ from: Color, _ to: Color, amount: Double) -> Color {
        let t = min(1, max(0, amount))
        guard
            let a = NSColor(from).usingColorSpace(.sRGB),
            let b = NSColor(to).usingColorSpace(.sRGB)
        else { return to }
        return Color(
            .sRGB,
            red: a.redComponent + (b.redComponent - a.redComponent) * t,
            green: a.greenComponent + (b.greenComponent - a.greenComponent) * t,
            blue: a.blueComponent + (b.blueComponent - a.blueComponent) * t,
            opacity: a.alphaComponent + (b.alphaComponent - a.alphaComponent) * t
        )
    }
}

extension Font {
    /// Tabular/monospaced numerals so the displays never wobble.
    static func numeral(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight, design: .monospaced)
    }

    static func label(_ size: CGFloat = 11, weight: Font.Weight = .medium) -> Font {
        .system(size: size, weight: weight)
    }
}

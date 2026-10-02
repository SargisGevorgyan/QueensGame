//
//  Theme.swift
//  QueensGame
//
//  Adaptive colour tokens (light / dark) and a couple of reusable modifiers.
//  Region pastels mirror the web build so the two clones read as one family.
//

import SwiftUI
import UIKit

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }

    /// A light/dark adaptive colour built from two hex values.
    init(hexLight: UInt32, hexDark: UInt32) {
        self = Color(uiColor: UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? hexDark : hexLight
            return UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255,
                           green: CGFloat((hex >> 8) & 0xFF) / 255,
                           blue: CGFloat(hex & 0xFF) / 255,
                           alpha: 1)
        })
    }
}

enum QColor {
    static let bg       = Color(hexLight: 0xEEF0F7, hexDark: 0x121320)
    static let surface  = Color(hexLight: 0xFFFFFF, hexDark: 0x1D1F2E)
    static let surface2 = Color(hexLight: 0xF4F5FB, hexDark: 0x252739)
    static let ink      = Color(hexLight: 0x1B1D2A, hexDark: 0xECEDF6)
    static let muted    = Color(hexLight: 0x6A6F82, hexDark: 0x9BA1B7)
    static let faint    = Color(hexLight: 0x9AA0B3, hexDark: 0x727790)
    static let line     = Color(hexLight: 0xE2E4EF, hexDark: 0x2C2E42)
    static let wall     = Color(hexLight: 0x23263A, hexDark: 0xC9CCE6)
    static let accent   = Color(hexLight: 0x5B4BD6, hexDark: 0x9184FF)
    static let gold     = Color(hexLight: 0xD99A17, hexDark: 0xEFB437)
    static let danger   = Color(hexLight: 0xDF4750, hexDark: 0xF0575F)
    static let ok       = Color(hexLight: 0x2E9E5B, hexDark: 0x41B671)
    static let fog      = Color(hexLight: 0xDCDEE9, hexDark: 0x2A2C40)
    static let guardBG  = Color(hexLight: 0xD5D7E4, hexDark: 0x31344A)

    private static let regionLight: [UInt32] =
        [0xF3C6C8, 0xF6E2A9, 0xC7E7C1, 0xB9D7F1, 0xDCCEF2, 0xF6D3B4, 0xB5E4DD, 0xDCDEEE, 0xE6ECAB]
    private static let regionDark: [UInt32] =
        [0x7F4A4D, 0x7F6A35, 0x4D6E49, 0x3F5C7D, 0x5C4F81, 0x875F3D, 0x3D6E69, 0x474B62, 0x6A723D]

    static func region(_ slot: Int) -> Color {
        let i = ((slot % 9) + 9) % 9
        return Color(hexLight: regionLight[i], hexDark: regionDark[i])
    }
}

// MARK: - Modifiers

private struct CardBackground: ViewModifier {
    var cornerRadius: CGFloat
    func body(content: Content) -> some View {
        content
            .background(QColor.surface, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(QColor.line, lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.06), radius: 14, y: 8)
    }
}

extension View {
    func card(cornerRadius: CGFloat = 16) -> some View {
        modifier(CardBackground(cornerRadius: cornerRadius))
    }
}

//
//  Haptics.swift
//  QueensGame
//
//  Thin wrapper around UIFeedbackGenerator.
//

import UIKit

@MainActor
final class Haptics {
    static let shared = Haptics()

    private let impact = UIImpactFeedbackGenerator(style: .medium)
    private let notification = UINotificationFeedbackGenerator()

    private init() {
        impact.prepare()
        notification.prepare()
    }

    /// `intensity` is 0...1.
    func tap(intensity: CGFloat = 0.9) {
        impact.impactOccurred(intensity: intensity)
        impact.prepare()
    }

    func notify(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        notification.notificationOccurred(type)
        notification.prepare()
    }
}

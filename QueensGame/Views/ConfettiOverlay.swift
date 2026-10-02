//
//  ConfettiOverlay.swift
//  QueensGame
//
//  A self-contained confetti burst drawn with Canvas. Particles are
//  regenerated deterministically each frame from a fixed seed, so no
//  per-particle state is stored. Skipped under Reduce Motion.
//

import SwiftUI

struct ConfettiOverlay: View {
    let isActive: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var startedAt: Date?

    private let palette: [Color] = [QColor.accent, QColor.gold,
                                    QColor.region(0), QColor.region(2), QColor.region(3)]
    private let duration: Double = 3.6
    private let count = 150

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                guard let startedAt else { return }
                let t = timeline.date.timeIntervalSince(startedAt)
                guard t <= duration else { return }

                var rng = SeededRNG(seed: 0x5EED_C0FF_EE15_600D)
                for _ in 0..<count {
                    let x0 = Double.random(in: 0...max(size.width, 1), using: &rng)
                    let vx = Double.random(in: -45 ... 45, using: &rng)
                    let vy = Double.random(in: 130 ... 340, using: &rng)
                    let delay = Double.random(in: 0 ... 0.45, using: &rng)
                    let spin = Double.random(in: -6 ... 6, using: &rng)
                    let colorIndex = Int(Double.random(in: 0..<Double(palette.count), using: &rng))
                    let w = Double.random(in: 5 ... 9, using: &rng)

                    let lt = max(0, t - delay)
                    let x = x0 + vx * lt
                    let y = -20 + vy * lt + 92 * lt * lt
                    guard y < size.height + 40 else { continue }
                    let alpha = max(0, 1 - lt / (duration - 0.4))

                    context.drawLayer { layer in
                        layer.translateBy(x: x, y: y)
                        layer.rotate(by: .radians(spin * lt))
                        layer.fill(
                            Path(CGRect(x: -w / 2, y: -w * 0.7, width: w, height: w * 1.4)),
                            with: .color(palette[colorIndex].opacity(alpha))
                        )
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
        .onChange(of: isActive) { _, active in
            startedAt = (active && !reduceMotion) ? Date() : nil
        }
    }
}

extension View {
    func confetti(isActive: Bool) -> some View {
        overlay(ConfettiOverlay(isActive: isActive))
    }
}

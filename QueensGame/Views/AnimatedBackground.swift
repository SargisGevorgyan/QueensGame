//
//  AnimatedBackground.swift
//  QueensGame
//
//  A calm ambient backdrop — two slow drifting colour washes over the base
//  tint. Honours Reduce Motion.
//

import SwiftUI

struct AnimatedBackground: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drift = false

    var body: some View {
        ZStack {
            QColor.bg

            Circle()
                .fill(QColor.accent.opacity(0.16))
                .frame(width: 340)
                .blur(radius: 90)
                .offset(x: drift ? -130 : -70, y: drift ? -240 : -180)

            Circle()
                .fill(QColor.gold.opacity(0.12))
                .frame(width: 320)
                .blur(radius: 100)
                .offset(x: drift ? 150 : 90, y: drift ? 300 : 360)
        }
        .ignoresSafeArea()
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 14).repeatForever(autoreverses: true)) {
                drift = true
            }
        }
    }
}

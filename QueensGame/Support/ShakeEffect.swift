//
//  ShakeEffect.swift
//  QueensGame
//
//  A horizontal shake `GeometryEffect`, played once whenever a token changes.
//

import SwiftUI

struct ShakeEffect: GeometryEffect {
    var travelDistance: CGFloat = 7
    var shakesPerUnit: CGFloat = 3
    var animatableData: CGFloat

    func effectValue(size: CGSize) -> ProjectionTransform {
        let dx = travelDistance * sin(animatableData * .pi * shakesPerUnit)
        return ProjectionTransform(CGAffineTransform(translationX: dx, y: 0))
    }
}

extension View {
    /// Plays one shake whenever `token` changes to a non-zero value.
    func shake(token: Int) -> some View {
        modifier(ShakeOnChange(token: token))
    }
}

private struct ShakeOnChange: ViewModifier {
    let token: Int
    @State private var progress: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .modifier(ShakeEffect(animatableData: progress))
            .onChange(of: token) { _, newValue in
                guard newValue != 0 else { progress = 0; return }
                progress = 0
                withAnimation(.linear(duration: 0.45)) { progress = 1 }
            }
    }
}

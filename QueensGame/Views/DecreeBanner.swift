//
//  DecreeBanner.swift
//  QueensGame
//
//  The herald's card — shows the decree in force during a Royal Decrees round.
//

import SwiftUI

struct DecreeBanner: View {
    @EnvironmentObject private var vm: QueensGameViewModel

    var body: some View {
        if let decree = vm.currentDecree {
            HStack(spacing: 12) {
                Image(systemName: decree.systemImage)
                    .font(.title3)
                    .foregroundStyle(QColor.accent)
                    .frame(width: 36, height: 36)
                    .background(QColor.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(vm.speedDecrees ? "DECREE · ROTATING" : "DECREE IN FORCE")
                        .font(.system(size: 10, weight: .bold))
                        .tracking(1.4)
                        .foregroundStyle(QColor.accent)
                    Text(decree.title)
                        .font(.system(.headline, design: .serif))
                        .foregroundStyle(QColor.ink)
                    Text(decree.blurb)
                        .font(.caption)
                        .foregroundStyle(QColor.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(QColor.accent.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(QColor.accent.opacity(0.30), lineWidth: 1)
            )
            .id(decree)
            .transition(.asymmetric(
                insertion: .move(edge: .top).combined(with: .opacity),
                removal: .opacity
            ))
        }
    }
}

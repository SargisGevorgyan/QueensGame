//
//  RealmChips.swift
//  QueensGame
//
//  A scrolling strip of realm chips (Roman numerals) that tick off when a
//  realm holds exactly one conflict-free Queen.
//

import SwiftUI

struct RealmChips: View {
    @EnvironmentObject private var vm: QueensGameViewModel

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(0..<vm.puzzle.size, id: \.self) { realm in
                    chip(realm)
                }
            }
            .padding(.horizontal, 2)
        }
    }

    private func chip(_ realm: Int) -> some View {
        let done = vm.analysis.regionDone[realm]
        let hidden = vm.currentDecree == .fogOfWar && !vm.isRevealed(realm)
        return HStack(spacing: 5) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(hidden ? QColor.fog : QColor.region(vm.puzzle.colorMap[realm]))
                .frame(width: 13, height: 13)
                .overlay(RoundedRectangle(cornerRadius: 4).strokeBorder(.black.opacity(0.12)))
            Text(hidden ? "?" : roman(realm + 1))
                .font(.system(size: 12, weight: .medium, design: .monospaced))
                .strikethrough(done, color: QColor.faint)
                .foregroundStyle(done ? QColor.faint : QColor.ink)
            if done {
                Image(systemName: "checkmark")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(QColor.ok)
            }
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 5)
        .background(QColor.surface2, in: Capsule())
        .overlay(Capsule().stroke(QColor.line, lineWidth: 1))
        .animation(.easeOut(duration: 0.2), value: done)
    }
}

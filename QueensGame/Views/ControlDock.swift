//
//  ControlDock.swift
//  QueensGame
//
//  Bottom bar: timer + move counter on the left, board actions on the right.
//

import SwiftUI

struct ControlDock: View {
    @EnvironmentObject private var vm: QueensGameViewModel

    private var timeString: String {
        let total = Int(vm.elapsed)
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    var body: some View {
        HStack(spacing: 12) {
            stat("TIME", timeString)
            stat("MOVES", "\(vm.moves)")

            Spacer(minLength: 8)

            dockButton("arrow.uturn.backward", label: "Undo") { vm.undo() }
                .disabled(!vm.canUndo)
            dockButton("arrow.uturn.forward", label: "Redo") { vm.redo() }
                .disabled(!vm.canRedo)
            dockButton("trash", label: "Clear board") { vm.clearBoard() }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .card(cornerRadius: 16)
    }

    private func stat(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(title)
                .font(.system(size: 9, weight: .bold))
                .tracking(1.1)
                .foregroundStyle(QColor.muted)
            Text(value)
                .font(.system(.title3, design: .monospaced))
                .fontWeight(.semibold)
                .monospacedDigit()
                .foregroundStyle(QColor.ink)
        }
        .frame(minWidth: 52, alignment: .leading)
    }

    private func dockButton(_ icon: String, label: String, active: Bool = false,
                            action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 40, height: 40)
                .background(active ? QColor.accent.opacity(0.16) : QColor.surface2,
                           in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .stroke(active ? QColor.accent.opacity(0.5) : QColor.line, lineWidth: 1)
                )
                .foregroundStyle(active ? QColor.accent : QColor.ink)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

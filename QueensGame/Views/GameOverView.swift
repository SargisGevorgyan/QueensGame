//
//  GameOverView.swift
//  QueensGame
//
//  The victory overlay.
//

import SwiftUI

struct GameOverView: View {
    @EnvironmentObject private var vm: QueensGameViewModel

    private var timeString: String {
        let total = Int(vm.elapsed)
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.5).ignoresSafeArea()
                .onTapGesture { dismiss() }

            VStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(QColor.gold.gradient)
                        .frame(width: 66, height: 66)
                        .shadow(color: QColor.gold.opacity(0.5), radius: 16, y: 6)
                    Image(systemName: "crown.fill")
                        .font(.system(size: 30))
                        .foregroundStyle(.white)
                }

                VStack(spacing: 4) {
                    Text("Long live the realm")
                        .font(.system(.title, design: .serif))
                        .foregroundStyle(QColor.ink)
                        .multilineTextAlignment(.center)
                    Text(vm.roundTag)
                        .font(.subheadline)
                        .foregroundStyle(QColor.muted)
                }

                HStack(spacing: 10) {
                    resultTile("Time", timeString)
                    resultTile("Moves", "\(vm.moves)")
                    resultTile("Board", "\(vm.puzzle.size)×\(vm.puzzle.size)")
                }

                HStack(spacing: 10) {
                    Button {
                        dismiss()
                        vm.startNewGame(daily: false)
                    } label: {
                        Text("New puzzle").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)

                    Button {
                        dismiss()
                    } label: {
                        Text("Review").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .controlSize(.large)
            }
            .padding(24)
            .frame(maxWidth: 360)
            .card(cornerRadius: 22)
            .padding(28)
        }
        .transition(.opacity.combined(with: .scale(scale: 0.92)))
    }

    private func dismiss() {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
            vm.showGameOver = false
        }
    }

    private func resultTile(_ title: String, _ value: String) -> some View {
        VStack(spacing: 2) {
            Text(title.uppercased())
                .font(.system(size: 9, weight: .bold))
                .tracking(1)
                .foregroundStyle(QColor.muted)
            Text(value)
                .font(.system(.title3, design: .monospaced))
                .fontWeight(.semibold)
                .foregroundStyle(QColor.ink)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(QColor.surface2, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

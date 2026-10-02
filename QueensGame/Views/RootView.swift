//
//  RootView.swift
//  QueensGame
//

import SwiftUI

struct RootView: View {
    @EnvironmentObject private var vm: QueensGameViewModel
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            AnimatedBackground()

            GeometryReader { geo in
                let boardSide = min(geo.size.width - 32, geo.size.height * 0.46, 460)

                ScrollView {
                    VStack(spacing: 14) {
                        header

                        Picker("Mode", selection: $vm.mode) {
                            ForEach(GameMode.allCases) { Text($0.title).tag($0) }
                        }
                        .pickerStyle(.segmented)

                        if vm.currentDecree != nil {
                            DecreeBanner()
                        }

                        BoardArea(side: boardSide)
                            .frame(maxWidth: .infinity)

                        statusLine

                        RealmChips()

                        ControlDock()
                    }
                    .padding(16)
                    .frame(minHeight: geo.size.height)
                    .frame(maxWidth: 520)
                    .frame(maxWidth: .infinity)
                    .animation(.spring(response: 0.4, dampingFraction: 0.85), value: vm.currentDecree)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
        }
        .foregroundStyle(QColor.ink)
        .confetti(isActive: vm.isSolved)
        .overlay {
            if vm.showGameOver { GameOverView() }
        }
        .sheet(isPresented: $vm.showHowToPlay) { HowToPlayView() }
        .preferredColorScheme(colorScheme)
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:      vm.resumeTimer()
            case .inactive,
                 .background:  vm.pauseTimer()
            @unknown default:  break
            }
        }
    }

    private var colorScheme: ColorScheme? {
        switch vm.theme {
        case .system: return nil
        case .light:  return .light
        case .dark:   return .dark
        }
    }

    // MARK: - Pieces

    private var header: some View {
        HStack(spacing: 8) {
            Image(systemName: "crown.fill")
                .foregroundStyle(QColor.gold)
                .font(.title3)
            Text("Queens & Decrees")
                .font(.system(.title3, design: .serif))
                .fontWeight(.semibold)

            Spacer()

            autoXToggle

            Menu {
                Button {
                    vm.startNewGame(daily: false)
                } label: { Label("New random puzzle", systemImage: "die.face.5") }

                Button {
                    vm.startNewGame(daily: true)
                } label: { Label(vm.dailyLabel + " puzzle", systemImage: "calendar") }

                Divider()

                Picker("Board size", selection: $vm.size) {
                    ForEach([6, 7, 8, 9], id: \.self) { Text("\($0)×\($0)").tag($0) }
                }

                Picker("Theme", selection: $vm.theme) {
                    ForEach(AppTheme.allCases) { Text($0.label).tag($0) }
                }

                if vm.mode == .decrees {
                    Toggle(isOn: $vm.speedDecrees) {
                        Label("Speed decrees (30s)", systemImage: "bolt.fill")
                    }
                }

                Divider()

                Button {
                    vm.showHowToPlay = true
                } label: { Label("How to play", systemImage: "questionmark.circle") }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .font(.title2)
                    .foregroundStyle(QColor.ink)
            }
        }
    }

    /// The Auto-X master switch, right in the header per the spec.
    private var autoXToggle: some View {
        Button {
            vm.autoXEnabled.toggle()
        } label: {
            HStack(spacing: 5) {
                Image(systemName: vm.autoXEnabled ? "xmark.square.fill" : "xmark.square")
                Text("Auto-X").font(.subheadline.weight(.semibold))
            }
            .padding(.horizontal, 11)
            .padding(.vertical, 6)
            .background(vm.autoXEnabled ? QColor.accent.opacity(0.16) : QColor.surface2, in: Capsule())
            .overlay(Capsule().stroke(vm.autoXEnabled ? QColor.accent.opacity(0.5) : QColor.line, lineWidth: 1))
            .foregroundStyle(vm.autoXEnabled ? QColor.accent : QColor.muted)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Auto-X")
        .accessibilityValue(vm.autoXEnabled ? "On" : "Off")
        .accessibilityAddTraits(vm.autoXEnabled ? [.isSelected] : [])
    }

    private var statusLine: some View {
        HStack(spacing: 6) {
            Text("\(vm.placedCount) / \(vm.puzzle.size) queens placed")
                .foregroundStyle(QColor.muted)
            if !vm.analysis.conflicts.isEmpty {
                Text("· \(vm.analysis.conflicts.count) in conflict")
                    .foregroundStyle(QColor.danger)
            }
        }
        .font(.subheadline)
        .frame(maxWidth: .infinity)
    }
}

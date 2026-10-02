//
//  BoardArea.swift
//  QueensGame
//
//  The board itself plus the rank/file numerals down the edges.
//  Input model:
//    • tap            → cycle empty → × → Queen → empty
//    • long-press     → drop a Queen directly
//    • drag (≥16 pt)  → sweep × marks across squares (drag from an × erases)
//

import SwiftUI

struct BoardArea: View {
    @EnvironmentObject private var vm: QueensGameViewModel
    /// Side length for the board square, decided by the parent.
    let side: CGFloat

    private let gap: CGFloat = 2
    private let labelGutter: CGFloat = 16

    private var n: Int { vm.puzzle.size }
    private var cell: CGFloat { (side - gap * CGFloat(n - 1)) / CGFloat(n) }

    var body: some View {
        VStack(spacing: 4) {
            // Column numerals
            HStack(spacing: gap) {
                Color.clear.frame(width: labelGutter, height: 10)
                ForEach(0..<n, id: \.self) { c in
                    numeral(c + 1, done: vm.analysis.colDone[c])
                        .frame(width: cell)
                }
            }

            HStack(alignment: .top, spacing: 4) {
                // Row numerals
                VStack(spacing: gap) {
                    ForEach(0..<n, id: \.self) { r in
                        numeral(r + 1, done: vm.analysis.rowDone[r])
                            .frame(height: cell)
                    }
                }
                .frame(width: labelGutter)

                board
            }
        }
        .frame(width: side + labelGutter + 4, alignment: .leading)
    }

    // MARK: - Board grid

    private var board: some View {
        VStack(spacing: gap) {
            ForEach(0..<n, id: \.self) { r in
                HStack(spacing: gap) {
                    ForEach(0..<n, id: \.self) { c in
                        let pos = GridPos(row: r, col: c)
                        CellView(pos: pos, side: cell)
                            .frame(width: cell, height: cell)
                            .contentShape(Rectangle())
                            .onTapGesture { vm.tap(pos) }
                            .onLongPressGesture(minimumDuration: 0.4) { vm.placeQueen(pos) }
                    }
                }
            }
        }
        .frame(width: side, height: side)
        .background(QColor.wall.opacity(0.22))
        .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 13, style: .continuous)
                .stroke(QColor.wall, lineWidth: 2)
        )
        .shadow(color: .black.opacity(0.10), radius: 16, y: 10)
        .simultaneousGesture(dragGesture)
        .shake(token: vm.shakeToken)
        .allowsHitTesting(!vm.isSolved)
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 16)
            .onChanged { value in
                vm.handleDrag(start: pos(at: value.startLocation),
                              current: pos(at: value.location))
            }
            .onEnded { _ in vm.endDrag() }
    }

    private func pos(at point: CGPoint) -> GridPos? {
        let step = cell + gap
        let col = Int((point.x / step).rounded(.down))
        let row = Int((point.y / step).rounded(.down))
        guard row >= 0, col >= 0, row < n, col < n else { return nil }
        return GridPos(row: row, col: col)
    }

    private func numeral(_ value: Int, done: Bool) -> some View {
        Text("\(value)")
            .font(.system(size: 11, weight: .semibold, design: .monospaced))
            .foregroundStyle(done ? QColor.faint : QColor.muted)
            .strikethrough(done, color: QColor.faint)
            .opacity(done ? 0.5 : 1)
            .animation(.easeOut(duration: 0.2), value: done)
    }
}

// MARK: - Single cell

struct CellView: View {
    @EnvironmentObject private var vm: QueensGameViewModel
    let pos: GridPos
    let side: CGFloat

    var body: some View {
        let region = vm.puzzle.regions[pos.row][pos.col]
        let guarded = vm.isGuard(pos)
        let fogged = vm.currentDecree == .fogOfWar && !vm.isRevealed(region)
        let display = vm.display(at: pos)
        let autoOnly = vm.isAutoX(at: pos)
        let conflict = vm.analysis.conflicts.contains(pos)

        ZStack {
            Rectangle()
                .fill(background(guarded: guarded, fogged: fogged, region: region))

            if !guarded && !fogged {
                WallOverlay(edges: vm.walls(at: pos))
            }

            symbol(display: display, autoOnly: autoOnly, guarded: guarded, conflict: conflict)
                .padding(side * 0.2)

            if conflict {
                Rectangle().strokeBorder(QColor.danger, lineWidth: 2.5)
            }
        }
        .animation(.spring(response: 0.28, dampingFraction: 0.55), value: display)
        .animation(.easeOut(duration: 0.18), value: conflict)
        .animation(.easeInOut(duration: 0.25), value: fogged)
    }

    @ViewBuilder
    private func symbol(display: CellDisplay, autoOnly: Bool, guarded: Bool, conflict: Bool) -> some View {
        if guarded {
            Image(systemName: "shield.lefthalf.filled")
                .resizable().scaledToFit()
                .foregroundStyle(QColor.muted)
        } else if display == .queen {
            Image(systemName: "crown.fill")
                .resizable().scaledToFit()
                .foregroundStyle(conflict ? QColor.danger : QColor.gold)
                .shadow(color: .black.opacity(0.22), radius: 1.5, y: 1.5)
                .transition(.scale(scale: 0.4).combined(with: .opacity))
        } else if display == .x {
            Image(systemName: "xmark")
                .resizable().scaledToFit()
                .fontWeight(.bold)
                .foregroundStyle(QColor.ink.opacity(autoOnly ? 0.24 : 0.55))
                .padding(side * 0.06)
                .transition(.scale(scale: 0.5).combined(with: .opacity))
        }
    }

    private func background(guarded: Bool, fogged: Bool, region: Int) -> Color {
        if guarded { return QColor.guardBG }
        if fogged { return QColor.fog }
        return QColor.region(vm.puzzle.colorMap[region])
    }
}

// MARK: - Realm wall overlay

struct WallOverlay: View {
    let edges: Set<WallEdge>
    private let thickness: CGFloat = 2

    var body: some View {
        ZStack {
            if edges.contains(.top) {
                Rectangle().frame(height: thickness).frame(maxHeight: .infinity, alignment: .top)
            }
            if edges.contains(.bottom) {
                Rectangle().frame(height: thickness).frame(maxHeight: .infinity, alignment: .bottom)
            }
            if edges.contains(.leading) {
                Rectangle().frame(width: thickness).frame(maxWidth: .infinity, alignment: .leading)
            }
            if edges.contains(.trailing) {
                Rectangle().frame(width: thickness).frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .foregroundStyle(QColor.wall)
        .allowsHitTesting(false)
    }
}

//
//  HowToPlayView.swift
//  QueensGame
//

import SwiftUI

struct HowToPlayView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text("Crown the board so every rank, file and coloured realm holds exactly one Queen.")
                        .font(.callout)
                        .foregroundStyle(QColor.muted)

                    rule("I", "One Queen per rank, file & realm",
                         "Each row, each column and each colour region gets exactly one Queen — no more, no fewer.")
                    rule("II", "Queens can’t touch",
                         "No two Queens may sit in adjacent squares — horizontally, vertically or diagonally.")
                    rule("III", "Controls",
                         "Tap a square to cycle empty → × → Queen → empty. Long-press to drop a Queen at once. Drag across squares to sweep × marks (drag from an × to erase).")
                    rule("IV", "Helpers",
                         "Rank & file numbers strike through when solved. Rule-breaking Queens flash red. Auto-X marks every square a Queen rules out. Undo, redo, a move counter and a timer track your run.")

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Royal Decrees mode")
                            .font(.system(.title3, design: .serif))
                            .foregroundStyle(QColor.ink)
                        Text("Switch on Royal Decrees and each new round a herald proclaims one rule-bending decree. Turn on Speed decrees to have it change every 30 seconds.")
                            .font(.footnote)
                            .foregroundStyle(QColor.muted)

                        ForEach(Decree.allCases.filter { $0 != .truce }) { decree in
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: decree.systemImage)
                                    .foregroundStyle(QColor.accent)
                                    .frame(width: 22)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(decree.title).font(.subheadline.weight(.semibold))
                                    Text(decree.blurb).font(.caption).foregroundStyle(QColor.muted)
                                }
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(QColor.surface2, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                    }
                }
                .padding(20)
            }
            .background(QColor.bg)
            .navigationTitle("How to play")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func rule(_ numeral: String, _ title: String, _ body: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(numeral)
                .font(.system(.title3, design: .serif))
                .foregroundStyle(QColor.accent)
                .frame(width: 26, alignment: .center)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.headline).foregroundStyle(QColor.ink)
                Text(body).font(.subheadline).foregroundStyle(QColor.muted)
            }
        }
    }
}

//
//  HintOfferView.swift
//  QueensGame
//
//  Shown when the player asks for a hint with none left: watch a rewarded
//  video for one more, or buy the hint pack. A free hint also arrives daily.
//

import SwiftUI

struct HintOfferView: View {
    @EnvironmentObject private var hints: HintWallet
    @EnvironmentObject private var store: PremiumStore
    @EnvironmentObject private var ads: AdRewards
    @Environment(\.dismiss) private var dismiss

    private var buying: Bool { store.hintPurchaseState == .purchasing }
    private var busy: Bool { buying || ads.isShowing }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    Image(systemName: "lightbulb.max.fill")
                        .font(.system(size: 50))
                        .foregroundStyle(QColor.gold)
                        .padding(.top, 8)

                    VStack(spacing: 6) {
                        Text("Out of hints")
                            .font(.system(.title, design: .serif))
                            .fontWeight(.semibold)
                        Text("A hint reveals one correct Queen, or lifts a misplaced one.")
                            .foregroundStyle(QColor.muted)
                            .multilineTextAlignment(.center)
                    }

                    VStack(spacing: 12) {
                        Button {
                            Task { await ads.watch(crediting: hints) }
                        } label: {
                            optionLabel(icon: "play.rectangle.fill",
                                        title: "Watch a video",
                                        detail: "+\(HintWallet.adReward) hint",
                                        spinning: ads.isShowing,
                                        filled: true)
                        }
                        .buttonStyle(.plain)
                        .disabled(busy)
                        .accessibilityIdentifier("hints.watchAd")

                        Button {
                            Task { await store.purchaseHintPack() }
                        } label: {
                            optionLabel(icon: "cart.fill",
                                        title: "\(PremiumStore.hintPackSize) hints",
                                        detail: store.hintPackPrice ?? "Buy",
                                        spinning: buying,
                                        filled: false)
                        }
                        .buttonStyle(.plain)
                        .disabled(busy)
                        .accessibilityIdentifier("hints.buyPack")
                    }

                    if let message = ads.errorMessage {
                        note(message, color: QColor.danger)
                    }
                    if case .failed(let message) = store.hintPurchaseState {
                        note(message, color: QColor.danger)
                    }
                    if store.hintPurchaseState == .pending {
                        note("Purchase pending approval. Your hints arrive once it's approved.", color: QColor.muted)
                    }

                    Label("You get a free hint every day.", systemImage: "gift")
                        .font(.footnote)
                        .foregroundStyle(QColor.muted)
                }
                .padding(20)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
            .background(QColor.bg.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .task {
            ads.preload()
            await store.loadProduct()
        }
        .onDisappear {
            ads.clearError()
            store.clearError()
        }
    }

    private func optionLabel(icon: String, title: String, detail: String,
                             spinning: Bool, filled: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .frame(width: 26)
            Text(title).fontWeight(.semibold)
            Spacer()
            if spinning {
                ProgressView().tint(filled ? .white : QColor.accent)
            } else {
                Text(detail).fontWeight(.semibold)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(filled ? QColor.accent : QColor.surface2,
                    in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(filled ? Color.clear : QColor.accent.opacity(0.5), lineWidth: 1)
        )
        .foregroundStyle(filled ? Color.white : QColor.accent)
    }

    private func note(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.footnote)
            .foregroundStyle(color)
            .multilineTextAlignment(.center)
    }
}

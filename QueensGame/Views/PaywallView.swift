//
//  PaywallView.swift
//  QueensGame
//
//  The Royal Pass sheet: what the one-time unlock includes, the localized
//  price from StoreKit, and the Restore Purchases button App Review requires.
//

import SwiftUI

struct PaywallView: View {
    @EnvironmentObject private var store: PremiumStore
    @Environment(\.dismiss) private var dismiss

    private var isBusy: Bool { store.purchaseState == .purchasing }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 22) {
                    Image(systemName: "crown.fill")
                        .font(.system(size: 54))
                        .foregroundStyle(QColor.gold)
                        .padding(.top, 12)

                    VStack(spacing: 6) {
                        Text("Royal Pass")
                            .font(.system(.largeTitle, design: .serif))
                            .fontWeight(.semibold)
                        Text("One purchase. Yours forever.")
                            .foregroundStyle(QColor.muted)
                    }

                    VStack(alignment: .leading, spacing: 14) {
                        feature("scroll.fill", "Royal Decrees mode",
                                "Every round the herald bends the rules.")
                        feature("cloud.fog.fill", "All four decrees",
                                "Fog of War, Royal Guard, Assassin's Range and Color Lock.")
                        feature("bolt.fill", "Speed decrees",
                                "A new decree every 30 seconds.")
                        feature("heart.fill", "Support an indie puzzle",
                                "No ads, no subscriptions, no tracking.")
                    }
                    .padding(16)
                    .card(cornerRadius: 16)

                    if case .failed(let message) = store.purchaseState {
                        Text(message)
                            .font(.footnote)
                            .foregroundStyle(QColor.danger)
                            .multilineTextAlignment(.center)
                    }
                    if store.purchaseState == .pending {
                        Text("Purchase pending approval. It unlocks automatically once approved.")
                            .font(.footnote)
                            .foregroundStyle(QColor.muted)
                            .multilineTextAlignment(.center)
                    }

                    if store.isPremium {
                        Label("Royal Pass unlocked", systemImage: "checkmark.seal.fill")
                            .font(.headline)
                            .foregroundStyle(QColor.ok)
                    } else {
                        Button {
                            Task { await store.purchase() }
                        } label: {
                            HStack {
                                if isBusy { ProgressView().tint(.white) }
                                Text(store.displayPrice.map { "Unlock for \($0)" } ?? "Unlock Royal Pass")
                                    .fontWeight(.semibold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(QColor.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .foregroundStyle(.white)
                        }
                        .buttonStyle(.plain)
                        .disabled(isBusy)
                        .accessibilityIdentifier("paywall.purchase")

                        Button("Restore Purchases") {
                            Task { await store.restore() }
                        }
                        .font(.subheadline)
                        .disabled(isBusy)
                        .accessibilityIdentifier("paywall.restore")
                    }
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
        .task { await store.loadProduct() }
        .onDisappear { store.clearError() }
    }

    private func feature(_ icon: String, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(QColor.accent)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(detail).font(.subheadline).foregroundStyle(QColor.muted)
            }
            Spacer(minLength: 0)
        }
    }
}

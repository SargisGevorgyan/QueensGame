//
//  RewardedAds.swift
//  QueensGame
//
//  The app's only view of rewarded video ads. Everything that touches the
//  Google Mobile Ads SDK lives in `AdMobRewardedAds.swift`, so swapping the
//  network (or stubbing it in tests) means providing another conformer.
//

import Foundation

enum RewardedAdError: LocalizedError {
    case notAvailable
    case noPresenter

    var errorDescription: String? {
        switch self {
        case .notAvailable: return "No video is available right now. Please try again later."
        case .noPresenter:  return "The video couldn't be shown right now."
        }
    }
}

@MainActor
protocol RewardedAdService: AnyObject {
    /// Starts loading the next ad in the background so it's ready on tap.
    func preload()
    /// Shows one rewarded ad. Returns true only when the reward was earned
    /// (the player watched to the end), false when they closed it early.
    func show() async throws -> Bool
}

/// Grants every reward instantly. Used by UI tests and SwiftUI previews.
@MainActor
final class InstantRewardedAds: RewardedAdService {
    func preload() {}
    func show() async throws -> Bool { true }
}

/// UI-facing state for "watch a video for a hint": busy flag, last error,
/// and crediting the wallet when the reward is earned.
@MainActor
final class AdRewards: ObservableObject {
    @Published private(set) var isShowing = false
    @Published private(set) var errorMessage: String?

    private let service: RewardedAdService

    init(service: RewardedAdService) {
        self.service = service
    }

    func preload() { service.preload() }

    /// Shows one ad and credits `HintWallet.adReward` hints if it was watched.
    func watch(crediting wallet: HintWallet) async {
        guard !isShowing else { return }
        isShowing = true
        errorMessage = nil
        defer { isShowing = false }
        do {
            if try await service.show() {
                wallet.credit(HintWallet.adReward)
                wallet.showOffer = false
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func clearError() { errorMessage = nil }
}

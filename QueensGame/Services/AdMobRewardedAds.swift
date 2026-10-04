//
//  AdMobRewardedAds.swift
//  QueensGame
//
//  Google AdMob rewarded ads behind `RewardedAdService`. The SDK starts
//  lazily, the first time a player asks for an ad, right after the App
//  Tracking Transparency prompt, so nothing ad-related runs at launch.
//
//  The ids below are Google's public *test* ids. Replace both (this unit id
//  and `GADApplicationIdentifier` in Info.plist) with the real ones from the
//  AdMob console before shipping.
//

import UIKit
import AppTrackingTransparency
import GoogleMobileAds

@MainActor
final class AdMobRewardedAds: NSObject, RewardedAdService {

    static let adUnitID = "ca-app-pub-3940256099942544/1712485313"

    private var started = false
    private var loaded: RewardedAd?
    private var loading: Task<RewardedAd, Error>?
    private var finish: ((Bool) -> Void)?
    private var earned = false

    func preload() {
        guard started, loaded == nil, loading == nil else { return }
        _ = loadTask()
    }

    func show() async throws -> Bool {
        await startIfNeeded()
        let ad: RewardedAd
        if let ready = loaded {
            ad = ready
        } else {
            do {
                ad = try await loadTask().value
            } catch {
                throw RewardedAdError.notAvailable
            }
        }
        loaded = nil
        guard let presenter = Self.topViewController() else { throw RewardedAdError.noPresenter }

        earned = false
        ad.fullScreenContentDelegate = self
        let rewarded = await withCheckedContinuation { (cont: CheckedContinuation<Bool, Never>) in
            finish = { cont.resume(returning: $0) }
            ad.present(from: presenter) { [weak self] in
                self?.earned = true
            }
        }
        preload()
        return rewarded
    }

    // MARK: - Private

    private func startIfNeeded() async {
        guard !started else { return }
        started = true
        if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
            _ = await ATTrackingManager.requestTrackingAuthorization()
        }
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            MobileAds.shared.start { _ in cont.resume() }
        }
    }

    private func loadTask() -> Task<RewardedAd, Error> {
        if let loading { return loading }
        let task = Task { @MainActor [weak self] () throws -> RewardedAd in
            defer { self?.loading = nil }
            let ad = try await RewardedAd.load(with: Self.adUnitID, request: Request())
            self?.loaded = ad
            return ad
        }
        loading = task
        return task
    }

    private func complete(_ result: Bool) {
        let done = finish
        finish = nil
        done?(result)
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

extension AdMobRewardedAds: FullScreenContentDelegate {
    nonisolated func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        Task { @MainActor in self.complete(self.earned) }
    }

    nonisolated func ad(_ ad: FullScreenPresentingAd,
                        didFailToPresentFullScreenContentWithError error: Error) {
        Task { @MainActor in self.complete(false) }
    }
}

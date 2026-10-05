//
//  QueensGameApp.swift
//  QueensGame
//
//  A Queens logic puzzle built with SwiftUI (MVVM). Includes the unique
//  "Royal Decrees" mode with per-round rule modifiers. The only third-party
//  dependency is Google Mobile Ads, for optional rewarded hint videos.
//

import SwiftUI

@main
struct QueensGameApp: App {
    @StateObject private var game = QueensGameViewModel()
    @StateObject private var hints: HintWallet
    @StateObject private var store: PremiumStore
    @StateObject private var ads: AdRewards
    @StateObject private var gameCenter = GameCenterManager()

    init() {
        let wallet = HintWallet()
        _hints = StateObject(wrappedValue: wallet)
        _store = StateObject(wrappedValue: PremiumStore(hints: wallet))
        _ads = StateObject(wrappedValue: AdRewards(service: Self.makeAdService()))
    }

    private static func makeAdService() -> RewardedAdService {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-UITestInstantAds") {
            return InstantRewardedAds()
        }
        #endif
        return AdMobRewardedAds()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(game)
                .environmentObject(hints)
                .environmentObject(store)
                .environmentObject(ads)
                .environmentObject(gameCenter)
                .tint(QColor.accent)
                .task {
                    let center = gameCenter
                    game.onSolve = { center.recordSolve($0) }
                    center.authenticate()
                }
        }
    }
}

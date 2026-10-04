//
//  GameCenterManager.swift
//  QueensGame
//
//  GameKit wrapper: signs the local player in on launch, submits first-try
//  Daily times to the per-size leaderboards, and reports achievement
//  progress derived from local stats. Everything is a silent no-op when the
//  player is not signed in or the app is not registered for Game Center yet
//  (GKError 15, "game unrecognized", until App Store Connect is set up), so
//  the game plays exactly the same without it.
//

import GameKit
import SwiftUI
import os

@MainActor
final class GameCenterManager: ObservableObject {

    enum Status: Equatable {
        case idle
        case signedIn
        case unavailable(String)
    }

    /// What the view model reports when a puzzle is solved.
    struct Solve: Equatable {
        let size: Int
        let seconds: Int
        /// True only the first time today's Daily for this size is solved,
        /// so replays of a known board never reach the leaderboard.
        let isFirstDailyCompletion: Bool
    }

    @Published private(set) var status: Status = .idle
    var isAuthenticated: Bool { status == .signedIn }

    private var didInstallHandler = false
    /// Last known percent per achievement id, so each one is reported only
    /// when it actually moves forward (and its banner shows once).
    private var knownPercent: [String: Double] = [:]
    private let log = Logger(subsystem: "QueensGame", category: "GameCenter")

    /// Skipped for unit-test hosts and UI tests (`-DisableGameCenter`) so a
    /// sign-in sheet can never cover the board.
    static var isDisabled: Bool {
        let info = ProcessInfo.processInfo
        return info.arguments.contains("-DisableGameCenter")
            || info.environment["XCTestConfigurationFilePath"] != nil
    }

    // MARK: - Authentication

    func authenticate() {
        guard !Self.isDisabled, !didInstallHandler else { return }
        didInstallHandler = true
        // GameKit calls this again whenever the sign-in state changes.
        GKLocalPlayer.local.authenticateHandler = { [weak self] viewController, error in
            Task { @MainActor in self?.handleAuthentication(viewController, error) }
        }
    }

    private func handleAuthentication(_ viewController: UIViewController?, _ error: Error?) {
        if let viewController {
            present(viewController)
            return
        }
        if let error {
            status = .unavailable(Self.describe(error))
            log.notice("Game Center unavailable: \(error.localizedDescription, privacy: .public)")
            return
        }
        guard GKLocalPlayer.local.isAuthenticated else {
            status = .unavailable("Not signed in to Game Center.")
            return
        }
        status = .signedIn
        Task { await syncAfterSignIn() }
    }

    private static func describe(_ error: Error) -> String {
        switch (error as? GKError)?.code {
        case .gameUnrecognized?:  return "This app is not set up for Game Center yet."
        case .notAuthenticated?,
             .cancelled?:         return "Not signed in to Game Center."
        case .notSupported?:      return "Game Center is not supported on this device."
        default:                  return error.localizedDescription
        }
    }

    private func present(_ viewController: UIViewController) {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }?
            .keyWindow
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        top?.present(viewController, animated: true)
    }

    // MARK: - Reporting

    func recordSolve(_ solve: Solve) {
        guard isAuthenticated else { return }
        Task {
            if solve.isFirstDailyCompletion {
                await submit(seconds: solve.seconds, size: solve.size)
            }
            await reportAchievements()
        }
    }

    /// Catches up on anything earned while signed out or offline.
    private func syncAfterSignIn() async {
        await loadAchievements()
        for size in GameCenterCatalog.leaderboardSizes {
            if let best = StatsStore.dailyBestSeconds(size: size) {
                await submit(seconds: best, size: size)
            }
        }
        await reportAchievements()
    }

    private func submit(seconds: Int, size: Int) async {
        guard isAuthenticated, seconds > 0 else { return }
        let id = GameCenterCatalog.dailyLeaderboardID(size: size)
        do {
            try await GKLeaderboard.submitScore(seconds, context: 0, player: GKLocalPlayer.local,
                                                leaderboardIDs: [id])
        } catch {
            log.notice("Score submit to \(id, privacy: .public) failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func loadAchievements() async {
        do {
            for achievement in try await GKAchievement.loadAchievements() {
                knownPercent[achievement.identifier] = achievement.percentComplete
            }
        } catch {
            log.notice("Loading achievements failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func reportAchievements() async {
        guard isAuthenticated else { return }
        let pending = GameCenterCatalog.progress(for: Self.currentSnapshot())
            .compactMap { entry -> GKAchievement? in
                guard entry.value > (knownPercent[entry.key.id] ?? 0) else { return nil }
                let achievement = GKAchievement(identifier: entry.key.id)
                achievement.percentComplete = entry.value
                achievement.showsCompletionBanner = true
                return achievement
            }
        guard !pending.isEmpty else { return }
        do {
            try await GKAchievement.report(pending)
            for achievement in pending {
                knownPercent[achievement.identifier] = achievement.percentComplete
            }
        } catch {
            log.notice("Achievement report failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    static func currentSnapshot() -> GameCenterCatalog.Snapshot {
        GameCenterCatalog.Snapshot(
            totalSolved: StatsStore.totalSolved,
            dailyStreak: StatsStore.dailyStreak(),
            everSolvedDaily: StatsStore.hasSolvedDaily,
            decreeWins: StatsStore.decreeWins,
            bestNineSeconds: StatsStore.bestSeconds(size: 9)
        )
    }

    // MARK: - UI

    /// Opens Game Center's own screens through the access point.
    func showLeaderboards() { show(.leaderboards) }
    func showAchievements() { show(.achievements) }

    private func show(_ state: GKGameCenterViewControllerState) {
        guard isAuthenticated else { return }
        GKAccessPoint.shared.trigger(state: state) {}
    }
}

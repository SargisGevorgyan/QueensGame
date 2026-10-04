//
//  GameCenterCatalog.swift
//  QueensGame
//
//  Every Game Center leaderboard and achievement id in one place, plus the
//  pure rules that turn local stats into achievement progress. The ids must
//  match what is configured in App Store Connect (Features → Game Center).
//

import Foundation

enum GameCenterCatalog {

    // MARK: - Leaderboards

    /// Board sizes that have a Daily leaderboard.
    static let leaderboardSizes = [6, 7, 8, 9]

    /// Classic leaderboard, best (lowest) first-try Daily solve time for a
    /// board size. Score format in App Store Connect: "Elapsed Time – To the
    /// Second", sort "Low to High". Submitted value is whole seconds.
    static func dailyLeaderboardID(size: Int) -> String { "queens.daily.best.\(size)x\(size)" }

    static var allLeaderboardIDs: [String] { leaderboardSizes.map(dailyLeaderboardID(size:)) }

    // MARK: - Achievements

    enum Achievement: String, CaseIterable {
        case firstSolve    = "queens.ach.first_solve"
        case solves25      = "queens.ach.solves_25"
        case solves100     = "queens.ach.solves_100"
        case firstDaily    = "queens.ach.first_daily"
        case dailyStreak3  = "queens.ach.daily_streak_3"
        case dailyStreak7  = "queens.ach.daily_streak_7"
        case firstDecree   = "queens.ach.first_decree"
        case decrees10     = "queens.ach.decrees_10"
        case swiftNine     = "queens.ach.nine_under_3min"

        var id: String { rawValue }

        /// Suggested App Store Connect title / description.
        var title: String {
            switch self {
            case .firstSolve:   return "Long Live the Queen"
            case .solves25:     return "Court Regular"
            case .solves100:    return "Monarch of Logic"
            case .firstDaily:   return "Daily Audience"
            case .dailyStreak3: return "Three-Day Reign"
            case .dailyStreak7: return "Week on the Throne"
            case .firstDecree:  return "By Royal Decree"
            case .decrees10:    return "Herald's Favourite"
            case .swiftNine:    return "Swift Sovereign"
            }
        }

        var detail: String {
            switch self {
            case .firstSolve:   return "Solve your first puzzle."
            case .solves25:     return "Solve 25 puzzles."
            case .solves100:    return "Solve 100 puzzles."
            case .firstDaily:   return "Solve a Daily puzzle."
            case .dailyStreak3: return "Solve a Daily puzzle 3 days in a row."
            case .dailyStreak7: return "Solve a Daily puzzle 7 days in a row."
            case .firstDecree:  return "Win a Royal Decrees round."
            case .decrees10:    return "Win 10 Royal Decrees rounds."
            case .swiftNine:    return "Solve a 9×9 puzzle in under 3 minutes."
            }
        }
    }

    /// Local stats the achievement rules read.
    struct Snapshot: Equatable {
        var totalSolved = 0
        var dailyStreak = 0
        var everSolvedDaily = false
        var decreeWins = 0
        /// Fastest 9×9 solve in seconds, any mode.
        var bestNineSeconds: Int?
    }

    /// Percent complete (0...100) for every achievement with any progress.
    static func progress(for s: Snapshot) -> [Achievement: Double] {
        func pct(_ value: Int, of target: Int) -> Double {
            min(100, Double(value) / Double(target) * 100)
        }
        let all: [Achievement: Double] = [
            .firstSolve:   pct(s.totalSolved, of: 1),
            .solves25:     pct(s.totalSolved, of: 25),
            .solves100:    pct(s.totalSolved, of: 100),
            .firstDaily:   s.everSolvedDaily ? 100 : 0,
            .dailyStreak3: pct(s.dailyStreak, of: 3),
            .dailyStreak7: pct(s.dailyStreak, of: 7),
            .firstDecree:  pct(s.decreeWins, of: 1),
            .decrees10:    pct(s.decreeWins, of: 10),
            .swiftNine:    (s.bestNineSeconds ?? .max) < 180 ? 100 : 0,
        ]
        return all.filter { $0.value > 0 }
    }
}

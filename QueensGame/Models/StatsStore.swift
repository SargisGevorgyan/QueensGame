//
//  StatsStore.swift
//  QueensGame
//
//  Lightweight UserDefaults-backed persistence: total solves, best time per
//  board size, best first-try Daily time per size, Royal Decrees wins, and
//  the set of completed Daily puzzles (which also yields the Daily streak).
//

import Foundation

enum StatsKey {
    static let totalSolved = "queens.solved"
    static let dailyDone   = "queens.daily.done"
    static let decreeWins  = "queens.decree.wins"
    static func best(_ size: Int) -> String { "queens.best.\(size)" }
    static func dailyBest(_ size: Int) -> String { "queens.daily.best.\(size)" }
}

enum StatsStore {
    private static var defaults: UserDefaults { .standard }

    static func recordSolve(size: Int, seconds: Int, moves: Int) {
        defaults.set(defaults.integer(forKey: StatsKey.totalSolved) + 1, forKey: StatsKey.totalSolved)
        let key = StatsKey.best(size)
        let previous = defaults.object(forKey: key) as? Int
        if previous == nil || seconds < previous! {
            defaults.set(seconds, forKey: key)
        }
    }

    static func bestSeconds(size: Int) -> Int? {
        defaults.object(forKey: StatsKey.best(size)) as? Int
    }

    static func recordDaily(key: String) {
        var done = Set(defaults.stringArray(forKey: StatsKey.dailyDone) ?? [])
        done.insert(key)
        defaults.set(Array(done), forKey: StatsKey.dailyDone)
    }

    static func isDailyDone(key: String) -> Bool {
        Set(defaults.stringArray(forKey: StatsKey.dailyDone) ?? []).contains(key)
    }

    static var totalSolved: Int { defaults.integer(forKey: StatsKey.totalSolved) }

    static var hasSolvedDaily: Bool { !(defaults.stringArray(forKey: StatsKey.dailyDone) ?? []).isEmpty }

    // MARK: - Daily best (first completion of each day's Daily only)

    static func recordDailyBest(size: Int, seconds: Int) {
        let key = StatsKey.dailyBest(size)
        let previous = defaults.object(forKey: key) as? Int
        if previous == nil || seconds < previous! {
            defaults.set(seconds, forKey: key)
        }
    }

    static func dailyBestSeconds(size: Int) -> Int? {
        defaults.object(forKey: StatsKey.dailyBest(size)) as? Int
    }

    // MARK: - Royal Decrees

    static func recordDecreeWin() {
        defaults.set(decreeWins + 1, forKey: StatsKey.decreeWins)
    }

    static var decreeWins: Int { defaults.integer(forKey: StatsKey.decreeWins) }

    // MARK: - Daily streak

    /// Consecutive days, ending today, on which at least one Daily was solved.
    static func dailyStreak(asOf date: Date = Date()) -> Int {
        dailyStreak(doneKeys: Set(defaults.stringArray(forKey: StatsKey.dailyDone) ?? []), asOf: date)
    }

    /// Pure version for tests. Keys look like `yyyy-MM-dd|size` (see
    /// `PuzzleGenerator.dailyKey`); any board size counts toward a day.
    static func dailyStreak(doneKeys: Set<String>, asOf date: Date, calendar: Calendar = .current) -> Int {
        let days = Set(doneKeys.compactMap { $0.split(separator: "|").first.map(String.init) })
        var streak = 0
        var day = date
        while days.contains(dayString(day, calendar: calendar)) {
            streak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return streak
    }

    private static func dayString(_ date: Date, calendar: Calendar) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.calendar = Calendar(identifier: .gregorian)
        f.timeZone = calendar.timeZone
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }
}

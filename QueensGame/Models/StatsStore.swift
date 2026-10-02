//
//  StatsStore.swift
//  QueensGame
//
//  Lightweight UserDefaults-backed persistence: total solves, best time per
//  board size, and the set of completed Daily puzzles.
//

import Foundation

enum StatsKey {
    static let totalSolved = "queens.solved"
    static let dailyDone   = "queens.daily.done"
    static func best(_ size: Int) -> String { "queens.best.\(size)" }
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
}

//
//  HintWallet.swift
//  QueensGame
//
//  The player's hint balance, kept in UserDefaults. New players start with
//  three free hints and get one more each day (while below the free cap).
//  Extra hints come from a rewarded ad (+1) or the consumable hint pack.
//

import Foundation

@MainActor
final class HintWallet: ObservableObject {

    /// Free hints on first launch, and the ceiling the daily refill tops up to.
    static let freeCap = 3
    /// Hints granted for watching one rewarded ad.
    static let adReward = 1

    @Published private(set) var balance: Int {
        didSet { defaults.set(balance, forKey: Keys.balance) }
    }
    /// The "out of hints" sheet.
    @Published var showOffer = false

    private enum Keys {
        static let balance = "queens.hints.balance"
        static let refillDay = "queens.hints.refillDay"
    }

    private let defaults: UserDefaults
    private let calendar: Calendar

    init(defaults: UserDefaults = .standard, calendar: Calendar = .current, now: Date = Date()) {
        self.defaults = defaults
        self.calendar = calendar
        #if DEBUG
        // UI tests pin the starting balance: `-UITestHintBalance 0`.
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-UITestHintBalance"), i + 1 < args.count, let pinned = Int(args[i + 1]) {
            self.balance = pinned
            defaults.set(Self.dayKey(now, calendar), forKey: Keys.refillDay)
            return
        }
        #endif
        if defaults.object(forKey: Keys.balance) == nil {
            self.balance = Self.freeCap
            defaults.set(Self.freeCap, forKey: Keys.balance)
            defaults.set(Self.dayKey(now, calendar), forKey: Keys.refillDay)
        } else {
            self.balance = max(0, defaults.integer(forKey: Keys.balance))
        }
        refillIfNewDay(now: now)
    }

    /// One free hint per new calendar day, never above `freeCap` (bought
    /// hints above the cap are kept as they are).
    func refillIfNewDay(now: Date = Date()) {
        let today = Self.dayKey(now, calendar)
        guard defaults.string(forKey: Keys.refillDay) != today else { return }
        defaults.set(today, forKey: Keys.refillDay)
        if balance < Self.freeCap { balance += 1 }
    }

    /// Takes one hint; false when the balance is empty.
    @discardableResult
    func spend() -> Bool {
        guard balance > 0 else { return false }
        balance -= 1
        return true
    }

    func credit(_ count: Int) {
        guard count > 0 else { return }
        balance += count
    }

    private static func dayKey(_ date: Date, _ calendar: Calendar) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }
}

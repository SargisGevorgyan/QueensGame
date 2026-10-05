//
//  HintWallet.kt
//  QueensGame (Android)
//
//  The player's hint balance — the Android twin of the iOS `HintWallet`.
//  New players start with three free hints and get one more each day (while
//  below the free cap). Extra hints come from a rewarded ad (+1) or the
//  consumable hint pack.
//

package com.app.queensgame.core

import java.time.LocalDate

class HintWallet(
    private val store: KeyValueStore,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    companion object {
        /** Free hints on first launch, and the ceiling the daily refill tops up to. */
        const val FREE_CAP = 3
        /** Hints granted for watching one rewarded ad. */
        const val AD_REWARD = 1

        private const val BALANCE = "queens.hints.balance"
        private const val REFILL_DAY = "queens.hints.refillDay"
    }

    var balance: Int = 0
        private set(value) {
            field = value
            store.putInt(BALANCE, value)
        }

    init {
        val stored = store.getInt(BALANCE)
        if (stored == null) {
            balance = FREE_CAP
            store.putString(REFILL_DAY, today().toString())
        } else {
            balance = stored.coerceAtLeast(0)
        }
        refillIfNewDay()
    }

    /**
     * One free hint per new calendar day, never above [FREE_CAP] (bought
     * hints above the cap are kept as they are). Returns true if it changed.
     */
    fun refillIfNewDay(): Boolean {
        val day = today().toString()
        if (store.getString(REFILL_DAY) == day) return false
        store.putString(REFILL_DAY, day)
        if (balance >= FREE_CAP) return false
        balance += 1
        return true
    }

    /** Takes one hint; false when the balance is empty. */
    fun spend(): Boolean {
        if (balance <= 0) return false
        balance -= 1
        return true
    }

    fun credit(count: Int) {
        if (count > 0) balance += count
    }
}

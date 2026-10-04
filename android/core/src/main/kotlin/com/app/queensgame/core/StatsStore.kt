//
//  StatsStore.kt
//  QueensGame (Android)
//
//  Lightweight key-value persistence: total solves, best time per board size,
//  the set of completed Daily puzzles, and the player's settings. The app
//  backs [KeyValueStore] with SharedPreferences; tests use [InMemoryStore].
//

package com.app.queensgame.core

interface KeyValueStore {
    fun getInt(key: String): Int?
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String): Boolean?
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getStringSet(key: String): Set<String>?
    fun putStringSet(key: String, value: Set<String>)
}

class InMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()
    override fun getInt(key: String) = values[key] as? Int
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getBoolean(key: String) = values[key] as? Boolean
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getString(key: String) = values[key] as? String
    override fun putString(key: String, value: String) { values[key] = value }
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String) = values[key] as? Set<String>
    override fun putStringSet(key: String, value: Set<String>) { values[key] = value.toSet() }
}

object StatsKey {
    const val TOTAL_SOLVED = "queens.solved"
    const val DAILY_DONE = "queens.daily.done"
    fun best(size: Int) = "queens.best.$size"
}

class StatsStore(private val store: KeyValueStore) {

    fun recordSolve(size: Int, seconds: Int) {
        store.putInt(StatsKey.TOTAL_SOLVED, totalSolved + 1)
        val previous = bestSeconds(size)
        if (previous == null || seconds < previous) {
            store.putInt(StatsKey.best(size), seconds)
        }
    }

    fun bestSeconds(size: Int): Int? = store.getInt(StatsKey.best(size))

    fun recordDaily(key: String) {
        store.putStringSet(StatsKey.DAILY_DONE, (store.getStringSet(StatsKey.DAILY_DONE) ?: emptySet()) + key)
    }

    fun isDailyDone(key: String): Boolean =
        store.getStringSet(StatsKey.DAILY_DONE)?.contains(key) == true

    val totalSolved: Int get() = store.getInt(StatsKey.TOTAL_SOLVED) ?: 0
}

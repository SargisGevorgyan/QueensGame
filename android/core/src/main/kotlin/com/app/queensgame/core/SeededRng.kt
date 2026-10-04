//
//  SeededRng.kt
//  QueensGame (Android)
//
//  SplitMix64 plus the exact sampling algorithms the Swift standard library
//  uses for `Int.random(in:using:)`, `shuffle(using:)` and
//  `randomElement(using:)`. Mirroring them means a given seed walks the same
//  random sequence as the iOS `SeededRNG`, so Daily puzzles are meant to come
//  out identical on both platforms.
//

package com.app.queensgame.core

class SeededRng(seed: ULong) {
    private var state: ULong = if (seed == 0uL) GOLDEN else seed

    fun next(): ULong {
        state += GOLDEN
        var z = state
        z = (z xor (z shr 30)) * 0xBF58_476D_1CE4_E5B9uL
        z = (z xor (z shr 27)) * 0x94D0_49BB_1331_11EBuL
        return z xor (z shr 31)
    }

    /** Swift's `RandomNumberGenerator.next(upperBound:)` (Lemire's method). */
    fun next(upperBound: ULong): ULong {
        require(upperBound != 0uL) { "upperBound cannot be zero." }
        var random = next()
        var low = random * upperBound
        if (low < upperBound) {
            val t = (0uL - upperBound) % upperBound
            while (low < t) {
                random = next()
                low = random * upperBound
            }
        }
        return multiplyHigh(random, upperBound)
    }

    /** Swift's `Int.random(in: lower..<upper, using:)`. */
    fun nextInt(lower: Int, upper: Int): Int {
        require(lower < upper) { "Can't get random value with an empty range" }
        val delta = (upper.toLong() - lower.toLong()).toULong()
        return (lower.toLong().toULong() + next(delta)).toLong().toInt()
    }

    /** Swift's `Int.random(in: 0..<upper, using:)`. */
    fun nextInt(upper: Int): Int = nextInt(0, upper)

    companion object {
        const val GOLDEN: ULong = 0x9E37_79B9_7F4A_7C15uL

        /** High 64 bits of the full 128-bit product of two unsigned 64-bit values. */
        fun multiplyHigh(a: ULong, b: ULong): ULong {
            val mask = 0xFFFF_FFFFuL
            val aLo = a and mask
            val aHi = a shr 32
            val bLo = b and mask
            val bHi = b shr 32
            val ll = aLo * bLo
            val lh = aLo * bHi
            val hl = aHi * bLo
            val hh = aHi * bHi
            val mid = (ll shr 32) + (lh and mask) + (hl and mask)
            return hh + (lh shr 32) + (hl shr 32) + (mid shr 32)
        }
    }
}

/** Swift's in-place `MutableCollection.shuffle(using:)`. */
fun <T> MutableList<T>.shuffleSwift(rng: SeededRng) {
    if (size <= 1) return
    var amount = size
    var current = 0
    while (amount > 1) {
        val random = rng.nextInt(amount)
        amount -= 1
        java.util.Collections.swap(this, current, current + random)
        current += 1
    }
}

/** Swift's `Sequence.shuffled(using:)`. */
fun <T> Iterable<T>.shuffledSwift(rng: SeededRng): List<T> =
    toMutableList().also { it.shuffleSwift(rng) }

/** Swift's `Collection.randomElement(using:)`. */
fun <T> List<T>.randomElementSwift(rng: SeededRng): T? =
    if (isEmpty()) null else this[rng.nextInt(size)]

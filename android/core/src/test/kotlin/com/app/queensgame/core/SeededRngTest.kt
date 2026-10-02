package com.app.queensgame.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class SeededRngTest {

    @Test
    fun sameSeedGivesSameSequence() {
        val a = SeededRng(123uL)
        val b = SeededRng(123uL)
        repeat(50) { assertEquals(a.next(), b.next()) }
    }

    @Test
    fun zeroSeedFallsBackToGoldenRatio() {
        val zero = SeededRng(0uL)
        val golden = SeededRng(SeededRng.GOLDEN)
        repeat(5) { assertEquals(golden.next(), zero.next()) }
    }

    @Test
    fun nextIntStaysInRange() {
        val rng = SeededRng(99uL)
        repeat(5_000) {
            val v = rng.nextInt(3, 10)
            assertTrue(v in 3 until 10)
        }
    }

    @Test
    fun multiplyHighMatchesBigInteger() {
        val rng = SeededRng(7uL)
        val two64 = BigInteger.ONE.shiftLeft(64)
        fun big(v: ULong) = BigInteger(v.toString())
        repeat(1_000) {
            val a = rng.next()
            val b = if (it % 3 == 0) ULong.MAX_VALUE else rng.next()
            val expected = big(a).multiply(big(b)).divide(two64)
            assertEquals(expected.toString(), SeededRng.multiplyHigh(a, b).toString())
        }
    }

    @Test
    fun shuffleIsAPermutation() {
        val rng = SeededRng(5uL)
        val shuffled = (0 until 9).shuffledSwift(rng)
        assertEquals((0 until 9).toList(), shuffled.sorted())
    }
}

package com.emberdeep.game.core

import com.emberdeep.game.testutil.A
import org.junit.Test

class RngTest {

    @Test
    fun `same seed produces the same sequence`() {
        val a = Rng(987654321L)
        val b = Rng(987654321L)
        for (i in 0 until 200) A.eq(a.nextInt(1000), b.nextInt(1000), "draw $i")
    }

    @Test
    fun `different seeds diverge`() {
        val a = Rng(1L)
        val b = Rng(2L)
        var equal = 0
        for (i in 0 until 50) if (a.nextInt(1_000_000) == b.nextInt(1_000_000)) equal++
        A.isTrue(equal < 5, "sequences should differ (had $equal matches)")
    }

    @Test
    fun `state can be saved and resumed`() {
        val a = Rng(42L)
        repeat(10) { a.nextInt(100) }
        val saved = a.state
        val b = Rng(1L)
        b.state = saved
        for (i in 0 until 20) A.eq(a.nextInt(1000), b.nextInt(1000), "resumed draw $i")
    }

    @Test
    fun `nextInt stays inside its bound`() {
        val rng = Rng(7L)
        for (i in 0 until 5000) A.within(rng.nextInt(7), 0, 6, "nextInt(7)")
        A.eq(0, rng.nextInt(0), "zero bound")
        A.eq(0, rng.nextInt(-5), "negative bound")
    }

    @Test
    fun `range and dice respect their bounds`() {
        val rng = Rng(11L)
        for (i in 0 until 5000) A.within(rng.range(3, 8), 3, 8, "range")
        for (i in 0 until 5000) A.within(rng.dice(2, 6), 2, 12, "2d6")
        for (i in 0 until 2000) A.within(rng.dice(3, 6, bonus = 4), 7, 22, "3d6+4")
        A.eq(5, rng.range(5, 5), "degenerate range")
    }

    @Test
    fun `chance is calibrated`() {
        val rng = Rng(2026L)
        var hits = 0
        val draws = 20_000
        repeat(draws) { if (rng.chance(0.25f)) hits++ }
        val rate = hits.toDouble() / draws
        A.near(0.25, rate, 0.02, "chance(0.25f) frequency")
        A.isTrue(rng.chance(1f), "chance(1f) must always fire")
        A.isFalse(rng.chance(0f), "chance(0f) must never fire")
    }

    @Test
    fun `pick only returns elements of the list`() {
        val rng = Rng(3L)
        val options = listOf("a", "b", "c")
        for (i in 0 until 500) A.isTrue(rng.pick(options) in options, "pick returned a stranger")
    }
}

package com.emberdeep.game.core

/**
 * Deterministic splitmix64 RNG. Game logic uses a seeded instance so runs are
 * reproducible and the RNG state can be persisted inside save files.
 */
class Rng(seed: Long = System.nanoTime()) {

    var state: Long = if (seed == 0L) DEFAULT_SEED else seed

    private fun next(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int =
        if (bound <= 0) 0 else ((next() ushr 1) % bound.toLong()).toInt()

    /** Inclusive range [a, b]. */
    fun range(a: Int, b: Int): Int = if (b <= a) a else a + nextInt(b - a + 1)

    fun nextFloat(): Float = (next() ushr 40).toFloat() / (1 shl 24).toFloat()

    fun chance(p: Float): Boolean = nextFloat() < p

    /** Roll [n] dice with [sides] sides, plus [bonus]. */
    fun dice(n: Int, sides: Int, bonus: Int = 0): Int {
        var total = bonus
        for (i in 0 until n) total += range(1, sides)
        return total
    }

    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]

    private companion object {
        const val DEFAULT_SEED = 0x1D872B41C8E72E5BL
        const val GOLDEN = -0x61c8864680b583ebL
        const val MIX1 = -0x40a7b892e31b1a47L
        const val MIX2 = -0x6b2fb644ecceee15L
    }
}

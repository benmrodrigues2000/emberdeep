package com.emberdeep.game.testutil

import org.junit.Assert.fail

/**
 * Tiny assertion helpers.
 *
 * JUnit's `assertEquals` has Int/Long/Double/Object overloads that Kotlin
 * resolves in surprising ways, so the suite compares values explicitly and
 * always reports a description of what broke.
 */
object A {

    fun eq(expected: Int, actual: Int, what: String = "value") {
        if (expected != actual) fail("$what: expected $expected but was $actual")
    }

    fun eq(expected: Long, actual: Long, what: String = "value") {
        if (expected != actual) fail("$what: expected $expected but was $actual")
    }

    fun eq(expected: String, actual: String, what: String = "value") {
        if (expected != actual) fail("$what: expected \"$expected\" but was \"$actual\"")
    }

    /** Equality for anything object-like (enums, data holders, strings). */
    fun eqAny(expected: Any?, actual: Any?, what: String = "value") {
        if (expected != actual) fail("$what: expected $expected but was $actual")
    }

    fun ne(unexpected: Int, actual: Int, what: String = "value") {
        if (unexpected == actual) fail("$what: expected anything but $unexpected")
    }

    fun isTrue(condition: Boolean, what: String) {
        if (!condition) fail(what)
    }

    fun isFalse(condition: Boolean, what: String) {
        if (condition) fail(what)
    }

    fun notNull(value: Any?, what: String) {
        if (value == null) fail("$what: expected a value but was null")
    }

    fun near(expected: Double, actual: Double, tolerance: Double, what: String = "value") {
        if (kotlin.math.abs(expected - actual) > tolerance) {
            fail("$what: expected $expected +/- $tolerance but was $actual")
        }
    }

    fun within(actual: Int, low: Int, high: Int, what: String = "value") {
        if (actual < low || actual > high) fail("$what: $actual not in [$low, $high]")
    }
}

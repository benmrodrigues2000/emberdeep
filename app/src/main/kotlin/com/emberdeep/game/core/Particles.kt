package com.emberdeep.game.core

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Fixed-pool particle system — zero allocations during gameplay.
 * Positions are in whatever space the caller renders in.
 */
class Particles(private val max: Int = 320) {

    private val px = FloatArray(max)
    private val py = FloatArray(max)
    private val vx = FloatArray(max)
    private val vy = FloatArray(max)
    private val life = FloatArray(max)
    private val maxLife = FloatArray(max)
    private val size = FloatArray(max)
    private val color = IntArray(max)
    private val gravity = FloatArray(max)
    private val alive = BooleanArray(max)

    private var cursor = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rng = Rng()

    fun spawn(
        x: Float, y: Float, velX: Float, velY: Float,
        lifetime: Float, radius: Float, col: Int, grav: Float = 0f
    ) {
        val i = cursor
        cursor = (cursor + 1) % max
        px[i] = x; py[i] = y
        vx[i] = velX; vy[i] = velY
        life[i] = lifetime; maxLife[i] = lifetime
        size[i] = radius; color[i] = col
        gravity[i] = grav
        alive[i] = true
    }

    fun burst(x: Float, y: Float, col: Int, count: Int, speed: Float, radius: Float, grav: Float = 0f) {
        for (i in 0 until count) {
            val a = rng.nextFloat() * 6.2832f
            val sp = speed * (0.35f + rng.nextFloat() * 0.65f)
            spawn(
                x, y,
                (Math.cos(a.toDouble()) * sp).toFloat(),
                (Math.sin(a.toDouble()) * sp).toFloat(),
                0.35f + rng.nextFloat() * 0.4f,
                radius * (0.6f + rng.nextFloat() * 0.8f),
                col, grav
            )
        }
    }

    fun emberRise(x: Float, y: Float, spread: Float, speed: Float, radius: Float) {
        spawn(
            x + (rng.nextFloat() - 0.5f) * spread, y,
            (rng.nextFloat() - 0.5f) * speed * 0.3f,
            -speed * (0.5f + rng.nextFloat()),
            1.2f + rng.nextFloat() * 1.3f,
            radius * (0.5f + rng.nextFloat()),
            if (rng.chance(0.3f)) Palette.EMBER_BRIGHT else Palette.EMBER
        )
    }

    fun update(dt: Float) {
        for (i in 0 until max) {
            if (!alive[i]) continue
            life[i] -= dt
            if (life[i] <= 0f) { alive[i] = false; continue }
            vy[i] += gravity[i] * dt
            px[i] += vx[i] * dt
            py[i] += vy[i] * dt
        }
    }

    fun render(c: Canvas) {
        for (i in 0 until max) {
            if (!alive[i]) continue
            val k = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            paint.color = Palette.alpha(color[i], (k * 255).toInt())
            c.drawCircle(px[i], py[i], size[i] * (0.5f + 0.5f * k), paint)
        }
    }

    fun clear() {
        java.util.Arrays.fill(alive, false)
    }
}

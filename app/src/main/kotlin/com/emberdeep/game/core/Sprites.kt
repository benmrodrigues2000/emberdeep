package com.emberdeep.game.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

enum class Spr {
    FLOOR, WALL, DOOR, STAIRS, VENT,
    HERO_FIGHTER, HERO_ROGUE, HERO_MAGE,
    RAT, GOBLIN, SKELETON, SKELETON_ARCHER, ORC, CULTIST, WRAITH, TROLL, DRAGON,
    POTION, SCROLL, SWORD, ARMOR, GOLD,
    ICON_WHIRLWIND, ICON_SHADOW, ICON_FIREBOLT, ICON_BAG, ICON_WAIT, ICON_DESCEND
}

/**
 * Procedural sprite atlas. Every visual in the game is drawn in code onto
 * cached bitmaps, so there are zero image assets to ship — yet everything can
 * be swapped for real art later by replacing this single class.
 */
class Sprites {

    // Concurrent: cleared from the UI thread on resize while the game thread
    // reads it. Worst case a sprite is rendered twice — never corruption.
    private val cache = java.util.concurrent.ConcurrentHashMap<Long, Bitmap>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    fun clear() {
        // Drop references only: the render thread may still be drawing a
        // frame with the old bitmaps, so recycling here could crash.
        cache.clear()
    }

    fun get(spr: Spr, size: Int, variant: Int = 0, tint: Int = 0): Bitmap {
        val key = (spr.ordinal.toLong() shl 48) or
            ((variant.toLong() and 0xFF) shl 40) or
            ((size.toLong() and 0xFFFF) shl 24) or
            (tint.toLong() and 0xFFFFFF)
        cache[key]?.let { return it }
        val bmp = Bitmap.createBitmap(size.coerceAtLeast(2), size.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(size / U, size / U)
        draw(c, spr, variant, tint)
        cache[key] = bmp
        return bmp
    }

    // ------------------------------------------------------------------ //

    private fun draw(c: Canvas, spr: Spr, variant: Int, tint: Int) {
        when (spr) {
            Spr.FLOOR -> floor(c, variant)
            Spr.WALL -> wall(c, variant)
            Spr.DOOR -> door(c)
            Spr.STAIRS -> stairs(c)
            Spr.VENT -> vent(c, variant)
            Spr.HERO_FIGHTER -> fighter(c)
            Spr.HERO_ROGUE -> rogue(c)
            Spr.HERO_MAGE -> mage(c)
            Spr.RAT -> rat(c)
            Spr.GOBLIN -> goblin(c)
            Spr.SKELETON -> skeleton(c, bow = false)
            Spr.SKELETON_ARCHER -> skeleton(c, bow = true)
            Spr.ORC -> orc(c)
            Spr.CULTIST -> cultist(c)
            Spr.WRAITH -> wraith(c)
            Spr.TROLL -> troll(c)
            Spr.DRAGON -> dragon(c)
            Spr.POTION -> potion(c, tint)
            Spr.SCROLL -> scroll(c)
            Spr.SWORD -> sword(c, tint)
            Spr.ARMOR -> armor(c, tint)
            Spr.GOLD -> gold(c)
            Spr.ICON_WHIRLWIND -> iconWhirlwind(c)
            Spr.ICON_SHADOW -> iconShadow(c)
            Spr.ICON_FIREBOLT -> iconFire(c)
            Spr.ICON_BAG -> iconBag(c)
            Spr.ICON_WAIT -> iconWait(c)
            Spr.ICON_DESCEND -> iconDescend(c)
        }
    }

    private fun f(color: Int): Paint { fill.color = color; fill.shader = null; return fill }
    private fun s(color: Int, w: Float): Paint {
        stroke.color = color; stroke.strokeWidth = w; stroke.shader = null; return stroke
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        c.drawRoundRect(RectF(l, t, r, b), rad, rad, f(color))
    }

    // --- Tiles --------------------------------------------------------- //

    private fun floor(c: Canvas, variant: Int) {
        c.drawRect(0f, 0f, U, U, f(Palette.FLOOR))
        val rng = Rng(variant * 7349L + 17)
        fill.color = 0xFF332A30.toInt()
        for (i in 0 until 5) {
            val x = rng.nextFloat() * U
            val y = rng.nextFloat() * U
            c.drawRect(x, y, x + 2f, y + 2f, fill)
        }
        // Tile seams.
        c.drawLine(0f, 0f, U, 0f, s(0xFF2E262B.toInt(), 1.4f))
        c.drawLine(0f, 0f, 0f, U, stroke)
        if (variant == 3) {
            // A crack.
            path.reset()
            path.moveTo(6f, 24f); path.lineTo(13f, 18f); path.lineTo(12f, 11f)
            c.drawPath(path, s(0xFF2A2126.toInt(), 1.5f))
        }
    }

    private fun wall(c: Canvas, variant: Int) {
        c.drawRect(0f, 0f, U, U, f(Palette.WALL))
        // Lighter cap for depth.
        c.drawRect(0f, 0f, U, 10f, f(Palette.WALL_TOP))
        c.drawRect(0f, 10f, U, 12f, f(0xFF1A141C.toInt()))
        // Brick seams.
        val p = s(0xFF1C161E.toInt(), 1.6f)
        c.drawLine(0f, 21f, U, 21f, p)
        val off = if (variant % 2 == 0) 8f else 18f
        c.drawLine(off, 12f, off, 21f, p)
        c.drawLine((off + 14f) % U, 21f, (off + 14f) % U, U, p)
    }

    private fun door(c: Canvas) {
        wall(c, 0)
        rr(c, 5f, 6f, 27f, 30f, 3f, 0xFF4E3421.toInt())
        rr(c, 7f, 8f, 25f, 30f, 2f, 0xFF6B4A2E.toInt())
        val p = s(0xFF4E3421.toInt(), 1.5f)
        c.drawLine(13f, 8f, 13f, 30f, p)
        c.drawLine(19f, 8f, 19f, 30f, p)
        c.drawCircle(22f, 20f, 1.8f, f(Palette.EMBER))
    }

    private fun stairs(c: Canvas) {
        floor(c, 0)
        rr(c, 3f, 3f, 29f, 29f, 2f, 0xFF17121A.toInt())
        rr(c, 6f, 6f, 29f, 29f, 2f, 0xFF231B28.toInt())
        rr(c, 10f, 10f, 29f, 29f, 2f, 0xFF140F18.toInt())
        rr(c, 14f, 14f, 29f, 29f, 2f, 0xFF0B080E.toInt())
        fill.shader = RadialGradient(
            24f, 24f, 10f, Palette.alpha(Palette.EMBER, 110), 0, Shader.TileMode.CLAMP
        )
        c.drawRect(12f, 12f, 30f, 30f, fill)
        fill.shader = null
    }

    private fun vent(c: Canvas, variant: Int) {
        floor(c, variant)
        path.reset()
        path.moveTo(7f, 20f); path.lineTo(14f, 16f); path.lineTo(18f, 18f)
        path.lineTo(25f, 13f)
        c.drawPath(path, s(0xFF1A1016.toInt(), 3f))
        fill.shader = RadialGradient(
            16f, 17f, 11f, Palette.alpha(Palette.EMBER, 150), 0, Shader.TileMode.CLAMP
        )
        c.drawRect(2f, 2f, 30f, 30f, fill)
        fill.shader = null
        c.drawPath(path, s(Palette.EMBER_BRIGHT, 1.4f))
    }

    // --- Heroes -------------------------------------------------------- //

    private fun shadow(c: Canvas) {
        c.drawOval(RectF(8f, 27f, 24f, 31f), f(0x66000000))
    }

    private fun fighter(c: Canvas) {
        shadow(c)
        // Sword.
        c.drawLine(25f, 6f, 25f, 20f, s(0xFFDDE5EC.toInt(), 2.2f))
        c.drawLine(22.6f, 18f, 27.4f, 18f, s(0xFF8A6B3A.toInt(), 2f))
        // Body armor.
        rr(c, 9f, 13f, 23f, 27f, 4f, 0xFF8E9AA8.toInt())
        rr(c, 11f, 15f, 21f, 23f, 3f, 0xFFB7C3D0.toInt())
        // Head and helmet.
        c.drawCircle(16f, 9f, 5f, f(0xFFE8C9A8.toInt()))
        path.reset()
        path.moveTo(10.5f, 9f); path.lineTo(16f, 2.5f); path.lineTo(21.5f, 9f)
        path.close()
        c.drawPath(path, f(0xFF8E9AA8.toInt()))
        c.drawLine(16f, 2.5f, 16f, 0.8f, s(Palette.EMBER, 2f))
        // Shield.
        rr(c, 4.5f, 15f, 10.5f, 24f, 3f, 0xFF6B4A2E.toInt())
        c.drawCircle(7.5f, 19.5f, 1.6f, f(Palette.EMBER))
        // Eyes.
        c.drawCircle(14f, 9f, 0.9f, f(0xFF2A2230.toInt()))
        c.drawCircle(18f, 9f, 0.9f, f(0xFF2A2230.toInt()))
    }

    private fun rogue(c: Canvas) {
        shadow(c)
        // Daggers.
        c.drawLine(6f, 14f, 9f, 22f, s(0xFFC3CCD6.toInt(), 1.8f))
        c.drawLine(26f, 14f, 23f, 22f, s(0xFFC3CCD6.toInt(), 1.8f))
        // Cloak body.
        path.reset()
        path.moveTo(9f, 28f); path.lineTo(11f, 13f); path.lineTo(21f, 13f)
        path.lineTo(23f, 28f)
        path.close()
        c.drawPath(path, f(0xFF3B3350.toInt()))
        // Hood.
        path.reset()
        path.moveTo(9.5f, 11f)
        path.quadTo(16f, -1f, 22.5f, 11f)
        path.quadTo(16f, 15f, 9.5f, 11f)
        path.close()
        c.drawPath(path, f(0xFF514677.toInt()))
        // Shadowed face with glowing eyes.
        c.drawOval(RectF(12f, 7f, 20f, 12.5f), f(0xFF17121A.toInt()))
        c.drawCircle(14.3f, 9.8f, 0.9f, f(0xFFB9A8E8.toInt()))
        c.drawCircle(17.7f, 9.8f, 0.9f, f(0xFFB9A8E8.toInt()))
    }

    private fun mage(c: Canvas) {
        shadow(c)
        // Staff with ember orb.
        c.drawLine(25f, 8f, 25f, 26f, s(0xFF6B4A2E.toInt(), 2f))
        c.drawCircle(25f, 6.5f, 2.6f, f(Palette.EMBER))
        c.drawCircle(25f, 6.5f, 1.2f, f(Palette.EMBER_BRIGHT))
        // Robe.
        path.reset()
        path.moveTo(8f, 28f); path.lineTo(11f, 13f); path.lineTo(21f, 13f)
        path.lineTo(24f, 28f)
        path.close()
        c.drawPath(path, f(0xFF8C3A1A.toInt()))
        rr(c, 12f, 16f, 20f, 24f, 2f, 0xFFB1522E.toInt())
        // Head.
        c.drawCircle(16f, 10f, 4.4f, f(0xFFE8C9A8.toInt()))
        // Pointed hat.
        path.reset()
        path.moveTo(8.5f, 9f); path.lineTo(16f, 0f); path.lineTo(23.5f, 9f)
        path.close()
        c.drawPath(path, f(0xFFB1522E.toInt()))
        c.drawLine(8.5f, 9f, 23.5f, 9f, s(0xFF8C3A1A.toInt(), 1.6f))
        c.drawCircle(14.4f, 10.5f, 0.8f, f(0xFF2A2230.toInt()))
        c.drawCircle(17.6f, 10.5f, 0.8f, f(0xFF2A2230.toInt()))
    }

    // --- Monsters ------------------------------------------------------ //

    private fun rat(c: Canvas) {
        shadow(c)
        path.reset()
        path.moveTo(23f, 25f)
        path.quadTo(30f, 23f, 29f, 17f)
        c.drawPath(path, s(0xFFC98A96.toInt(), 1.6f))
        c.drawOval(RectF(6f, 15f, 24f, 27f), f(0xFF8D8378.toInt()))
        c.drawOval(RectF(3f, 15f, 13f, 24f), f(0xFF9C9288.toInt()))
        path.reset()
        path.moveTo(6f, 16.5f); path.lineTo(8f, 12f); path.lineTo(10.5f, 15.5f)
        path.close()
        c.drawPath(path, f(0xFF9C9288.toInt()))
        c.drawCircle(6.4f, 18.6f, 1.1f, f(0xFFE0443C.toInt()))
        c.drawLine(3.2f, 20f, 0.5f, 19f, s(0xFFC9C0B4.toInt(), 0.9f))
        c.drawLine(3.2f, 21f, 0.5f, 22f, stroke)
    }

    private fun goblin(c: Canvas) {
        shadow(c)
        // Club.
        c.drawLine(24f, 13f, 28f, 24f, s(0xFF6B4A2E.toInt(), 2.4f))
        c.drawCircle(24f, 12f, 2.6f, f(0xFF57402A.toInt()))
        rr(c, 10f, 15f, 22f, 27f, 4f, 0xFF60804A.toInt())
        c.drawCircle(16f, 10f, 5.5f, f(0xFF7BA05B.toInt()))
        // Big ears.
        path.reset()
        path.moveTo(10.8f, 9f); path.lineTo(4.5f, 6.5f); path.lineTo(11f, 12f); path.close()
        c.drawPath(path, f(0xFF7BA05B.toInt()))
        path.reset()
        path.moveTo(21.2f, 9f); path.lineTo(27.5f, 6.5f); path.lineTo(21f, 12f); path.close()
        c.drawPath(path, f(0xFF7BA05B.toInt()))
        c.drawCircle(14f, 9.6f, 1.2f, f(0xFFE8D44D.toInt()))
        c.drawCircle(18f, 9.6f, 1.2f, f(0xFFE8D44D.toInt()))
        c.drawCircle(14f, 9.6f, 0.5f, f(0xFF17121A.toInt()))
        c.drawCircle(18f, 9.6f, 0.5f, f(0xFF17121A.toInt()))
        c.drawLine(13.5f, 13.4f, 18.5f, 13.2f, s(0xFF3E5430.toInt(), 1.2f))
    }

    private fun skeleton(c: Canvas, bow: Boolean) {
        shadow(c)
        if (bow) {
            path.reset()
            path.moveTo(26f, 8f)
            path.quadTo(31f, 16f, 26f, 24f)
            c.drawPath(path, s(0xFF8A6B3A.toInt(), 1.8f))
            c.drawLine(26f, 8f, 26f, 24f, s(0xFFC9C0B4.toInt(), 0.9f))
        } else {
            c.drawLine(26f, 8f, 26f, 21f, s(0xFFC3CCD6.toInt(), 2f))
        }
        // Ribcage.
        rr(c, 11f, 14f, 21f, 26f, 3f, 0xFFD8CBB0.toInt())
        val p = s(0xFF8F8470.toInt(), 1.3f)
        c.drawLine(12f, 17f, 20f, 17f, p)
        c.drawLine(12f, 20f, 20f, 20f, p)
        c.drawLine(12f, 23f, 20f, 23f, p)
        // Skull.
        c.drawCircle(16f, 8.5f, 5f, f(0xFFE8E0D0.toInt()))
        rr(c, 13f, 11f, 19f, 14.5f, 1.5f, 0xFFE8E0D0.toInt())
        c.drawCircle(14f, 8.5f, 1.5f, f(0xFF17121A.toInt()))
        c.drawCircle(18f, 8.5f, 1.5f, f(0xFF17121A.toInt()))
        c.drawRect(15.4f, 12f, 16.2f, 14f, f(0xFF8F8470.toInt()))
        c.drawRect(17f, 12f, 17.8f, 14f, f(0xFF8F8470.toInt()))
    }

    private fun orc(c: Canvas) {
        shadow(c)
        // Axe.
        c.drawLine(26f, 7f, 26f, 23f, s(0xFF6B4A2E.toInt(), 2.2f))
        path.reset()
        path.moveTo(26f, 7f); path.quadTo(32f, 10f, 26f, 14f); path.close()
        c.drawPath(path, f(0xFFB7C3D0.toInt()))
        // Hulking body.
        rr(c, 7f, 12f, 23f, 27f, 5f, 0xFF4E7340.toInt())
        rr(c, 9f, 14f, 21f, 20f, 3f, 0xFF5B8A4A.toInt())
        // Head with tusks.
        c.drawCircle(15f, 8f, 5.6f, f(0xFF5B8A4A.toInt()))
        c.drawCircle(13f, 7.6f, 1.2f, f(0xFFE0443C.toInt()))
        c.drawCircle(17.4f, 7.6f, 1.2f, f(0xFFE0443C.toInt()))
        c.drawLine(12f, 11.5f, 11f, 9f, s(0xFFE8E0D0.toInt(), 1.7f))
        c.drawLine(18f, 11.5f, 19f, 9f, stroke)
    }

    private fun cultist(c: Canvas) {
        shadow(c)
        path.reset()
        path.moveTo(8f, 28f); path.lineTo(10.5f, 12f); path.lineTo(21.5f, 12f)
        path.lineTo(24f, 28f); path.close()
        c.drawPath(path, f(0xFF8C2F26.toInt()))
        path.reset()
        path.moveTo(10f, 11f)
        path.quadTo(16f, 0.5f, 22f, 11f)
        path.quadTo(16f, 14.5f, 10f, 11f)
        path.close()
        c.drawPath(path, f(0xFFA63A2E.toInt()))
        c.drawOval(RectF(12.4f, 7f, 19.6f, 12f), f(0xFF17121A.toInt()))
        c.drawCircle(14.5f, 9.5f, 0.9f, f(Palette.EMBER_BRIGHT))
        c.drawCircle(17.5f, 9.5f, 0.9f, f(Palette.EMBER_BRIGHT))
        // Ember sigil.
        path.reset()
        path.moveTo(16f, 17f); path.lineTo(18f, 21f); path.lineTo(16f, 25f)
        path.lineTo(14f, 21f); path.close()
        c.drawPath(path, f(Palette.EMBER))
    }

    private fun wraith(c: Canvas) {
        path.reset()
        path.moveTo(7f, 29f)
        path.quadTo(9f, 22f, 8f, 14f)
        path.quadTo(9f, 4f, 16f, 4f)
        path.quadTo(23f, 4f, 24f, 14f)
        path.quadTo(23f, 22f, 25f, 29f)
        path.quadTo(21f, 25f, 19f, 29f)
        path.quadTo(16f, 25f, 13f, 29f)
        path.quadTo(11f, 25f, 7f, 29f)
        path.close()
        c.drawPath(path, f(0x9C9FB8D8.toInt()))
        c.drawOval(RectF(11f, 8f, 21f, 16f), f(0x66E8F0FF))
        c.drawCircle(13.8f, 11.5f, 1.3f, f(0xFFBFE3FF.toInt()))
        c.drawCircle(18.2f, 11.5f, 1.3f, f(0xFFBFE3FF.toInt()))
    }

    private fun troll(c: Canvas) {
        shadow(c)
        // Massive body.
        rr(c, 5f, 10f, 27f, 27f, 7f, 0xFF5A6B48.toInt())
        rr(c, 8f, 13f, 24f, 21f, 4f, 0xFF6E7D5A.toInt())
        // Long arms.
        c.drawLine(6.5f, 14f, 3f, 25f, s(0xFF5A6B48.toInt(), 3.6f))
        c.drawLine(25.5f, 14f, 29f, 25f, stroke)
        // Small head on big shoulders.
        c.drawCircle(16f, 7.5f, 4.6f, f(0xFF6E7D5A.toInt()))
        c.drawCircle(14.3f, 7f, 1f, f(0xFFE8D44D.toInt()))
        c.drawCircle(17.7f, 7f, 1f, f(0xFFE8D44D.toInt()))
        c.drawLine(13f, 10.4f, 12.4f, 8.6f, s(0xFFE8E0D0.toInt(), 1.4f))
        c.drawLine(19f, 10.4f, 19.6f, 8.6f, stroke)
        // Mossy patches.
        c.drawCircle(10f, 18f, 1.6f, f(0xFF8CA06B.toInt()))
        c.drawCircle(21f, 23f, 1.8f, f(0xFF8CA06B.toInt()))
    }

    private fun dragon(c: Canvas) {
        // Wings.
        path.reset()
        path.moveTo(15f, 14f); path.lineTo(1f, 4f); path.lineTo(6f, 15f)
        path.lineTo(2.5f, 14f); path.lineTo(10f, 20f)
        path.close()
        c.drawPath(path, f(0xFFA33A20.toInt()))
        path.reset()
        path.moveTo(17f, 14f); path.lineTo(31f, 4f); path.lineTo(26f, 15f)
        path.lineTo(29.5f, 14f); path.lineTo(22f, 20f)
        path.close()
        c.drawPath(path, f(0xFFA33A20.toInt()))
        shadow(c)
        // Tail.
        path.reset()
        path.moveTo(16f, 24f)
        path.quadTo(26f, 28f, 30f, 23f)
        c.drawPath(path, s(0xFFC44426.toInt(), 3f))
        path.reset()
        path.moveTo(29f, 25f); path.lineTo(32f, 22f); path.lineTo(28.6f, 21.4f)
        path.close()
        c.drawPath(path, f(0xFFE0502B.toInt()))
        // Body and belly.
        c.drawOval(RectF(8f, 11f, 24f, 27f), f(0xFFC44426.toInt()))
        c.drawOval(RectF(11f, 15f, 21f, 26.5f), f(0xFFFFA14D.toInt()))
        val p = s(0xFFE07A2E.toInt(), 1.2f)
        c.drawLine(12f, 18f, 20f, 18f, p)
        c.drawLine(12f, 21f, 20f, 21f, p)
        c.drawLine(12f, 24f, 20f, 24f, p)
        // Head with horns.
        c.drawOval(RectF(10.5f, 2.5f, 21.5f, 12f), f(0xFFE0502B.toInt()))
        path.reset()
        path.moveTo(11.5f, 4.5f); path.lineTo(8f, 0.5f); path.lineTo(13.5f, 3f); path.close()
        c.drawPath(path, f(0xFFE8E0D0.toInt()))
        path.reset()
        path.moveTo(20.5f, 4.5f); path.lineTo(24f, 0.5f); path.lineTo(18.5f, 3f); path.close()
        c.drawPath(path, f(0xFFE8E0D0.toInt()))
        c.drawCircle(13.6f, 7f, 1.4f, f(0xFFFFE066.toInt()))
        c.drawCircle(18.4f, 7f, 1.4f, f(0xFFFFE066.toInt()))
        c.drawCircle(13.6f, 7f, 0.6f, f(0xFF17121A.toInt()))
        c.drawCircle(18.4f, 7f, 0.6f, f(0xFF17121A.toInt()))
        // Smoke nostrils.
        c.drawCircle(15f, 10.4f, 0.6f, f(0xFF551E12.toInt()))
        c.drawCircle(17f, 10.4f, 0.6f, f(0xFF551E12.toInt()))
    }

    // --- Items --------------------------------------------------------- //

    private fun potion(c: Canvas, tint: Int) {
        val liquid = if (tint != 0) (0xFF000000.toInt() or tint) else Palette.HP
        c.drawOval(RectF(13f, 26f, 21f, 29.5f), f(0x55000000))
        path.reset()
        path.moveTo(14f, 12f)
        path.lineTo(14f, 15f)
        path.quadTo(8.5f, 18f, 9f, 23f)
        path.quadTo(9.5f, 28f, 16f, 28f)
        path.quadTo(22.5f, 28f, 23f, 23f)
        path.quadTo(23.5f, 18f, 18f, 15f)
        path.lineTo(18f, 12f)
        path.close()
        c.drawPath(path, f(0x7FD8E8F0))
        // Liquid.
        path.reset()
        path.moveTo(10f, 21f)
        path.quadTo(16f, 18.5f, 22f, 21f)
        path.quadTo(22f, 27.4f, 16f, 27.4f)
        path.quadTo(10f, 27.4f, 10f, 21f)
        path.close()
        c.drawPath(path, f(liquid))
        rr(c, 13.2f, 8.5f, 18.8f, 12.5f, 1.5f, 0xFF6B4A2E.toInt())
        c.drawCircle(13.5f, 19f, 1.1f, f(0x88FFFFFF.toInt()))
    }

    private fun scroll(c: Canvas) {
        c.drawOval(RectF(9f, 25f, 25f, 29f), f(0x55000000))
        rr(c, 8f, 9f, 24f, 26f, 2.5f, 0xFFE8DCC0.toInt())
        c.drawOval(RectF(7f, 7f, 25f, 12f), f(0xFFD4C5A0.toInt()))
        c.drawOval(RectF(7f, 23.5f, 25f, 28.5f), f(0xFFD4C5A0.toInt()))
        val p = s(0xFF9C8F70.toInt(), 1.2f)
        c.drawLine(11f, 14.5f, 21f, 14.5f, p)
        c.drawLine(11f, 17.5f, 21f, 17.5f, p)
        c.drawLine(11f, 20.5f, 17f, 20.5f, p)
        c.drawLine(16f, 9.5f, 16f, 26f, s(0xFFC04A3C.toInt(), 1.6f))
    }

    private fun sword(c: Canvas, tint: Int) {
        val blade = if (tint != 0) (0xFF000000.toInt() or tint) else 0xFFC3CCD6.toInt()
        c.drawOval(RectF(10f, 26f, 24f, 29.5f), f(0x55000000))
        c.drawLine(10f, 22f, 23f, 9f, s(blade, 3.4f))
        path.reset()
        path.moveTo(21f, 7f); path.lineTo(25.5f, 6.5f); path.lineTo(25f, 11f)
        path.close()
        c.drawPath(path, f(blade))
        c.drawLine(9.5f, 17.5f, 15f, 23f, s(0xFF8A6B3A.toInt(), 2.4f))
        c.drawLine(8f, 24f, 11f, 21f, s(0xFF57402A.toInt(), 3f))
        c.drawCircle(6.8f, 25.2f, 1.8f, f(Palette.GOLD))
    }

    private fun armor(c: Canvas, tint: Int) {
        val metal = if (tint != 0) (0xFF000000.toInt() or tint) else 0xFFB7C3D0.toInt()
        c.drawOval(RectF(8f, 26f, 24f, 29.5f), f(0x55000000))
        path.reset()
        path.moveTo(9f, 8f)
        path.lineTo(23f, 8f)
        path.lineTo(26f, 13f)
        path.lineTo(23.5f, 16f)
        path.lineTo(22f, 13.5f)
        path.lineTo(22f, 26f)
        path.lineTo(10f, 26f)
        path.lineTo(10f, 13.5f)
        path.lineTo(8.5f, 16f)
        path.lineTo(6f, 13f)
        path.close()
        c.drawPath(path, f(metal))
        c.drawPath(path, s(Palette.mix(metal, 0xFF000000.toInt(), 0.35f), 1.4f))
        c.drawLine(16f, 10f, 16f, 24f, s(Palette.mix(metal, 0xFF000000.toInt(), 0.25f), 1.4f))
        c.drawCircle(13f, 12f, 1f, f(Palette.mix(metal, 0xFFFFFFFF.toInt(), 0.4f)))
        c.drawCircle(19f, 12f, 1f, f(Palette.mix(metal, 0xFFFFFFFF.toInt(), 0.4f)))
    }

    private fun gold(c: Canvas) {
        c.drawOval(RectF(7f, 25f, 25f, 29.5f), f(0x55000000))
        for ((i, pos) in arrayOf(
            floatArrayOf(11f, 23f), floatArrayOf(20f, 23.5f), floatArrayOf(15.5f, 18.5f)
        ).withIndex()) {
            c.drawOval(
                RectF(pos[0] - 5f, pos[1] - 2.6f, pos[0] + 5f, pos[1] + 2.6f),
                f(if (i == 2) Palette.GOLD else 0xFFD9A32E.toInt())
            )
            c.drawOval(
                RectF(pos[0] - 3.2f, pos[1] - 1.5f, pos[0] + 3.2f, pos[1] + 1.5f),
                s(0xFFA87818.toInt(), 1f)
            )
        }
    }

    // --- UI icons ------------------------------------------------------ //

    private fun iconWhirlwind(c: Canvas) {
        val p = s(Palette.TEXT, 2.4f)
        for (i in 0 until 3) {
            path.reset()
            val a = i * 120f
            c.save()
            c.rotate(a, 16f, 16f)
            path.moveTo(16f, 6f)
            path.quadTo(26f, 8f, 24f, 16f)
            c.drawPath(path, p)
            path.reset()
            path.moveTo(24f, 16f); path.lineTo(26.5f, 12.5f); path.lineTo(21f, 13f)
            c.drawPath(path, s(Palette.TEXT, 1.6f))
            c.restore()
        }
        c.drawCircle(16f, 16f, 2.4f, f(Palette.TEXT))
    }

    private fun iconShadow(c: Canvas) {
        // A crescent: the dark disc bites into the disc above.
        c.drawCircle(16f, 16f, 10f, f(Palette.TEXT))
        c.drawCircle(20.5f, 13.5f, 8.2f, f(0xFF1A151E.toInt()))
        c.drawCircle(24f, 22f, 1.6f, f(Palette.TEXT))
        c.drawCircle(8f, 7f, 1.2f, f(Palette.TEXT))
    }

    private fun iconFire(c: Canvas) {
        path.reset()
        path.moveTo(16f, 3f)
        path.quadTo(23f, 10f, 24f, 17f)
        path.quadTo(24.5f, 25f, 16f, 28f)
        path.quadTo(7.5f, 25f, 8f, 17f)
        path.quadTo(9f, 10f, 16f, 3f)
        path.close()
        c.drawPath(path, f(Palette.EMBER))
        path.reset()
        path.moveTo(16f, 12f)
        path.quadTo(20f, 17f, 19.5f, 21f)
        path.quadTo(19f, 25f, 16f, 25.5f)
        path.quadTo(13f, 25f, 12.5f, 21f)
        path.quadTo(12f, 17f, 16f, 12f)
        path.close()
        c.drawPath(path, f(0xFFFFE066.toInt()))
    }

    private fun iconBag(c: Canvas) {
        rr(c, 7f, 11f, 25f, 27f, 4f, 0xFF8A6B3A.toInt())
        rr(c, 7f, 11f, 25f, 16f, 3f, 0xFF6B4A2E.toInt())
        path.reset()
        path.moveTo(12f, 11f)
        path.quadTo(12f, 5f, 16f, 5f)
        path.quadTo(20f, 5f, 20f, 11f)
        c.drawPath(path, s(0xFF57402A.toInt(), 2.2f))
        c.drawCircle(16f, 19f, 2f, f(Palette.GOLD))
    }

    private fun iconWait(c: Canvas) {
        val p = s(Palette.TEXT, 2.2f)
        c.drawLine(9f, 6f, 23f, 6f, p)
        c.drawLine(9f, 26f, 23f, 26f, p)
        path.reset()
        path.moveTo(10.5f, 6.5f)
        path.lineTo(10.5f, 9f); path.lineTo(16f, 16f); path.lineTo(21.5f, 9f)
        path.lineTo(21.5f, 6.5f)
        path.close()
        c.drawPath(path, f(Palette.alpha(Palette.TEXT, 120)))
        path.reset()
        path.moveTo(10.5f, 25.5f)
        path.lineTo(10.5f, 23f); path.lineTo(16f, 16f); path.lineTo(21.5f, 23f)
        path.lineTo(21.5f, 25.5f)
        path.close()
        c.drawPath(path, f(Palette.GOLD))
    }

    private fun iconDescend(c: Canvas) {
        rr(c, 5f, 5f, 27f, 27f, 3f, 0xFF231B28.toInt())
        rr(c, 9f, 9f, 27f, 27f, 2f, 0xFF140F18.toInt())
        path.reset()
        path.moveTo(16f, 10f); path.lineTo(16f, 20f)
        c.drawPath(path, s(Palette.EMBER_BRIGHT, 2.4f))
        path.reset()
        path.moveTo(11f, 16f); path.lineTo(16f, 22f); path.lineTo(21f, 16f)
        c.drawPath(path, s(Palette.EMBER_BRIGHT, 2.4f))
    }

    private companion object {
        const val U = 32f
    }
}
